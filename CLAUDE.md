# CLAUDE.md

Guidance for working in this repository.

## What this is

Native **Android** port of the AI4Kids iOS app
([alfredang/ai4kidsapp](https://github.com/alfredang/ai4kidsapp)). A tablet-first
(also runs on phone) educational activity app for ages 4–16, built with **Kotlin +
Jetpack Compose + Material 3**.

Core principles: **no ads, no analytics, no third-party SDKs.** The four learning
activities and solo card games play **fully offline with no login and no data
collection**. The **one** exception is the optional online "Brain Arcade"
multiplayer, which uses INTERNET to talk to the ai4kids backend and signs in with
a **username-only** kid account (no name/email/phone/location).

**Google Play Families Policy:** the app targets children (Designed for Families),
so a **parental gate** (`cards/ParentalGate.kt`) guards (1) first launch — a
one-time parental consent persisted by `data/ConsentStore.kt`, and (2) the Brain
Arcade sign-in, immediately before any data leaves the device. The Data safety
declaration must list **User IDs** (the username) as collected for app
functionality, not shared. Do not add ads, analytics, or SDKs that would break
Families Policy compliance.

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
  - `ui/activities/` — one screen per activity

## Conventions

- Keep parity with the iOS source where practical; each file notes its iOS
  counterpart.
- UI is 100% Compose; the XML theme only styles the window/status bar pre-Compose.
- Award stars through `ProgressStore.award(count, activity)` so totals persist.

## Status

- **Phonics** is currently a placeholder ("Coming soon") page — the activity is
  being redesigned. The other three activities are fully implemented.
