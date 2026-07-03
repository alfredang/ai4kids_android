#!/usr/bin/env python3
"""
Build-time phoneme audio generator for AI4Kids Phonics.

Renders the ~44 English phonemes (isolated "pure" sound) + a matching example
word for each, using a cloud TTS engine that honours SSML <phoneme> (IPA).
Runs ONCE on a dev machine; the resulting audio ships in the APK, so nothing
leaves the device at runtime.

Parameterised by voice + phonetic alphabet so you can batch several voices and
A/B them before committing to one. A compare page (out/index.html) is written
with inline <audio> players for side-by-side listening.

Providers:
  polly  - Amazon Polly (needs boto3 + AWS creds in the usual chain)
  azure  - Azure Speech (needs `requests` + AZURE_TTS_KEY env var + --region)

Examples:
  python generate_phonemes.py --provider azure --region westus \
      --voices en-GB-LibbyNeural,en-GB-MaisieNeural,en-US-AnaNeural
  python generate_phonemes.py --provider polly --voices Amy,Ivy,Arthur

Then open out/index.html, pick the winner, and copy that voice's *_sound /
*_word files into the app (see README).
"""

from __future__ import annotations

import argparse
import json
import os
import sys
from dataclasses import dataclass
from pathlib import Path
from xml.sax.saxutils import escape


# --------------------------------------------------------------------------
# The English phoneme inventory (~44). IPA leans RP / en-GB, which is the
# closest fit for the UK/Singapore phonics curriculum. For a US voice, swap
# the accent-specific vowels noted below (/ɒ/->/ɑ/, /əʊ/->/oʊ/).
#
# NOTE on ASCII 'g': strict IPA uses U+0261 (ɡ) but both Polly and Azure
# accept ASCII 'g' (U+0067), which is safer for encoding. We use ASCII 'g'.
# --------------------------------------------------------------------------
@dataclass(frozen=True)
class Phoneme:
    slug: str   # file-safe id (also valid as an Android res/raw name)
    ipa: str    # IPA string for the SSML <phoneme ph=...>
    word: str   # example word rendered in the SAME voice


PHONEMES: list[Phoneme] = [
    # --- consonants (24) ---
    Phoneme("c_b", "b", "bat"),
    Phoneme("c_d", "d", "dog"),
    Phoneme("c_f", "f", "fish"),
    Phoneme("c_g", "g", "goat"),
    Phoneme("c_h", "h", "hat"),
    Phoneme("c_y", "j", "yes"),
    Phoneme("c_k", "k", "cat"),
    Phoneme("c_l", "l", "leg"),
    Phoneme("c_m", "m", "map"),
    Phoneme("c_n", "n", "net"),
    Phoneme("c_p", "p", "pig"),
    Phoneme("c_r", "r", "rat"),
    Phoneme("c_s", "s", "sun"),
    Phoneme("c_t", "t", "top"),
    Phoneme("c_v", "v", "van"),
    Phoneme("c_w", "w", "wet"),
    Phoneme("c_z", "z", "zip"),
    Phoneme("c_sh", "ʃ", "ship"),
    Phoneme("c_zh", "ʒ", "treasure"),
    Phoneme("c_ch", "tʃ", "chip"),
    Phoneme("c_j", "dʒ", "jam"),
    Phoneme("c_th_unvoiced", "θ", "thin"),
    Phoneme("c_th_voiced", "ð", "this"),
    Phoneme("c_ng", "ŋ", "ring"),
    # --- short vowels (6) ---
    Phoneme("v_a_short", "æ", "cat"),
    Phoneme("v_e_short", "e", "peg"),
    Phoneme("v_i_short", "ɪ", "pig"),
    Phoneme("v_o_short", "ɒ", "dog"),   # US: ɑ
    Phoneme("v_u_short", "ʌ", "cup"),
    Phoneme("v_oo_short", "ʊ", "book"),
    # --- long vowels (5) ---
    Phoneme("v_ar", "ɑː", "car"),
    Phoneme("v_ee", "iː", "see"),
    Phoneme("v_or", "ɔː", "door"),
    Phoneme("v_oo_long", "uː", "moon"),
    Phoneme("v_ur", "ɜː", "her"),
    # --- diphthongs (8) ---
    Phoneme("d_ai", "eɪ", "day"),
    Phoneme("d_ie", "aɪ", "pie"),
    Phoneme("d_oi", "ɔɪ", "boy"),
    Phoneme("d_ow", "aʊ", "cow"),
    Phoneme("d_oa", "əʊ", "boat"),      # US: oʊ
    Phoneme("d_ear", "ɪə", "ear"),
    Phoneme("d_air", "eə", "air"),
    Phoneme("d_ure", "ʊə", "pure"),
    # --- schwa (1) ---
    Phoneme("v_schwa", "ə", "about"),
]


