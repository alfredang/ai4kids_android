# Changelog

All notable changes to the AI4Kids Android app. Format follows
[Keep a Changelog](https://keepachangelog.com); the newest entry's bullets are
the source of truth for the Play Store release notes.

## [1.2] — 2026-07-21 · versionCode 4

### Added
- Phonics Quest adventure map with new mini-games: Sound Blender, Sound Buddies, Build the Word, and on-device phoneme audio.
- Story Builder branching tales with illustrated story pages.
- Code Puzzles: Scratch-style repeat loops, wider 7–12 age band, and plan kept on failed runs so kids can debug.
- Escape Room: new themed rooms (Kindness Castle, Green Lab, History Vault, Lion City Carnival), co-op escape rooms with room codes, teammate avatars, and per-room decor.
- Talking Buddy and AI Art Studio activities (dormant in this build — they show an "ask a grown-up" screen; no AI keys are shipped).
- Parental gate now also guards the co-op Escape Room sign-in.

### Changed
- Landscape layout polish across activities.
- Auth session cookie is now encrypted (Android Keystore) and excluded from backup.

### Fixed
- Talking Buddy no longer echoes role labels in replies.
- Various puzzle and phonics fixes.

### Play submission
- Closed testing — Alpha track, "All Testers" email list, Singapore. Submitted 2026-07-21.
- Build ships keyless: Gemini/NVIDIA/Cloudflare AI paths are inactive, so Data safety remains "User IDs only, encrypted in transit, not shared".
- RECORD_AUDIO permission intentionally not declared (mic feature dormant; Families Policy consistency).

## [1.1] — 2026-06-17 · versionCode 3

### Added
- Parental gate (math challenge) on first launch and before Brain Arcade sign-in (Families Policy).

### Play submission
- Resubmitted after "Inaccurate Target Audience" rejection; target audience set to under-13, joined Designed for Families. Status: approved for closed testing.

## [1.0] — 2026-06-16 · versionCode 1–2

### Added
- Initial release: four learning activities, solo card games, online Brain Arcade (username-only kid account).
- versionCode 2: targetSdk 35 fix.
