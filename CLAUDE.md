# CLAUDE.md

Guidance for working in this repository.

## What this is

Native **Android** port of the AI4Kids iOS app
([alfredang/ai4kidsapp](https://github.com/alfredang/ai4kidsapp)). An educational
activity app for ages 4–16, built with **Kotlin + Jetpack Compose + Material 3**.

The app is **offline-first**: the four home activities and the local star tally
run on-device with no account, and every one of them is fully playable with no
keys and no network. Two of them have *optional* keyed AI on top (see below), so
"nothing leaves the phone" holds for the **default, keyless** build — not
unconditionally. The home grid
([`model/Activity.kt`](app/src/main/java/sg/com/tertiarycourses/ai4kids/model/Activity.kt))
holds:

- **Phonics Playground** ("Phonics Quest" — adventure-map mini-games)
- **Story Builder**
- **Code Puzzles**
- **Escape Room** (a LibGDX top-down game)

Layered on top are **optional online features** that require a learner sign-in and
talk to the ai4kids backend:

- **Brain Arcade** — networked card games (`cards/`)
- **Co-op Escape Rooms** — multiplayer sessions over the same Escape Room
  (`escape/` session layer + the LibGDX game in `gdx/`)

Two **AI activities** live on the home grid and call Google's **Gemini API**
(with a Cloudflare Workers AI image fallback) when a key is supplied — they show a
friendly "ask a grown-up" state when no key is configured:

- **Talking Buddy** (`ui/activities/buddy/`) — chat by voice or text. Replies come
  from Gemini; **speech-in uses on-device `SpeechRecognizer`** and **speech-out
  on-device `TextToSpeech`**, so only the chat *text* leaves the phone.
- **Art Studio** (`ui/activities/art/`) — an AI-painted picture (Gemini "Nano
  Banana", Cloudflare Flux fallback) the child can then turn into a jigsaw.

Two of the offline-core activities also call Gemini when a key is set:

- **Phonics Quest**'s "Buddy" sends a short prompt for a hint.
- **Story Builder** has two modes with *different* privacy weight. **"Build a
  story"** weaves the tale from only the four ingredient picks — never free text —
  and falls back to on-device templates, so it works with no key at all; when an
  *image* key is also set it illustrates each page via `ArtEngine.paint` (sending
  that page's prose, not the child's words, to NVIDIA/Cloudflare), otherwise pages
  stay emoji-only and the mode is fully offline. **"Write your own"**
  (`WriteMode`) sends the child's **typed idea** (≤300 chars) to Gemini: first to
  a kid-safety gate (`GeminiClient.classifyStoryIdea`), then to write a 3-scene
  story. Each scene is then **illustrated** via `ArtEngine.paint` — the same
  NVIDIA-FLUX-then-Cloudflare image path as Art Studio — with an emoji as the
  loading/failure fallback; nothing is saved off-device. With no key that mode
  alone hides behind the "ask a grown-up" state while Build mode stays fully
  playable.

All Gemini/Cloudflare usage stays fully offline when the keys are blank.

## Privacy posture

- **No ads. No third-party analytics or tracking SDKs.**
- The **offline core collects nothing** — `ProgressStore` keeps stars in local
  `SharedPreferences` only.
- **Online features are opt-in behind a sign-in** and *do* transmit account +
  gameplay data to the ai4kids backend (NextAuth session, room codes, moves,
  co-op presence). The app is therefore **not** "Data Not Collected" — keep the
  Play **Data Safety** form in sync whenever these features change.
- **AI activities (Talking Buddy, Art Studio)** send the child's typed/spoken
  message or drawing prompt to **Google Gemini** (and, for the image fallback,
  **Cloudflare Workers AI**) — off-device. The Talking Buddy also uses Android's
  **`SpeechRecognizer`**, which may send audio to Google for cloud recognition
  (we pass `EXTRA_PREFER_OFFLINE`); `TextToSpeech` stays on-device. These are
  keyed features that stay dormant with no key — but when enabled they collect,
  so keep the **Data Safety** form and the `RECORD_AUDIO` permission disclosure
  in sync. Keys live in git-ignored `local.properties` → `BuildConfig`, never in
  source or on the client UI.
- Kids **don't self-register**; accounts are provisioned by a parent/admin
  (`LoginScreen` is sign-in only).
- **Network:** production is HTTPS-only; cleartext is permitted *only* for local
  dev hosts (`res/xml/network_security_config.xml`). `AndroidManifest.xml`
  requests `INTERNET` + `ACCESS_NETWORK_STATE`.
- The auth **session cookie is stored in `EncryptedSharedPreferences`**
  (Keystore-backed) and excluded from backup/device-transfer.

Before adding anything that sends data off-device or pulls in a new SDK, confirm
it fits this posture **and** update the privacy disclosure.

## Layout

- `app/src/main/java/sg/com/tertiarycourses/ai4kids/` — all Kotlin source
  - `model/Activity.kt` — the four activities (single source of truth for title,
    color, age band, icon)
  - `data/ProgressStore.kt` — local star tally in `SharedPreferences`, exposed as
    Compose state via `LocalProgressStore`
  - `ui/theme/Theme.kt` — brand palette/shapes/shadows (mirrors iOS `Theme.swift`)
  - `ui/RootScreen.kt`, `ui/ParentsCornerSheet.kt`
  - `ui/components/SharedUI.kt` — `KidButton`, `StarBadge`, `CloseButton`,
    `CelebrationView`, `kidCard`/`softShadow` modifiers
  - `ui/activities/` — one screen per home activity
  - `ui/activities/phonics/` — Phonics Quest content + optional Gemini "Buddy"
  - `ui/activities/buddy/` — **Talking Buddy** (`TalkingBuddyScreen`, `BuddyFace`
    Canvas, `BuddyVoice` on-device TTS)
  - `ui/activities/art/` — **Art Studio** (`ArtStudioScreen`, `JigsawBoard`)
  - `ai/` — `GeminiClient` (text/JSON/chat/image), `CloudflareClient` (Flux image
    fallback), `ArtEngine` (safety-gate + generate orchestrator)
  - `cards/` — online **Brain Arcade**: `CardApi.kt` (OkHttp + NextAuth client,
    Keystore-encrypted session), `LoginScreen.kt`, `BrainArcadeScreen.kt`,
    `CardGameScreen.kt`
  - `escape/` — co-op Escape Room session layer: `EscapeApi.kt`, `CoopSession.kt`,
    `EscapeLobbyScreen.kt` (reuses `CardApi`'s session cookie)
  - `gdx/` — LibGDX Escape Room game (`EscapeGdxGame.kt`, `EscapeActivity.kt`)

## Conventions

- Keep parity with the iOS source where practical; each file notes its iOS
  counterpart.
- UI is 100% Compose (except the LibGDX Escape Room, which renders in its own
  `EscapeActivity` surface); the XML theme only styles the window/status bar
  pre-Compose.
- Award stars through `ProgressStore.award(count, activity)` so totals persist.
- Networking goes through the existing OkHttp clients (`CardApi` / `EscapeApi`),
  which share a single NextAuth session cookie. Keep their blocking calls on a
  background dispatcher (`Dispatchers.IO`).

## Status

- All four home activities are implemented. **Phonics Playground** is now
  "Phonics Quest" (an adventure map of mini-games), replacing the old "Coming
  soon" placeholder.
- **Brain Arcade** (online cards) and **co-op Escape Rooms** are the optional
  online features — both require a learner sign-in (see Privacy posture).