# --------------------------------------------------------------------------
# Per-phoneme voice assignment, chosen by ear from the compare page. Each IPA
# symbol is rendered by whichever voice sounded best for it. Audio is keyed by
# *phoneme* (never by letter) — a letter has many sounds (c=/k/ or /s/, g=/g/
# or /dʒ/, every vowel), so only a phoneme id is unambiguous.
# --------------------------------------------------------------------------
VOICE_BY_IPA: dict[str, str] = {
    # en-GB-LibbyNeural
    "b": "en-GB-LibbyNeural", "k": "en-GB-LibbyNeural", "m": "en-GB-LibbyNeural",
    "v": "en-GB-LibbyNeural", "w": "en-GB-LibbyNeural", "z": "en-GB-LibbyNeural",
    "tʃ": "en-GB-LibbyNeural", "dʒ": "en-GB-LibbyNeural", "θ": "en-GB-LibbyNeural",
    "ð": "en-GB-LibbyNeural", "e": "en-GB-LibbyNeural", "ɪ": "en-GB-LibbyNeural",
    "ɒ": "en-GB-LibbyNeural", "ʌ": "en-GB-LibbyNeural", "iː": "en-GB-LibbyNeural",
    "ɔː": "en-GB-LibbyNeural", "uː": "en-GB-LibbyNeural", "aɪ": "en-GB-LibbyNeural",
    "ɔɪ": "en-GB-LibbyNeural", "ɪə": "en-GB-LibbyNeural", "eə": "en-GB-LibbyNeural",
    "ʊə": "en-GB-LibbyNeural", "ə": "en-GB-LibbyNeural", "ŋ": "en-GB-LibbyNeural",
    # en-GB-MaisieNeural
    "h": "en-GB-MaisieNeural", "j": "en-GB-MaisieNeural", "l": "en-GB-MaisieNeural",
    "p": "en-GB-MaisieNeural", "r": "en-GB-MaisieNeural", "ʒ": "en-GB-MaisieNeural",
    "æ": "en-GB-MaisieNeural", "ʊ": "en-GB-MaisieNeural", "ɑː": "en-GB-MaisieNeural",
    "aʊ": "en-GB-MaisieNeural", "əʊ": "en-GB-MaisieNeural",
    # en-US-AnaNeural
    "d": "en-US-AnaNeural", "f": "en-US-AnaNeural", "g": "en-US-AnaNeural",
    "n": "en-US-AnaNeural", "s": "en-US-AnaNeural", "t": "en-US-AnaNeural",
    "ʃ": "en-US-AnaNeural", "ɜː": "en-US-AnaNeural", "eɪ": "en-US-AnaNeural",
}


