---
name: play-store-submission
description: End-to-end submission of an Android app to the Google Play Console — create the app, complete every App content declaration, build the store listing, upload the signed AAB to a Closed testing track, and send it for review (so "Update status" becomes "In review"). Optionally publish an Internal testing release for instant install. Use when publishing/updating a native Android app (Kotlin/Compose) to Google Play. Drives the Console web UI with the Playwright MCP browser (the Play Developer API can't create apps or fill the policy forms on first publish).
---

# Google Play Store Submission

This skill captures the **exact, working** Play Console flow used to submit **Tertiary Scanner**
(`com.tertiaryinfotech.scannerapp`) to **Closed testing** — from app creation to **"Update status:
In review"** — plus the hard-won gotchas that block first-time submissions. It also publishes an
**Internal testing** release (instant, no review) so the app is installable immediately.

Almost everything here is **web-UI only** and must be driven in the browser (Playwright MCP). The
Play Developer API cannot create the app or complete the App-content / Data-safety / content-rating
forms on first publish.

## Fixed values for this developer account

- **Developer account:** Tertiary Infotech Academy — account ID `4722227264647952214` (personal)
- **Privacy Policy URL:** `https://www.tertiaryinfotech.com/privacy`  ← always use this (same as the
  Sudoku app). Do NOT host a separate GitHub Pages privacy page.
- **Category:** Productivity (for utility/tool apps)
- **Contact email:** `angch@tertiaryinfotech.com` · **Website:** `www.tertiaryinfotech.com`
- **Testing emails (closed + internal):** `angch@tertiaryinfotech.com`, `angchewhoe@gmail.com`
  (the existing **`tester 01`** email list already contains `angch@…`; add `angchewhoe@gmail.com`
  to it — see Testers below).
- **Data collection:** none. **Ads:** none. **Content rating:** Everyone. **Target age:** 18+.

## Prerequisites (build these first)

1. **Signed release AAB** with **Play App Signing** (Google manages the app signing key; your
   keystore is the *upload* key):
   ```bash
   export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
   ./gradlew :app:bundleRelease   # -> app/build/outputs/bundle/release/app-release.aab
   ```
   The upload keystore lives **outside** the repo; signing creds are in the gitignored
   `keystore.properties`. **Back the keystore up** — losing it blocks future updates.
2. **Graphics** (Play rejects wrong sizes — see Gotchas):
   - App icon **512×512** PNG (≤1 MB)
   - Feature graphic **1024×500** PNG/JPEG
   - **2–8 phone screenshots**, **9:16 ratio, 1080×1920** (NOT raw 1080×2400 device caps — too tall)
3. **Listing copy:** app name (≤30), short description (**≤80 chars** — watch the em-dash!), full
   description (≤4000).

### Playwright upload caveat
The Playwright MCP file chooser only reads files under its allowed roots. **Copy all upload files
into the allowed root first**, then upload from there:
```bash
cp app/build/outputs/bundle/release/app-release.aab store-assets/play_store_512.png \
   store-assets/feature_graphic_1024x500.png store-assets/screenshots/*.png \
   <PLAYWRIGHT_ALLOWED_ROOT>/upload/
```

## The flow (Console web UI)

> Deep-linking to `/app-content/*` often bounces to the app list. Navigate by **clicking the
> dashboard task** or the left-nav, not by pasting sub-page URLs. Many "task" rows are
> `div[role="button"]` (not `<button>`), so `querySelector('button')` misses them — match on
> `[role="button"]` by text. Sticky action bars **intercept** Playwright clicks; fall back to a JS
> `element.click()` (e.g. via `debug-id` attributes like `upload-button`,
> `add-to-content-button`, `send-for-review-button`).

### 1. Create the app
All apps → **Create app**. App name; **App** (not Game); **Free**; default language en-US; tick
both declarations (Developer Program Policies + US export laws); leave automatic protection on →
**Create app**. Note the new app ID in the URL.

### 2. App content (Dashboard → "Provide app information…" → 11 tasks)
Complete each; answers for an offline, no-data utility:

| Task | Answer |
|---|---|
| **Privacy policy** | `https://www.tertiaryinfotech.com/privacy` |
| **Sign in details** (App access) | **No** part of the app is restricted |
| **Ads** | **No**, does not contain ads |
| **Content rating** | Start questionnaire → email `angch@tertiaryinfotech.com`, category **All other app types**, agree IARC terms → answer **No** to every question (downloaded content, user-sharing, online content, age-restricted, location/purchases/crypto/browser/news) → **Save**. Result: **Everyone / PEGI 3**. |
| **Target audience** | Tick **18 and over** only (skips the children steps) → Save |
| **Data safety** | "Does your app collect or share any data?" → **No** → Preview shows *No data collected / No data shared* → Save |
| **Government apps** | **No** |
| **Financial features** | "My app doesn't provide any financial features" → Next → Save |
| **Health** | "My app does not have any health features" → Next → Save |
| **App category & contact** (Store settings) | Category **Productivity**; contact email + website |
| **Store listing** | see step 3 |

### 3. Store listing (Store presence → Store listings → Create default store listing)
- App name, **short description (≤80)**, full description.
- **App icon** → Add assets → **Upload** (JS-click `button[debug-id="upload-button"]`) → choose the
  512 PNG → it auto-selects in the asset library → JS-click `button[debug-id="add-to-content-button"]`.
- **Feature graphic** → same flow with the 1024×500.
- **Phone screenshots** → same flow; select all 4 → Add.
- **Save** (button enables once required fields + assets are present; disabled = saved).

### 4. Closed testing release (Test and release → Testing → Closed testing → Manage track)
1. **Create new release** → **Upload** the AAB (or "Add from library" if already uploaded). Wait
   ~30–60 s for processing; the artifact row shows `1 (1.0) · 24+ · target SDK 36`. App integrity
   shows *Automatic protection on* + *Releases signed by Google Play* (Play App Signing).
2. Release name auto-fills (`1 (1.0)`); add **release notes** inside the `<en-US>…</en-US>` tags.
   **Next** → review → **Save** (saves the release as a draft; the actual rollout for a *first* app
   happens via Publishing overview, step 6).
3. **Countries / regions** tab → Add countries → **Select all rows** → Save.
4. **Testers** tab → tick the **`tester 01`** email list (and others as needed) → set a feedback
   email → **Save**. See Testers below to add `angchewhoe@gmail.com`.

### 5. Advertising ID declaration (the #1 first-submit blocker)
Apps targeting API 33+ must declare advertising-ID usage, **and ML Kit / Play Services pull in the
`com.google.android.gms.permission.AD_ID` permission transitively** — so Play flags
*"Incomplete advertising ID declaration"* and disables **Send for review**.
Fix: Publishing overview → the issue card → **Complete declaration** →
`app-content/ad-id-declaration` → **"Does your app use advertising ID?" → No** → Save.

### 6. Send for review → "Update status: In review"
**Publishing overview** → **Send N changes for review** → confirm the *"Send N changes for
review?"* dialog → **Send changes for review**. (Managed publishing is **off** = auto-publish on
approval.) The panel flips to **"Changes in review"** and the app list shows **Update status: In
review**. Quick checks run (~minutes), then human review (typically up to 7 days).

