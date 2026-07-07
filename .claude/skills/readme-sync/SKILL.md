---
name: readme-sync
description: Keeps the repo's READMEs accurate and on-voice as the app changes. There are TWO — the root README.md (product showcase: badges, activity/worlds/rooms/games tables, tech-stack versions, ASCII architecture diagram, project-structure tree, setup + privacy) and tools/phoneme-tts/README.md (a terse dev-tool doc for the build-time phoneme audio generator). Both silently drift from the code. Use after adding/renaming/removing an activity, mini-game, room, card game, dependency, permission, package, or changing the phoneme tool — or when asked to "update the README" — to reconcile each section against its real source of truth and preserve each file's distinct format.
---

# README Sync — AI4Kids Android

There are **two READMEs with different jobs and different voices** — keep them straight and don't cross-contaminate style:

1. **[README.md](../../../README.md)** (root) — a **product showcase**: centered header, shields.io badges, one emoji per list item, curated tables. Marketing-lite but every claim must be true.
2. **[tools/phoneme-tts/README.md](../../../tools/phoneme-tts/README.md)** — a **terse dev-tool doc** for the build-time phoneme audio generator. Plain, command-first, no badges/emoji. It documents a Python script that never ships in the APK.

## Golden rule

**Read the source before you touch a number, name, version, flag, or path.** Every claim has a file that owns it — open that file; don't trust the README's current value (it may be the stale one). Don't `git add`/commit unless asked ([git-push](../../commands/git-push.md)'s job).

## Root README — section → source of truth

| Section | Verify against |
| --- | --- |
| Top **badges** + **Tech Stack** table (Kotlin, Min/Target SDK, Compose BOM, AGP) | [app/build.gradle.kts](../../../app/build.gradle.kts) + root [build.gradle.kts](../../../build.gradle.kts). Badge ↔ table ↔ prose must state the **same** numbers. |
| **Activities** table + home grid | [model/Activity.kt](../../../app/src/main/java/sg/com/tertiarycourses/ai4kids/model/Activity.kt) — single source of truth for title/colour/age/icon. Also what exists under [ui/activities/](../../../app/src/main/java/sg/com/tertiarycourses/ai4kids/ui/activities/) incl. `buddy/` (Talking Buddy), `art/` (Art Studio). |
| **Phonics Quest — five worlds** | [ui/activities/phonics/PhonicsContent.kt](../../../app/src/main/java/sg/com/tertiarycourses/ai4kids/ui/activities/phonics/PhonicsContent.kt). |
| **Escape Room — themed rooms** | [gdx/EscapeGdxGame.kt](../../../app/src/main/java/sg/com/tertiarycourses/ai4kids/gdx/EscapeGdxGame.kt). |
| **Brain Arcade — card games** | [cards/CardGameMeta.kt](../../../app/src/main/java/sg/com/tertiarycourses/ai4kids/cards/CardGameMeta.kt). |
| **Architecture** ASCII diagram | Real wiring: `MainActivity`, `RootScreen`, `EscapeApi`/`CardApi`, backend endpoints. Conceptual but true. |
| **Project Structure** tree | The real tree under [app/src/main/java/…/ai4kids/](../../../app/src/main/java/sg/com/tertiarycourses/ai4kids/); keep the one-line purpose comments. |
| **Getting Started** (base URL, cleartext hosts, `GEMINI_API_KEY`) | `CardApi.DEFAULT_BASE`, [res/xml/network_security_config.xml](../../../app/src/main/res/xml/network_security_config.xml), the `GEMINI_API_KEY` wiring in `build.gradle.kts`. |
| **Privacy** section | Posture in [CLAUDE.md](../../../CLAUDE.md), [AndroidManifest.xml](../../../app/src/main/AndroidManifest.xml) permissions, network config. Moves together with the Play Data Safety form — see [privacy-check](../privacy-check/SKILL.md). |
| **Screenshots** | Files present under `docs/screenshots/`. Don't reference an image that doesn't exist. |

## Phoneme-tool README — section → source of truth

| Section | Verify against |
| --- | --- |
| Setup / provider flags / env vars | [tools/phoneme-tts/generate_phonemes.py](../../../tools/phoneme-tts/generate_phonemes.py) — the actual `argparse` flags (`--provider`, `--region`, `--voices`, `--assemble`), provider list, and how it reads `AZURE_TTS_KEY` from `local.properties`/env. |
| `VOICE_BY_IPA` / per-phoneme voices | The `VOICE_BY_IPA` map at the top of the script. |
| Slug scheme + `phonemes_manifest.json` shape | The script's naming/output code and the app-side player [ui/activities/phonics/PhonemeAudio.kt](../../../app/src/main/java/sg/com/tertiarycourses/ai4kids/ui/activities/phonics/PhonemeAudio.kt) (slugs are `res/raw`-safe; keyed by phoneme, never by letter). |
| "Ship into the app" paths | `app/src/main/res/raw/` vs `app/src/main/assets/phoneme/` — whichever the player actually loads from. |
| Keys/licensing notes | Keep the "never commit the key; `local.properties` is git-ignored; runs on a dev machine, nothing leaves the device at runtime" framing — it's part of the privacy posture. |

## Voice & format (preserve, don't "improve")

- **Root:** keep the `<div align="center">` header + badge row + shields.io style (change a badge's value, not its style); one emoji per activity/world/room/game matching the code's icon; existing table column shapes; British/Singapore spelling; honest, no aspirational features.
- **Tool:** stay plain and command-first — no badges, no emoji, no marketing voice. Match the existing terse register.

## The iOS-vs-web framing caveat

The root README frames the app as an **"Android port of the iOS app"** and Contributing says *"keep parity with the iOS source."* Per [web-parity](../web-parity/SKILL.md), the **web app is the real source of truth** for the activities and API — iOS was only a bare-bones home shell. If revising those lines, reconcile the framing, but **flag it to the user rather than silently rewriting the app's stated identity** — it's an editorial call, not a code fact.

## Workflow

1. Identify which README(s) and section(s) the change touches.
2. For each, **open the source-of-truth file** and diff it against the README text.
3. Edit only the drifted lines; preserve surrounding format, emoji, and voice (per file).
4. Keep the root README numerically consistent (a version appears in badge + table + prose — update all).
5. If the change ships data off-device or alters a permission, hand the Data Safety / disclosure step to [privacy-check](../privacy-check/SKILL.md).
6. Summarise what changed and which source proved the old value stale.

## Known-stale spots to check first (root README, as of writing)

Verify and fix if still wrong:

- **Compose BOM** — Tech Stack says `2024.09.03`; `app/build.gradle.kts` uses a **newer** BOM (`2024.12.01`). Update table (and any badge).
- **`ai/` in the structure tree** — lists only `GeminiClient.kt`, but the package also has `CloudflareClient.kt` (Flux image fallback) and `ArtEngine.kt`. Add them.
- **Missing home activities** — CLAUDE.md places **Talking Buddy** (`ui/activities/buddy/`) and **Art Studio** (`ui/activities/art/`) on the home grid, but they're in neither the Activities table nor the structure tree. Confirm against `model/Activity.kt` and add (with their Gemini + Cloudflare connectivity noted).
