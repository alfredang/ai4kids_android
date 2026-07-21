---
name: android-launcher-icon
description: Generate and fix the adaptive Android launcher icon for Tertiary Scanner (Kotlin/Compose document scanner). Use when the app icon looks wrong on a device — e.g. a washed-out white tile on Samsung/One UI, the blue background missing, the document glyph too small/large/cropped — or when regenerating launcher + Play Store icon art across all densities.
---

# Android Launcher Icon

Generate and repair the adaptive launcher icon for **Tertiary Scanner**. Covers the
adaptive-icon foreground-sizing gotcha that makes the icon render differently across
launchers (the bug that makes it look like a plain white tile on Samsung One UI), plus the
end-to-end regenerate → preview → install → verify → ship-verified workflow.

> ⚠️ **The icon you ship is the one baked into the artifact, not the one in `app/src`.**
> Regenerating the source PNGs does **not** fix a release until you **rebuild** the AAB/APK.
> A pre-existing `app-release.aab` whose mtime predates the icon change carries the **old**
> icon — uploading it ships the bug even though the repo looks correct. This is exactly how
> the v1 white-tile shipped to Play (AAB built 6h before the foreground was resized). Always
> rebuild after touching icons, then **verify the icon inside the built artifact** (Step 5).

## When to Use This Skill

- The app icon looks wrong on a real device (white/washed-out tile, missing blue
  background, document glyph too small or cropped) even though it looks fine in Android Studio.
- Redesigning or recoloring the launcher icon.
- Regenerating the icon set across all densities, plus the Play Store 512/1024 art.
- A reviewer/user says "the icon doesn't seem correct."

## The core gotcha: adaptive-icon foreground sizing

The launcher icon is an **adaptive icon** ([app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml](app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml)):
a blue gradient **background** layer ([drawable/ic_launcher_background.xml](app/src/main/res/drawable/ic_launcher_background.xml))
+ a **foreground** PNG (the white document sheet with text lines and an accent scan bar).

The 108dp canvas is NOT all visible. Launchers crop to a mask and **scale the foreground
up to fill it** — and they disagree on how much:

- **Pixel / AOSP:** little or no upscale (~1.0×).
- **Samsung One UI:** aggressive upscale (~1.4×) to fill its squircle.

So a foreground whose content fills too much of the 108dp canvas gets blown up on
Samsung until the white document covers nearly all the blue background → the icon reads as
a **plain white tile**. This looks fine in the Studio preview (Pixel-style render) but
breaks on a physical Samsung phone.

**Rule of thumb:** keep the foreground's visible artwork to **~0.50 of the 108dp canvas**
(comfortably inside the safe zone). After any launcher's upscale a healthy blue frame
survives and it matches the self-contained legacy `ic_launcher.png`. The legacy/Play
square icons are self-contained (no launcher mask) so they use a larger **0.72** panel.

In [scripts/IconGen.java](scripts/IconGen.java) this is the one line that matters:

```java
float panel = withBg ? size*0.72f : size*0.50f;  // legacy=0.72, adaptive foreground=0.50
```

If the icon ever looks like a white tile again, that `0.50` is almost certainly the
knob — lower it for more blue frame, raise it for a bigger document.

## Workflow

All commands use Android Studio's bundled JBR — no system JDK needed:

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
SKILL=.claude/skills/android-launcher-icon
```

### 1. Regenerate the full icon set

Writes every density's `ic_launcher_foreground.png`, the legacy
`ic_launcher.png` / `ic_launcher_round.png`, and the Play art (512 + 1024):

```bash
cd "$SKILL/scripts"
"$JAVA_HOME/bin/javac" IconGen.java
"$JAVA_HOME/bin/java" IconGen \
  ../../../../app/src/main/res \
  ../../../../store-assets
