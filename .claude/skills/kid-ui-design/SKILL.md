---
name: kid-ui-design
description: Project-locked UI design skill for the AI4Kids Android app. Enforces ONE bright, playful, chunky kid aesthetic across every Compose screen AND the LibGDX Escape Room game — the app's saturated brand palette, big rounded corners, soft shadows, large tap targets, emoji/pictogram icons, and gentle spring/scale motion — built on the shared Theme + SharedUI components (Compose) and the SmoothDraw/RichText toolkit (LibGDX), kept in parity with the iOS app. Use whenever building or refining any Compose screen, activity, dialog, shared component, or the Escape Room game rendering.
---

# AI4Kids Android — Bright & Friendly Aesthetic

This skill locks ONE design direction for everything a child sees: the home grid and all four activities (Phonics Quest, Story Builder, Code Puzzles, Escape Room), plus the online Brain Arcade and co-op screens — AND the LibGDX-rendered Escape Room game itself. Think **warm, rounded, chunky, joyful** — a kid should feel like they're playing, not using software. Do not invent new palettes, shapes, or motion; reuse the existing tokens and components.

> Two render surfaces, ONE aesthetic. Almost everything is **Jetpack Compose + Material 3** (§ Compose below). The **LibGDX Escape Room game** (`gdx/`, launched in its own `EscapeActivity` surface) draws with OpenGL, not Compose — but it must still look like the same app (§ LibGDX below). Keep parity with the iOS source where practical (each file notes its iOS counterpart).

# Part A — Compose (home grid, activities, dialogs, online screens)

## Design tokens (LOCK these — do not hardcode colors)

Everything comes from [ui/theme/Theme.kt](../../../app/src/main/java/sg/com/tertiarycourses/ai4kids/ui/theme/Theme.kt). Reference `Theme.*` and `MaterialTheme.colorScheme.*`; never paste raw `Color(...)` hex into a screen when a token exists.

```
Theme.Purple   0.45,0.30,0.92   // PRIMARY action / brand pop (colorScheme.primary)
Theme.Pink     0.98,0.35,0.62   // secondary (colorScheme.secondary)
Theme.Orange   1.00,0.58,0.20
Theme.Yellow   1.00,0.80,0.16   // stars / highlights
Theme.Green    0.20,0.78,0.45   // success / correct / "go"
Theme.Blue     0.20,0.62,0.98   // info / secondary action
Theme.Teal     0.12,0.78,0.78
Theme.Red      0.96,0.34,0.34   // gentle error only
Theme.Ink      0.16,0.14,0.30   // headings & body text (onBackground / onSurface)
```

- **Backgrounds:** the app-wide warm gradient is `Theme.Background` (lilac → cream). Draw content cards on **white** (`Color.White` / `colorScheme.surface`) over it.
- **Text:** `Theme.Ink` for headings and body on white/light surfaces; `Color.White` for text on a saturated accent fill (buttons, celebration card).
- **Accents** are used as solid fills on buttons/tiles and as soft tints elsewhere — pick ONE accent per feature and stay consistent.

## Shape & elevation

From `Theme` + [ui/components/SharedUI.kt](../../../app/src/main/java/sg/com/tertiarycourses/ai4kids/ui/components/SharedUI.kt):

- **Corners:** `Theme.CardShape` (28dp) for cards/panels, `Theme.BigShape` (36dp) for big hero/celebration surfaces, `CircleShape` for badges/close buttons, `RoundedCornerShape(22.dp)` for buttons. No sharp corners.
- **Shadow:** use `Modifier.softShadow(shape)` — never a raw `shadow()` with ad-hoc values. It applies the standard soft, friendly drop shadow.
- **The white card:** use `Modifier.kidCard()` to get the standard shadow + clip + white fill in one call. Build most content on it.
- **Heading-over-color:** apply `Theme.SoftTextShadow` to text drawn on a colored surface for legibility.

## Typography

- Headings/titles/buttons: **ExtraBold** (`FontWeight.ExtraBold`), large (titles 28–36sp, button labels ~22sp). Big and friendly.
- Body/support: medium weight, comfortable size (≥16sp — kids need large text).
- Numbers/scores: ExtraBold (see `StarBadge`).
- The app uses the Material 3 default type ramp via `AI4KidsTheme`; keep sizes generous and weights heavy. If a custom rounded display font is added later, wire it through `AI4KidsTheme`'s `Typography`, not per-screen.

## Reuse the shared components — don't re-roll them

These already encode the aesthetic. Prefer them over bespoke UI:

- **`KidButton(title, icon?, color, onClick)`** — THE chunky primary button. Spring scale-on-press, soft shadow, 56dp min height, white ExtraBold label. Pass a `Theme.*` color; default is `Theme.Purple`.
- **`StarBadge(count)`** — the star-tally pill (drives from `ProgressStore`). Use it wherever stars are shown.
- **`CloseButton(onClick)`** — the standard round back-to-home button; 56dp tap area around a 44dp circle. Every full-screen activity uses it.
- **`CelebrationView(message, onDismiss)`** — the confetti + purple card shown on a win. Trigger it after awarding stars.
- **`Modifier.kidCard()` / `Modifier.softShadow(shape)`** — the card & shadow primitives above.
- **`EmojiBadge(emoji, size)`** — emoji as imagery.

If you need something new, build it FROM these tokens (softShadow + Theme shapes + Theme colors) so it matches.

## Required visual language

1. **Big rounded corners** everywhere (`Theme.CardShape`/`BigShape`/`CircleShape`).
2. **Soft shadows** via `softShadow` — never hard borders.
3. **Emoji as icons** are ENCOURAGED (🦦 🚀 ⭐ 🎉 🔢 🃏 🧠). Put a large emoji in a tinted rounded tile for section/activity headers.
4. **One accent per feature** — reuse the `Activity` model's color (each activity in [model/Activity.kt](../../../app/src/main/java/sg/com/tertiarycourses/ai4kids/model/Activity.kt) owns its title/color/age band/icon — the single source of truth). Don't pick a new color for an existing activity.
5. **Generous spacing** — roomy padding, cards that breathe.

## Motion