# --------------------------------------------------------------------------
# SSML builders
# --------------------------------------------------------------------------
def ssml_sound(ipa: str, alphabet: str, lang: str, voice: str | None,
               rate: str, volume: str, pad_ms: int) -> str:
    """SSML that speaks the isolated phoneme. Fallback text is empty so a
    non-supporting engine emits (near) nothing rather than a wrong word.

    - <prosody volume> lifts the soft/quiet isolated sounds.
    - <prosody rate> slows continuants (/s/ /m/ /f/...) for clarity; it can't
      lengthen a plosive burst (/b/ /d/ /g/ /p/ /t/ /k/) — those are physically
      a single release — but it doesn't hurt them.
    - <break> pads leading/trailing silence so the engine doesn't trim the
      burst flush to the clip edge, which is what makes plosives feel cut off.
    """
    ph = f'<phoneme alphabet="{alphabet}" ph="{escape(ipa, {chr(34): "&quot;"})}"></phoneme>'
    pad = f'<break time="{pad_ms}ms"/>'
    inner = f'{pad}<prosody rate="{rate}" volume="{volume}">{ph}</prosody>{pad}'
    return _wrap(inner, lang, voice)


def ssml_word(word: str, lang: str, voice: str | None) -> str:
    return _wrap(escape(word), lang, voice)


def _wrap(inner: str, lang: str, voice: str | None) -> str:
    # Azure requires <voice>; Polly takes the voice out-of-band and rejects it
    # in SSML, so voice is None for Polly.
    if voice:
        return (
            f'<speak version="1.0" xml:lang="{lang}" '
            f'xmlns:mstts="https://www.w3.org/2001/mstts">'
            f'<voice name="{voice}">{inner}</voice></speak>'
        )
    return f'<speak>{inner}</speak>'


# --------------------------------------------------------------------------
# Providers
# --------------------------------------------------------------------------
def synth_polly(ssml: str, voice: str, out_path: Path, fmt: str) -> None:
    import boto3  # lazy import so azure-only users need not install it

    client = _polly_client()
    resp = client.synthesize_speech(
        TextType="ssml",
        Text=ssml,
        VoiceId=voice,
        Engine="neural",
        OutputFormat="mp3" if fmt == "mp3" else "ogg_vorbis",
    )
    out_path.write_bytes(resp["AudioStream"].read())


_POLLY_SINGLETON = {}


def _polly_client():
    import boto3

    if "c" not in _POLLY_SINGLETON:
        _POLLY_SINGLETON["c"] = boto3.client("polly")
    return _POLLY_SINGLETON["c"]


def azure_key() -> str | None:
    """Azure Speech key: the AZURE_TTS_KEY env var first, else the same key from
    the repo's git-ignored local.properties (walking up from this file) — so a
    run works without exporting anything, mirroring how the app reads its keys."""
    key = os.environ.get("AZURE_TTS_KEY")
    if key:
        return key
    here = Path(__file__).resolve().parent
    for base in (here, *here.parents):
        lp = base / "local.properties"
        if lp.exists():
            for line in lp.read_text(encoding="utf-8", errors="ignore").splitlines():
                s = line.strip()
                if s.startswith("AZURE_TTS_KEY") and "=" in s:
                    return s.split("=", 1)[1].strip()
            break  # found local.properties but no key -> stop searching
    return None


def synth_azure(ssml: str, voice: str, out_path: Path, fmt: str, region: str) -> None:
    import requests  # lazy import

    key = azure_key()
    if not key:
        sys.exit("AZURE_TTS_KEY not found (set the env var or add it to local.properties)")
    out_fmt = (
        "audio-24khz-48kbitrate-mono-mp3"
        if fmt == "mp3"
        else "ogg-24khz-16bit-mono-opus"
    )
    resp = requests.post(
        f"https://{region}.tts.speech.microsoft.com/cognitiveservices/v1",
        headers={
            "Ocp-Apim-Subscription-Key": key,
            "Content-Type": "application/ssml+xml",
            "X-Microsoft-OutputFormat": out_fmt,
            "User-Agent": "ai4kids-phoneme-gen",
        },
        data=ssml.encode("utf-8"),
        timeout=30,
    )
    resp.raise_for_status()
    out_path.write_bytes(resp.content)