```

(Adjust the relative paths to the repo `app/src/main/res` and `store-assets`.)

### 2. Preview what a launcher will actually render

`IconPreview` composites background + foreground, masks to a squircle, and renders at
**1.0× (AOSP)** and **1.4× (Samsung)** so you can catch the white-tile bug before
installing. Always check the **1.4×** output — that's the worst case.

```bash
"$JAVA_HOME/bin/javac" IconPreview.java
"$JAVA_HOME/bin/java" IconPreview ../../../../app/src/main/res/mipmap-xxxhdpi/ic_launcher_foreground.png
# wrote /tmp/preview_x1.0.png and /tmp/preview_x1.4.png — open and inspect both
```

A correct icon shows the white document sheet (text lines + blue scan bar) with a clear
blue frame on **both** scales.

### 3. Build, install, verify on a real device

```bash
export PATH=$PATH:~/Library/Android/sdk/platform-tools
./gradlew :app:assembleDebug

# Pick the physical device explicitly if several are attached (adb devices).
# A signature mismatch (release vs debug) needs an uninstall first — this CLEARS
# the app's local scans/library on that device, so confirm with the user before doing it.
adb -s <DEVICE_ID> uninstall com.tertiaryinfotech.scannerapp   # only if signatures differ
adb -s <DEVICE_ID> install -r app/build/outputs/apk/debug/app-debug.apk

# Launchers cache icons. Check the app-drawer icon (true current art); the old
# home-screen shortcut may stay cached until reboot / re-add.
adb -s <DEVICE_ID> shell input keyevent KEYCODE_HOME
adb -s <DEVICE_ID> shell input swipe 360 1400 360 300 300   # open app drawer
adb -s <DEVICE_ID> exec-out screencap -p > /tmp/drawer.png
```

### 4. Ship to Play

```bash
# ALWAYS rebuild after any icon change — never upload a pre-existing AAB.
# If you changed the icon, an older app-release.aab is STALE and ships the old icon.
./gradlew :app:bundleRelease   # signed AAB -> app/build/outputs/bundle/release/
```

- Bump `versionCode` in [app/build.gradle.kts](app/build.gradle.kts) first — Play rejects a
  duplicate `versionCode` (and an icon-only fix still needs a new `versionCode` to ship).
- The Play **store listing** icon (512×512) is a separate upload in the Console; refresh
  it from [store-assets/play_store_512.png](store-assets/play_store_512.png).

### 5. Verify the icon INSIDE the built artifact (do not skip)

The source assets passing Step 2 is **not** proof the upload is correct — only the artifact
is. Extract the foreground from the AAB you are about to upload and look at it. The build
re-encodes/optimizes PNGs, so an **md5 vs source will not match even when correct** — verify
**visually**, not by hash. Confirm a compact document panel with clear margins (the fixed
≈0.50 look), not an edge-to-edge sheet (the broken ≈0.72 look that becomes a white tile).

```bash
AAB=app/build/outputs/bundle/release/app-release.aab
rm -rf /tmp/aab_check && mkdir -p /tmp/aab_check && cd /tmp/aab_check
unzip -o -q "$OLDPWD/$AAB" "base/res/mipmap-xxxhdpi*/ic_launcher_foreground.png"
# open base/res/mipmap-xxxhdpi-v4/ic_launcher_foreground.png and eyeball it
```

Sanity-check the artifact is fresh, too — its mtime must be **newer** than the icon files:

```bash
ls -la "$AAB" app/src/main/res/mipmap-xxxhdpi/ic_launcher_foreground.png   # AAB must be newer
```

Only upload once the extracted foreground matches the fixed art **and** the AAB is newer than
the source icons.

## Icon design reference (current art)

Defined in [scripts/IconGen.java](scripts/IconGen.java):

- Background: linear gradient `#4C8DF6` (top-left) → `#1B4FB0` (bottom-right) + a soft
  top-left radial sheen. Mirrored in [drawable/ic_launcher_background.xml](app/src/main/res/drawable/ic_launcher_background.xml).
- Foreground: a white rounded **document sheet** with a drop shadow, four light grey text
  lines (`#C8D4E8`, the last one short), and a brand-accent **scan bar** (`#2E7CF6`) near the
  bottom that slightly overhangs the sheet — evoking a page being scanned.
- The accent `#2E7CF6` matches the app's Compose accent (`AccentLight`) and the Scan button.
- No `<monochrome>` layer is shipped; if you later want Android 13+ themed icons, hand-author
  a single-color document glyph vector and add a `<monochrome>` entry to the adaptive icon
  (don't generate it via IconGen).
