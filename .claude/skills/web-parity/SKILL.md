---
name: web-parity
description: Keeps the AI4Kids Android app in parity with its real source of truth — the ai4kids Next.js web app (the sibling `ai4kids` repo with the /learn games and the backend the online features call). Use whenever porting or changing an activity/game, or touching the online Brain Arcade (cards) or co-op Escape Room networking, so the Android build matches the web /learn game behaviour AND the /api/learn/* request/response contract. This supersedes iOS parity for the activities — the iOS app was only a bare-bones home screen; the web app is where the games and the API actually live.
---

# AI4Kids Android ↔ Web Parity

The **web app is the source of truth** for what the activities *do* and for the backend contract the online features depend on. The iOS app (`alfredang/ai4kidsapp`) only ever shipped the home-screen shell with stub activities — parity with iOS is cosmetic. Real behaviour, game content, scoring, and the network API all come from the **`ai4kids` Next.js repo** (sibling checkout, typically `../ai4kids` → `c:/Users/jun_sen/Documents/GitHub/ai4kids`).

There are **two kinds of parity**, and they have very different stakes:

| | What it is | Stakes if it drifts |
| --- | --- | --- |
| **A. API-contract parity** (HARD) | Android's networked clients must exactly match the web route handlers under `src/app/api/learn/*` | **Runtime break** — a payload/field/endpoint change on the server silently breaks the shipped app in users' hands |
| **B. Game-behaviour parity** (EXPERIENCE) | Android's activity screens should mirror the web `/learn/*` game rules, content, and scoring | Divergent experience — a kid gets a different game on Android than on web |

> **Offline-first caveat:** Android is offline-first; several activities have on-device versions with **no** server round-trip (phonics mini-games, story builder, code puzzles). Parity there means *behaviour/content*, not a network contract. Only the **opt-in online features** (cards, co-op escape) have a hard API contract. Don't invent a backend call for something that's meant to run offline.

## The correspondence map

Backend base URL: **`https://ai4kids.tertiarycourses.com.sg`** (`CardApi.DEFAULT_BASE`). Auth is **NextAuth** (`/api/auth/...`); the session cookie is shared between the cards and escape clients.

| Android | Web (source of truth) | Contract? |
| --- | --- | --- |
| [cards/CardApi.kt](../../../app/src/main/java/sg/com/tertiarycourses/ai4kids/cards/CardApi.kt), `CardGameScreen`, `BrainArcadeScreen` | `src/app/api/learn/cards/{create,join,start,move,sync}/route.ts` + `src/app/learn/cards/**` | **Yes — hard** |
| [escape/EscapeApi.kt](../../../app/src/main/java/sg/com/tertiarycourses/ai4kids/escape/EscapeApi.kt), `CoopSession`, `EscapeLobbyScreen` + [gdx/](../../../app/src/main/java/sg/com/tertiarycourses/ai4kids/gdx/) | `src/app/api/learn/escape/{create,join,start,solve,finish,sync}/route.ts` + `src/app/learn/escape-room/**` | **Yes — hard** |
| **Phonics** — data [PhonicsContent.kt](../../../app/src/main/java/sg/com/tertiarycourses/ai4kids/ui/activities/phonics/PhonicsContent.kt) · behaviour [PhonicsGames.kt](../../../app/src/main/java/sg/com/tertiarycourses/ai4kids/ui/activities/phonics/PhonicsGames.kt) + [PhonicsScreen.kt](../../../app/src/main/java/sg/com/tertiarycourses/ai4kids/ui/activities/phonics/PhonicsScreen.kt) · audio [PhonemeAudio.kt](../../../app/src/main/java/sg/com/tertiarycourses/ai4kids/ui/activities/phonics/PhonemeAudio.kt) | data `src/lib/phonics/content.ts` · behaviour `src/app/learn/phonics/PhonicsQuest.tsx` · `src/app/api/learn/phonics-buddy/route.ts` | Behaviour; Buddy hint = AI call |
| **Story Builder** — data/logic [StoryEngine.kt](../../../app/src/main/java/sg/com/tertiarycourses/ai4kids/ui/activities/StoryEngine.kt) · UI [StoryBuilderScreen.kt](../../../app/src/main/java/sg/com/tertiarycourses/ai4kids/ui/activities/StoryBuilderScreen.kt) | data `src/lib/story-builder/templates.ts` · behaviour `src/app/learn/storytelling/page.tsx` · `src/app/api/learn/{story-builder,story-image}/route.ts` | Behaviour; AI is optional |
| [ui/activities/buddy/](../../../app/src/main/java/sg/com/tertiarycourses/ai4kids/ui/activities/buddy/) (Talking Buddy) | `src/app/learn/buddy/**` + `src/app/api/learn/buddy/{listen,speak}/route.ts` | Behaviour (Android uses on-device STT/TTS) |
| [ui/activities/art/](../../../app/src/main/java/sg/com/tertiarycourses/ai4kids/ui/activities/art/) (Art Studio) | `src/app/learn/{art,jigsaw,gallery}/**` + `src/app/api/learn/art/**` | Behaviour; AI image call |
| Star tally ([data/ProgressStore.kt](../../../app/src/main/java/sg/com/tertiarycourses/ai4kids/data/ProgressStore.kt)) | `src/app/api/learn/score/route.ts`, `src/app/learn/leaderboard/**` | **Local-only on Android** — see privacy note |
| [ui/activities/CodePuzzlesScreen.kt](../../../app/src/main/java/sg/com/tertiarycourses/ai4kids/ui/activities/CodePuzzlesScreen.kt) + [CodePuzzlesEngine.kt](../../../app/src/main/java/sg/com/tertiarycourses/ai4kids/ui/activities/CodePuzzlesEngine.kt) | `src/app/learn/code-puzzles/**` + `src/lib/code-puzzles.ts` | Behaviour; **Android is the source of truth here** (the web port came from it) |

Before starting, confirm the web repo is checked out and note its path. If it isn't present, say so and ask for it rather than guessing the contract from memory.

## A. Checking API-contract parity (the one that breaks users)

The **Zod schemas and JSON responses in the web route handlers are the contract.** Android hand-parses JSON with `org.json`, so nothing catches a mismatch at compile time — you must diff by hand.

1. **Open the matching web route handler** (`src/app/api/learn/<feature>/<action>/route.ts`). Read:
   - the **request** `z.object({...})` schema (field names, types, min/max, which are required),
   - the **response** shape it returns (`NextResponse.json({...})`), especially the redacted state DTO.
2. **Open the Android caller** and confirm:
   - the **path** matches (`/api/learn/cards/move`, etc.),
   - the **request body keys** match the Zod schema exactly (e.g. cards `move` posts `{ code, move }`),
   - the **response parsing** reads the same field names/types. The DTOs already flag their origin — e.g. `EscapeState` says *"Mirrors the server's `SessionStateDTO`"* and `status` ∈ `"lobby" | "playing" | "escaped"`. Keep those enums and field names identical.
3. **Auth:** all `/api/learn/*` calls require a learner NextAuth session (handlers return `403 "Learners only"` otherwise). Don't add an endpoint that assumes a different auth model; reuse `CardApi`'s shared session cookie.
4. **When the web contract changes**, treat it as a breaking change to a deployed client: update the Android request/response code in the same change, and note it. A field rename on the server with no Android update = a field silently read as null/absent in production.

Report contract findings as `Android <file:line>` ↔ `web <route.ts:line>` with the exact field that differs.

## B. Checking game-behaviour parity

### Every game has two layers, and they drift independently

| Layer | Web | Android | What drifts |
| --- | --- | --- | --- |
| **Data** | `src/lib/<game>/*.ts` | `*Content.kt` / `*Engine.kt` | word lists, levels, story beats, star thresholds |
| **Behaviour** | `src/app/learn/<game>/*.tsx` | `*Games.kt` / `*Screen.kt` | how a round is actually *played* — the taps, the timing, the feedback |

> **Identical data files are NOT parity. A data diff cannot see behaviour.**
>
> This has already produced one wrong call, so take it literally: phonics' `content.ts` and `PhonicsContent.kt` were **byte-identical** — same seven worlds, same rounds, same 44 phoneme clips — while the web's Build game had gained a **tap-to-arm** mechanic (first tap hears the letter, second commits it) that Android lacked entirely, plus a slower playback rate, end-timed sound gaps, and cancel-on-advance. All of it lived in `PhonicsQuest.tsx`, which the data diff never touched.
>
> **If you have only diffed the data layer, you have not checked parity.** Say "content matches; behaviour unchecked" — never "at parity".

**Read the web component top-to-bottom and diff it function-by-function** against its Kotlin counterpart. Both files are ~1000 lines and the divergence hides in the middle, not the exports. Specifically check:

- **Interaction model** — how many taps commit an answer? Is there a preview/arm step *before* judging? When exactly is a mistake counted? The web tends to let a child explore unpunished; Android often judges the first tap.
- **Audio & timing** — playback rate and pitch handling; whether gaps are timed from a clip's **end** or its start; lead-in silences; and what cuts a sound when the child moves on. Compose cancels coroutines, but **not** a `MediaPlayer` — cancellation and audio are separate problems.
- **Async fallbacks** — timeouts on AI calls, and the canned fallback shown *and spoken* instead. The web caps what a child will sit through; Android must too.
- **Feedback & praise** — instant/hardcoded, or a model round-trip? The web has deliberately moved *away* from LLM calls on the celebration path.
- **Round/progression flow** — child-paced vs auto-advance, and whether win state resets between rounds.
- **Copy is behaviour** — prompt and hint text tell the child what to do; a state-driven prompt ("Tap it again to place it!") is part of the mechanic, not decoration.

Then, as before:

- Match **scoring** to the web: award the same star counts via `ProgressStore.award(count, activity)` so a kid earns comparable rewards. Web posts to `/api/learn/score`; **Android keeps the tally local** (privacy) — mirror the *amounts*, not the network call.
- Match **content** (word lists, story branches, puzzle sets) where the web defines a canonical set — port the data, don't improvise a different one, unless the change is intentional and noted.
- **Translate idioms, don't transliterate:** React/Tailwind → Compose/Material 3 via the [kid-ui-design](../kid-ui-design/SKILL.md) skill (the web's bright kid aesthetic maps onto our `Theme` + `SharedUI`). Don't try to reproduce web markup 1:1.

**Web idioms that need a real Android equivalent, not a transliteration.** The web solves these for free; Android does not, and each has already been a bug here:

| Web | Android equivalent |
| --- | --- |
| `await play()` resolving on the `"ended"` event | `suspend fun` over `setOnCompletionListener` — a fixed `delay()` guessed from the clip length chops the sound |
| pausing audio emits `"pause"`, settling a superseded promise | `release()` fires **no** callback — you must resume the pending continuation yourself or its caller hangs forever |
| `speechSynthesis.cancel()` before a clip | `tts.stop()` — otherwise TTS and the clip duck each other |
| `useSequencer`'s `alive()` guard / unmount cleanup | key a `LaunchedEffect` on the round **and** stop the player; a `rememberCoroutineScope()` isn't tied to the round |

## Privacy posture is a hard boundary — parity never overrides it

The web app has surfaces the offline-first Android app deliberately does **not** replicate. Do **not** port these across in the name of parity (see [CLAUDE.md](../../../CLAUDE.md) → Privacy posture):

- ❌ Server-side score/leaderboard sync for the **offline** activities — the Android star tally stays in local `SharedPreferences` (`ProgressStore`). Only the opt-in online features talk to the backend.
- ❌ Any analytics / tracking SDK the web uses — Android has **none**.
- ❌ Self-registration — Android `LoginScreen` is sign-in only; accounts are provisioned by a parent/admin.
- ✅ When you DO extend an online feature (new endpoint, new data leaving the device, new permission), keep the Play **Data Safety** form and permission disclosures in sync — same rule as the `privacy-check` concern.

## When invoked

1. Locate the web counterpart from the map above. **New work is web-first** — port web → Android. Only dig into direction (file headers, then `git log -S`) when reconciling *existing* code, where history still runs both ways.
2. Open **both** layers on both sides — the `src/lib/<game>` data module *and* the `/learn/<game>` component (behaviour), plus the route handler for anything networked. Opening one and inferring the other is the known failure mode.
3. If it's an **online** feature: diff the Zod request schema + response DTO against the Android request body + JSON parsing, field by field. Fix mismatches in the Android client.
4. If it's a **game**: diff data *and* behaviour; mirror rules, content, and star amounts; render via `kid-ui-design`; keep the tally local.
5. Note any **intentional divergence** (offline substitute, on-device STT/TTS instead of the `buddy/listen` call, Gemini-on-device instead of the web's Claude route, Android-original like Code Puzzles) in the file's header comment, per the repo convention of naming each file's counterpart.
6. Never drag web tracking/registration/leaderboard-sync into the offline core; keep Data Safety in sync when an online feature grows.
7. **Report honestly:** state which layers you read; scope claims to those layers.

## Direction: web-first for all new work

**As of 2026-07-17 the rule is: key changes are made on the WEB first, then ported to Android.** Web is the origin. For anything new, don't re-derive direction — port web → Android and move on.

This rule exists because direction used to vary per feature *and per layer*, which caused a real mix-up (below). Making web the single origin removes the ambiguity going forward.

If a change *does* land on Android first, treat it as a deliberate exception: say so, and mirror it back to web in the same breath, or the two silently diverge again.

### History still runs both ways — this rule doesn't rewrite it

When **reading existing code**, direction is still per-layer, so don't assume the web was always ahead:

- **Code Puzzles is an Android-original**, ported *to* the web (`/learn/code-puzzles`, engine in `src/lib/code-puzzles.ts`). `CodePuzzlesEngine.kt` remains the source of truth for its rules and `LEVELS`. Change the Kotlin engine first, then mirror it. The web port has no unit tests (that repo has no test suite); its `npm run check:code-puzzles` stands in for `CodePuzzlesEngineTest.everyLevelHasASolutionWithinMaxMoves`, so if you edit LEVELS on either side, re-run both guards.
- **Phonics ran BOTH ways at once.** Its *content* flowed Android → web (`src/lib/phonics/content.ts` opens with "Ported from the ai4kids Android app (PhonicsContent.kt)"), while its *behaviour* ran ahead on the **web**. Same feature, opposite directions per layer.

**Never infer direction from a commit title.** The web's `feat(learn): expand phonics worlds` commit *added* worlds Android already had — it was the web catching up, and reading it as "the web pulled ahead" produced a scope that was backwards. When you need to establish direction on existing code:

- **Read the file header.** Both repos name their counterpart in the top doc comment. That's the cheapest, most reliable signal.
- **`git log -S'<symbol>' -- <path>`** on *both* repos to see which one introduced it first, and compare the dates.

## Before you report "at parity"

Parity claims are load-bearing — they decide whether work happens. Do not make one you have not earned:

- Name **which layers you actually read**, on both sides. "Content matches" and "at parity" are different claims.
- A byte-identical data file is evidence about **data only**. Never generalize it to the feature.
- If the user says something diverged, **believe them and go look** at the layer you skipped — they have used the app. A file you did not open is not evidence of absence. Re-read the behaviour component before pushing back.