# Changelog

## [1.4] — 2026-11-03 · versionCode 7

### Added
- AI Workshops with parent login, powered by DeepSeek: Storybook Studio, Mini Maker Studio, Maths Detectives and Science Explorers.
- Parent Login: a parent PIN plus the parent's own DeepSeek API key, saved encrypted on the device. Locks when the app closes, after 30 minutes, or with "Lock now".
- Kid-safety prompts and a personal-info filter on every workshop message. Chats are never saved.
- Parents' Corner: AI Workshops status, Lock now, Replace key, Change PIN, Remove key & turn off AI.

### Changed
- Warm cream colours and website-style buttons, matching the AI Kids website.

### Privacy
- The offline activities still collect nothing. When a parent turns AI Workshops on, only the text a child types in a workshop is sent to DeepSeek. No developer key is included in the app.

## [1.3] — 2026-11-03 · versionCode 6

### Changed
- Sunny bookworm launcher icon and original colourful activity artwork, matching the AI Kids website.
- Sky-blue and lavender screens, playful borders and larger Fraunces / Nunito typography.
- Parent sign-in and owned child-profile selection for online games. Offline activities stay available without an account.
- AI art, Talking Buddy and writing your own story open the working family website after a grown-up check; parent sign-in is required there.
- Store bundle uses package sg.com.tertiarycourses.ai4kidsapp for the sales@tertiarycourses.com.sg developer account.
- Release bundles contain no embedded AI provider keys.

### Play submission
- Signed release bundle validated with bundletool and jarsigner; release build passed on 3 November 2026.
- Production submission pending sales-account authentication and final device/store checks. Not yet uploaded to Google Play.


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
- **Production** — versionCode 5 bundle (rebuild of 1.2, uploaded Aug 7) promoted from closed testing via "Add from library"; full rollout, all 177 countries/regions + rest of world. Submitted for review 2026-08-28 after production access was granted the same day.
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