- **Press feedback:** spring scale-down (`KidButton` does 0.94f via `spring(stiffness = Spring.StiffnessMedium)`). Reuse that pattern for other tappables.
- **Entrance/delight:** spring scale-in for cards (see `CelebrationView`'s `StiffnessMediumLow`), gentle confetti on a win.
- **Tone:** bouncy and playful is GOOD. Keep it readable — no fast strobing, no nauseating loops.

## Interaction & accessibility (kids)

- **Tap targets ≥ 48dp** (buttons default to 56dp; `CloseButton` gives a 56dp hit area). Never ship a small tappable.
- **High contrast:** `Theme.Ink` on white/light for anything a child reads; white on saturated accents. Don't put light text on light tints.
- **Kind copy:** encouraging, never harsh ("So close! Try again 🙂"). Use `Theme.Red`/coral sparingly and gently for nudges, `Theme.Green` for success.
- **Award stars through `ProgressStore.award(count, activity)`** so totals persist and the `StarBadge` updates.

## What NOT to do (bans)

- ❌ Raw hex `Color(0x...)` / `shadow()` with custom values when a `Theme.*` token or `softShadow`/`kidCard` exists.
- ❌ Sharp corners, hard borders, tiny dense controls, cramped spacing.
- ❌ Cold/clinical copy or scary red error states.
- ❌ Off-palette colors outside the `Theme` set, or giving an existing activity a color other than its `Activity.color`.
- ❌ Small text / small tap targets — this is a kids' app.
- ❌ Re-implementing `KidButton`/`StarBadge`/`CloseButton`/`CelebrationView` inline instead of reusing them.

## When invoked (Compose)

When asked to build or refresh a Compose screen/component:

1. Re-read the tokens above; build content on `Modifier.kidCard()` over `Theme.Background`.
2. Pick ONE accent (reuse the `Activity.color` for activity screens) for buttons/tiles/highlights.
3. Reuse `KidButton`, `StarBadge`, `CloseButton`, `CelebrationView` and the `kidCard`/`softShadow` modifiers.
4. ExtraBold generous headings in `Theme.Ink`; large body; add an emoji or two for warmth.
5. Round everything, add `softShadow`, and a spring scale on interactive elements.
6. Verify tap targets ≥ 48dp and that text stays large and legible; check a small phone (≈360dp wide) — cards stack cleanly, nothing overflows.

Good references already in the repo: the home grid in [ui/RootScreen.kt](../../../app/src/main/java/sg/com/tertiarycourses/ai4kids/ui/RootScreen.kt), the activity screens under [ui/activities/](../../../app/src/main/java/sg/com/tertiarycourses/ai4kids/ui/activities/), and the shared components in [ui/components/SharedUI.kt](../../../app/src/main/java/sg/com/tertiarycourses/ai4kids/ui/components/SharedUI.kt).

---

# Part B — LibGDX Escape Room game

The Escape Room renders in OpenGL through [gdx/EscapeGdxGame.kt](../../../app/src/main/java/sg/com/tertiarycourses/ai4kids/gdx/EscapeGdxGame.kt), hosted in [gdx/EscapeActivity.kt](../../../app/src/main/java/sg/com/tertiarycourses/ai4kids/gdx/EscapeActivity.kt). It's a **different render pipeline, but the SAME look**: bright, rounded, chunky, joyful — a kid should not feel they left the app when the game opens. It also honours the app's **"no third-party SDKs"** privacy stance, so it uses its own tiny drawing toolkit instead of pulling in ShapeDrawer / TextraTypist.

## Match the palette (mirror `Theme`, in LibGDX Color)

Compose's `Theme.*` colors are `androidx…Color` (sRGB); the game needs `com.badlogic.gdx.graphics.Color` (linear 0–1 floats). The game **already mirrors the brand palette** — reuse those fields rather than inventing hues:

- `cInk = Color(0.16f, 0.14f, 0.30f, 1f)` ≡ `Theme.Ink` — text/outlines.
- `cGood = Color(0.20f, 0.78f, 0.45f, 1f)` ≡ `Theme.Green` — success / "solved".
- Confetti + glyph colors are the same warm set as `CelebrationView` (yellow, coral/pink, sky-blue, green, orange, purple).
- Gentle-error red `Color(0.96f, 0.34f, 0.34f, 1f)` ≡ `Theme.Red` — the "Try again!" flash.

Rule: any new color in the game must correspond to a `Theme.*` token converted to a GDX `Color`. If you're reaching for a hue that isn't in the palette, stop — pick the nearest brand color. Keep the near-black/desaturated tones scoped to *diegetic props* (an LCD bezel, a powered-down panel), never as chrome a child taps.

## Reuse the drawing toolkit — don't add a library

[gdx/GdxToolkit.kt](../../../app/src/main/java/sg/com/tertiarycourses/ai4kids/gdx/GdxToolkit.kt) is the game's aesthetic in code. Use it; do not re-roll shapes or add ShapeRenderer flushes:

- **`SmoothDraw`** — anti-aliased `circle`, round-capped `line`, `rect`, and `triangle`, all drawn through the existing `Batch` so shapes batch with text/sprites. This is how the game gets **soft, rounded edges** that echo the Compose `softShadow`/rounded-corner language. Prefer circles, rounded pills, and round-capped strokes over hard rectangles for anything interactive.
- **`RichText`** — typewriter reveal + inline `[#rrggbb]…[]` color markup + `:gN:` pictogram icons. Use it for narration/prompts so game text has the same warm, encouraging voice. Pictograms (via the `icon` lambda) are the game's emoji stand-in — the "emoji as icons" rule, GL-side.

## Game-side conventions

- **Chunky, tappable targets:** interactive cells/keys/buttons must be large and well-spaced — this is a touch game for small hands. Give the same generous hit areas as `KidButton`/`CloseButton` (≥48dp equivalent in world units after the viewport scale).
- **Round everything drawable:** use `SmoothDraw.circle`/`line`/`triangle` and rounded pills; reserve `rect` for props and backgrounds.
- **Kind feedback:** wrong answers get a brief, gentle red flash + "Try again!" / "Not quite!" — never a harsh buzzer or scary state. Wins get confetti (mirroring `CelebrationView`).
- **Award stars through the app, not the game loop:** when the room is solved, route the reward through `ProgressStore.award(count, activity)` (via the activity/bridge) so the star tally and `StarBadge` stay consistent with every other activity.
- **Readable text:** large `BitmapFont` scale, `cInk` on light surfaces / bright glyphs on dark diegetic screens; keep contrast high.
- **Stay asset-free & SDK-free:** shapes/pictograms are generated at runtime (see the `Pixmap` builders in `GdxToolkit`). Do not introduce a sprite-atlas dependency, a font/rich-text library, or any third-party SDK — it would break the privacy posture in [CLAUDE.md](../../../CLAUDE.md).

## When invoked (LibGDX)

1. Reuse the palette fields already defined in `EscapeGdxGame` (or add a new one only by converting a `Theme.*` token to a GDX `Color`).
2. Draw with `SmoothDraw` (rounded, anti-aliased) and write text via `RichText` — never add a shape/text library.
3. Keep targets big and spaced; keep feedback kind (gentle red flash, confetti on win).
4. Route star rewards through `ProgressStore.award(...)`.
5. Verify it reads as the same app: bright, rounded, high-contrast, playful — open it right after a Compose activity and confirm the visual jump is minimal.