### 7. (Optional) Internal testing — installable immediately, no review
Internal testing publishes instantly (no review) and makes the app match a "Draft / Internal
testing" state:
Test and release → Testing → **Internal testing** → **Create new release** → **Add from library**
→ select bundle `1 (1.0)` → **Add to release** → release notes → Next → **Save and publish** →
confirm *"Publish change on Google Play?"*. Release shows **"Available to internal testers"**.
Then **Testers** tab → select `tester 01` → Save.
**Opt-in link:** `https://play.google.com/apps/internaltest/<trackId>` (only works for emails on a
selected list).

> Until the closed-testing review completes, testers see a **temporary name**
> `com.<pkg> (unreviewed)` and a generic icon. The real name/icon (and the app-list icon + "Draft"
> flipping) appear **after Google approves** the listing — this is normal, not a bug.

## Testers — adding an email (important gotcha)
Selecting/creating tester lists has a **two-step save**: the list dialog's **"Save changes"**
opens a second **"Save changes to email list?"** confirmation that you MUST also confirm, or
nothing persists (the inline "Create email list" silently fails the same way).
Reliable path to add `angchewhoe@gmail.com`:
1. Testers tab → on the **`tester 01`** row click **Edit email list**.
2. Type the email in **Add email addresses** → press **Enter** (it becomes a chip in the table).
3. **Save changes** → then confirm **"Save changes to email list?"** → the row count increments
   (12 → 13). `angch@tertiaryinfotech.com` is already in `tester 01`.

## Production access (later)
This is a **personal** account, so Production requires a **closed test with ≥12 testers opted-in
for 14 days** first. Testers must actually open the opt-in link, accept, and keep the app
installed. After 14 days, Dashboard → Production → **Apply for production** unlocks.

## Gotchas checklist
- **Advertising ID** declaration is required (ML Kit adds the AD_ID permission) → declare **No**.
- **Screenshots** must be ≤2:1; raw phone caps (1080×2400 = 2.22:1) are **rejected** — letterbox to
  **1080×1920**.
- **Short description ≤80 chars** (an `—` em-dash counts as 1; it's easy to hit 81).
- **Email-list saves need the secondary confirm dialog** (see Testers).
- **Playwright:** copy upload files into the allowed root; JS-click sticky-bar-intercepted buttons
  via `debug-id`; match task rows as `[role="button"]`; don't deep-link `/app-content/*`.
- **Managed publishing off** = auto-publish on approval (recommended for testing).
- **Internal testing ≠ review**; only Closed/Open/Production submissions create "In review".