# --------------------------------------------------------------------------
# Compare page
# --------------------------------------------------------------------------
def write_compare_page(out_dir: Path, voices: list[str], ext: str) -> None:
    rows = []
    for p in PHONEMES:
        cells = [f"<th>/{escape(p.ipa)}/<br><small>{escape(p.word)}</small></th>"]
        for v in voices:
            d = _voice_dir(v)
            snd = f"{d}/{p.slug}_sound.{ext}"
            wrd = f"{d}/{p.slug}_word.{ext}"
            cells.append(
                f'<td><audio controls preload="none" src="{snd}"></audio>'
                f'<br><audio controls preload="none" src="{wrd}"></audio></td>'
            )
        rows.append(f"<tr>{''.join(cells)}</tr>")
    head = "".join(f"<th>{escape(v)}</th>" for v in voices)
    html = f"""<!doctype html><meta charset="utf-8">
<title>Phoneme voice compare</title>
<style>
 body{{font-family:system-ui,sans-serif;margin:24px}}
 table{{border-collapse:collapse}} td,th{{border:1px solid #ccc;padding:6px 8px;text-align:center;vertical-align:top}}
 th:first-child{{position:sticky;left:0;background:#fafafa}}
 audio{{height:30px;width:180px}}
</style>
<h1>Phoneme voice compare</h1>
<p>Top player = isolated sound, bottom = example word. Pick a column, then copy
that voice's files into the app (see README).</p>
<table><thead><tr><th>phoneme</th>{head}</tr></thead>
<tbody>{''.join(rows)}</tbody></table>"""
    (out_dir / "index.html").write_text(html, encoding="utf-8")


# --------------------------------------------------------------------------
def _voice_dir(voice: str) -> str:
    return voice.replace("/", "_")


def lang_for_voice(voice: str) -> str:
    """en-GB-LibbyNeural -> en-GB, en-US-AnaNeural -> en-US. Match xml:lang to
    the voice's own locale so <phoneme> IPA is interpreted in that accent."""
    parts = voice.split("-")
    return "-".join(parts[:2]) if len(parts) >= 2 else "en-GB"


