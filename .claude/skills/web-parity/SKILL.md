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
| [ui/activities/phonics/](../../../app/src/main/java/sg/com/tertiarycourses/ai4kids/ui/activities/phonics/) | `src/app/learn/phonics/**` + `src/app/api/learn/phonics-buddy/route.ts` | Behaviour; Buddy hint = AI call |
| [ui/activities/StoryBuilderScreen.kt](../../../app/src/main/java/sg/com/tertiarycourses/ai4kids/ui/activities/StoryBuilderScreen.kt) | `src/app/learn/storytelling/**` + `src/app/api/learn/{story-builder,story-image}/route.ts` | Behaviour; AI is optional |
| [ui/activities/buddy/](../../../app/src/main/java/sg/com/tertiarycourses/ai4kids/ui/activities/buddy/) (Talking Buddy) | `src/app/learn/buddy/**` + `src/app/api/learn/buddy/{listen,speak}/route.ts` | Behaviour (Android uses on-device STT/TTS) |
| [ui/activities/art/](../../../app/src/main/java/sg/com/tertiarycourses/ai4kids/ui/activities/art/) (Art Studio) | `src/app/learn/{art,jigsaw,gallery}/**` + `src/app/api/learn/art/**` | Behaviour; AI image call |
| Star tally ([data/ProgressStore.kt](../../../app/src/main/java/sg/com/tertiarycourses/ai4kids/data/ProgressStore.kt)) | `src/app/api/learn/score/route.ts`, `src/app/learn/leaderboard/**` | **Local-only on Android** — see privacy note |
| [ui/activities/CodePuzzlesScreen.kt](../../../app/src/main/java/sg/com/tertiarycourses/ai4kids/ui/activities/CodePuzzlesScreen.kt) | *(no web counterpart — `coming-soon` on web)* | Android-original; document it |

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

For the `/learn/*` games, mirror **rules, content, and scoring**, not the DOM:

- Read the web game component under `src/app/learn/<game>/` for the actual rules (round structure, correct-answer logic, star/points awarded, difficulty/age gating).
- Match **scoring** to the web: award the same star counts via `ProgressStore.award(count, activity)` so a kid earns comparable rewards. Web posts to `/api/learn/score`; **Android keeps the tally local** (privacy) — mirror the *amounts*, not the network call.
- Match **content** (word lists, story branches, puzzle sets) where the web defines a canonical set — port the data, don't improvise a different one, unless the change is intentional and noted.
- **Translate idioms, don't transliterate:** React/Tailwind → Compose/Material 3 via the [kid-ui-design](../kid-ui-design/SKILL.md) skill (the web's bright kid aesthetic maps onto our `Theme` + `SharedUI`). Don't try to reproduce web markup 1:1.

## Privacy posture is a hard boundary — parity never overrides it

The web app has surfaces the offline-first Android app deliberately does **not** replicate. Do **not** port these across in the name of parity (see [CLAUDE.md](../../../CLAUDE.md) → Privacy posture):

- ❌ Server-side score/leaderboard sync for the **offline** activities — the Android star tally stays in local `SharedPreferences` (`ProgressStore`). Only the opt-in online features talk to the backend.
- ❌ Any analytics / tracking SDK the web uses — Android has **none**.
- ❌ Self-registration — Android `LoginScreen` is sign-in only; accounts are provisioned by a parent/admin.
- ✅ When you DO extend an online feature (new endpoint, new data leaving the device, new permission), keep the Play **Data Safety** form and permission disclosures in sync — same rule as the `privacy-check` concern.

## When invoked

1. Locate the web counterpart from the map above; open the route handler (contract) and/or the `/learn/<game>` component (behaviour) in the `ai4kids` repo.
2. If it's an **online** feature: diff the Zod request schema + response DTO against the Android request body + JSON parsing, field by field. Fix mismatches in the Android client.
3. If it's a **game**: mirror rules, content, and star amounts; render via `kid-ui-design`; keep the tally local.
4. Note any **intentional divergence** (offline substitute, on-device STT/TTS instead of the `buddy/listen` call, Android-original like Code Puzzles) in the file's header comment, per the repo convention of naming each file's counterpart.
5. Never drag web tracking/registration/leaderboard-sync into the offline core; keep Data Safety in sync when an online feature grows.