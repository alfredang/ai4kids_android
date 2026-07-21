# Phoneme audio generator (build-time)

Renders the ~44 English phonemes (isolated "pure" sound) + a matching example
word, using a cloud TTS engine that honours SSML `<phoneme>` (IPA). This runs
**once on a dev machine** — the audio ships in the APK, so the app plays clips
fully offline and **nothing leaves the device at runtime**. This is a dev tool;
it is not part of the app and never ships in the APK.

Why not on-device TTS? Android's on-device engine (`com.google.android.tts`)
reads text *as words* and ignores `<phoneme>`, so "ah" ≠ /æ/. Polly and Azure
honour `<phoneme>`, giving a correct, natural, kid-appropriate isolated sound.

## Setup

```bash
pip install boto3 requests        # boto3 only for Polly, requests only for Azure

# Polly: standard AWS creds (env vars, ~/.aws/credentials, or SSO)
export AWS_PROFILE=...            # or AWS_ACCESS_KEY_ID / AWS_SECRET_ACCESS_KEY

# Azure: the Speech resource key, EITHER as an env var...
export AZURE_TTS_KEY=...          # ...and pass --region too
# ...OR add it to the repo's git-ignored local.properties (same place the app
# keeps its keys) and the script reads it from there automatically:
#   AZURE_TTS_KEY=<key>
```

Never commit the key. `local.properties` is git-ignored; keep it there or in an
env var, never in source or this README.

## Batch a few voices, then compare

```bash
# Azure — good child / en-GB voices
python generate_phonemes.py --provider azure --region westus \
    --voices en-GB-LibbyNeural,en-GB-MaisieNeural,en-US-AnaNeural

# Polly — Amy (en-GB), Ivy (en-US child), Arthur (en-GB)
python generate_phonemes.py --provider polly --voices Amy,Ivy,Arthur
```

Open **`out/index.html`** — a table with an inline player per phoneme per voice
(top = isolated sound, bottom = example word). Listen, pick the column that
sounds best, and check especially the tricky ones: the "pure" consonants
(/b/ /d/ /p/ without a trailing "-uh"), the digraphs (sh/ch/th/ng), and the
short vowels.

### Accent note

The IPA table leans **RP / en-GB** (closest to the UK/Singapore phonics
curriculum). For a US voice, two vowels differ: `/ɒ/`→`/ɑ/` (dog) and
`/əʊ/`→`/oʊ/` (boat). Match the voice's locale to the phonics scheme you teach,
and render the isolated sound + its example word in the **same** voice so they
sound like one person.

## Assemble the final library (per-phoneme voices)

You don't have to pick one voice for everything — the best voice often differs
per sound. Record your choices in `VOICE_BY_IPA` at the top of
`generate_phonemes.py` (IPA symbol → voice id), then run **assemble mode** to
render one isolated-sound clip per phoneme, each from its assigned voice:

```bash
python generate_phonemes.py --provider azure --region southeastasia \
    --assemble out/library
```

This writes one clip per phoneme, **named by phoneme slug** (not by letter):

```
out/library/
  c_b.mp3          # /b/
  v_a_short.mp3    # /æ/
  d_oa.mp3         # /əʊ/
  phonemes_manifest.json
```

**Keyed by phoneme, never by letter.** A letter has many sounds — `c` is /k/
*or* /s/, `g` is /g/ *or* /dʒ/, every vowel letter has several — so audio is
addressed by its phoneme slug. In the game, a round names the actual phoneme it
teaches (Apple → `v_a_short`, Circle → `c_s`) and plays that clip.

Sound-only: the game still speaks whole words with TTS (it reads words fine);
the clips exist only for the isolated sounds TTS gets wrong.

## Ship into the app

The slugs are valid Android `res/raw` names (lowercase, `_`-safe). Copy the
clips into either:

- `app/src/main/res/raw/`  — reference via `R.raw.v_a_short`, or
- `app/src/main/assets/phoneme/` — reference by asset path (keeps the `res/raw`
  namespace uncluttered).

`phonemes_manifest.json` (slug → ipa + example word + voice) is the mapping a
`PhonemePlayer` loads to find the clip for a phoneme.

## Licensing

Both providers permit redistributing generated speech in your product
(Amazon Polly and Azure Speech both allow it under their service terms). Keep a
note of the voice + provider used. No CC-BY / GPL obligations, unlike Wikipedia
IPA audio or bundling eSpeak-NG.