def assemble_library(args) -> None:
    """Render the final phoneme library the game consumes: one isolated-sound
    clip per phoneme, each from its assigned voice (VOICE_BY_IPA), named by the
    phoneme slug (res/raw-safe) into one drop-in folder + a manifest.

    Sound-only: the game speaks whole words with TTS (which reads them fine);
    clips exist for the isolated phoneme sounds TTS gets wrong."""
    out: Path = args.assemble
    out.mkdir(parents=True, exist_ok=True)
    ext = args.format
    mapping, missing = [], []
    for p in PHONEMES:
        voice = VOICE_BY_IPA.get(p.ipa)
        if not voice:
            missing.append(f"{p.slug} (/{p.ipa}/)")
            continue
        lang = lang_for_voice(voice)
        ssml = ssml_sound(p.ipa, args.alphabet, lang,
                          voice if args.provider == "azure" else None,
                          args.rate, args.volume, args.pad_ms)
        dest = out / f"{p.slug}.{ext}"
        entry = {"slug": p.slug, "ipa": p.ipa, "example": p.word,
                 "voice": voice, "file": dest.name}
        if dest.exists() and not args.overwrite:
            mapping.append(entry)
            continue
        try:
            if args.provider == "polly":
                synth_polly(ssml, voice, dest, ext)
            else:
                synth_azure(ssml, voice, dest, ext, args.region)
            mapping.append(entry)  # record before logging so a console-encoding
            print(f"  ✓ {dest}  (/{p.ipa}/, {voice})")  # hiccup can't drop it
        except Exception as e:  # keep going; one bad phoneme != abort
            print(f"  ✗ {dest}: {e}", file=sys.stderr)

    (out / "phonemes_manifest.json").write_text(
        json.dumps(mapping, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"\nDone. {len(mapping)} phoneme clips in {out}")
    if missing:
        print(f"⚠ unassigned (add to VOICE_BY_IPA): {', '.join(missing)}", file=sys.stderr)
    print("Copy the .<ext> files into app/src/main/res/raw/ (names are res/raw-safe);")
    print("the game references a phoneme by slug, e.g. R.raw.v_a_short for /æ/.")


def main() -> None:
    # The IPA symbols and ✓/✗ markers are non-ASCII; a Windows console defaults
    # to cp1252 and would crash on them. Force UTF-8 output.
    for stream in (sys.stdout, sys.stderr):
        try:
            stream.reconfigure(encoding="utf-8", errors="replace")
        except (AttributeError, ValueError):
            pass

    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--provider", choices=["polly", "azure"], required=True)
    ap.add_argument("--voices",
                    help="compare mode: comma-separated voice ids to A/B (e.g. Amy,Ivy)")
    ap.add_argument("--assemble", type=Path, metavar="DIR",
                    help="assemble mode: render the final per-phoneme library "
                         "(voice per phoneme from VOICE_BY_IPA) into DIR")
    ap.add_argument("--alphabet", default="ipa", choices=["ipa", "x-sampa"])
    ap.add_argument("--lang", default="en-GB", help="xml:lang for SSML (Azure)")
    ap.add_argument("--region", default="", help="Azure region, e.g. westus")
    ap.add_argument("--format", default="mp3", choices=["mp3", "ogg"])
    ap.add_argument("--out", default="out", type=Path)
    ap.add_argument("--overwrite", action="store_true", help="re-render existing files")
    # --- isolated-sound shaping (fixes soft / clipped phonemes) ---
    ap.add_argument("--rate", default="slow",
                    help='prosody rate for the isolated sound (x-slow|slow|medium|fast or e.g. "-20%%")')
    ap.add_argument("--volume", default="loud",
                    help='prosody volume for the isolated sound: named (soft|medium|'
                         'loud|x-loud) works on both providers. Azure also takes a '
                         'percent (+30%%) or 0-100; dB works on Polly but NOT Azure.')
    ap.add_argument("--pad-ms", type=int, default=150, dest="pad_ms",
                    help="leading/trailing silence (ms) so plosive bursts aren't trimmed flush")
    args = ap.parse_args()

    if args.provider == "azure" and not args.region:
        sys.exit("--region is required for --provider azure")

    # Assemble mode: build the final library from the per-phoneme voice map.
    if args.assemble:
        assemble_library(args)
        return

    if not args.voices:
        sys.exit("give --voices (compare mode) or --assemble DIR (build the library)")
    voices = [v.strip() for v in args.voices.split(",") if v.strip()]
    ext = args.format
    args.out.mkdir(parents=True, exist_ok=True)
    manifest = {"provider": args.provider, "alphabet": args.alphabet,
                "lang": args.lang, "voices": voices, "phonemes": []}

    for p in PHONEMES:
        manifest["phonemes"].append({"slug": p.slug, "ipa": p.ipa, "word": p.word})

    for voice in voices:
        vdir = args.out / _voice_dir(voice)
        vdir.mkdir(parents=True, exist_ok=True)
        for p in PHONEMES:
            for kind, ssml in (
                ("sound", ssml_sound(p.ipa, args.alphabet, args.lang,
                                     voice if args.provider == "azure" else None,
                                     args.rate, args.volume, args.pad_ms)),
                ("word", ssml_word(p.word, args.lang,
                                   voice if args.provider == "azure" else None)),
            ):
                dest = vdir / f"{p.slug}_{kind}.{ext}"
                if dest.exists() and not args.overwrite:
                    continue
                try:
                    if args.provider == "polly":
                        synth_polly(ssml, voice, dest, ext)
                    else:
                        synth_azure(ssml, voice, dest, ext, args.region)
                    print(f"  ✓ {dest}")
                except Exception as e:  # keep going; one bad phoneme != abort
                    print(f"  ✗ {dest}: {e}", file=sys.stderr)

    (args.out / "manifest.json").write_text(
        json.dumps(manifest, ensure_ascii=False, indent=2), encoding="utf-8")
    write_compare_page(args.out, voices, ext)
    print(f"\nDone. Open {args.out / 'index.html'} to compare voices.")


if __name__ == "__main__":
    main()