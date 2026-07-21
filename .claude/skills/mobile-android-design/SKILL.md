---
name: mobile-android-design
description: Master Material Design 3 and Jetpack Compose patterns for building native Android apps. Use when designing Android interfaces, implementing Compose UI, or following Google's Material Design guidelines.
---

# Android Mobile Design

Master Material Design 3 (Material You) and Jetpack Compose to build modern, adaptive Android applications that integrate seamlessly with the Android ecosystem.

## When to Use This Skill

- Designing Android app interfaces following Material Design 3
- Building Jetpack Compose UI and layouts
- Implementing Android navigation patterns (Navigation Compose)
- Creating adaptive layouts for phones, tablets, and foldables
- Using Material 3 theming with dynamic colors
- Building accessible Android interfaces
- Implementing Android-specific gestures and interactions
- Designing for different screen configurations

## Detailed section: Core Concepts

Originally a 9201-byte section in this SKILL.md. Moved to `references/details.md` to fit Codex's 8 KB skill body cap.

## Quick Start Component

```kotlin
@Composable
fun ItemListCard(
    item: Item,
    onItemClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onItemClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Star,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = item.subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
```

## This Project: Tertiary Scanner (Android)

This skill is installed in the **Tertiary Scanner** app — a native, fully offline
document scanner (`com.tertiaryinfotech.scannerapp`) and a faithful port of the
SwiftUI iOS edition. Apply the guidance below to *this* codebase; where the generic
best practices below conflict with these project decisions, **the project decisions win**.

**Architecture (MVVM + Navigation Compose):** the source of truth lives in three
`ViewModel`s — `ScannerViewModel` (the capture→edit→save flow), `LibraryViewModel`, and
`SettingsViewModel` — each exposing `StateFlow` collected with
`collectAsStateWithLifecycle()`. Dependencies are wired by a manual `AppContainer`
(`ScannerApplication.kt`: Room db, `StorageService`, `SettingsStore`); ViewModels are
created via `viewModel(factory = …Factory)`. Navigation is a single `NavHost` in
`ui/Navigation.kt` (`AppNav` + `Routes`). The editor screens (Preview → Filter → Export)
**share one Activity-scoped `ScannerViewModel`** — `viewModel(viewModelStoreOwner = activity)` —
so the in-flight `WorkingDocument` survives across the back stack. Screens live in `ui/`:
`HomeScreen`, `PreviewScreen`, `FilterScreen`, `ExportScreen`, `LibraryScreen`,
`DocumentDetailScreen`, `SettingsScreen`, plus `ui/components/`.

**Theming (`ui/theme/Theme.kt`):** wrap everything in `TertiaryScannerTheme { }` and read
colors via `MaterialTheme.colorScheme`. This app deliberately uses a **fixed iOS-matched
palette** — accent `#2E7CF6` light / `#4A94FF` dark — to keep visual parity with iOS.
**Do NOT introduce Material You dynamic color.** Dark mode follows the system; the status
bar is tinted to the accent in `TertiaryScannerTheme`.

**Layout scope:** phone-only and **portrait-only**
(`android:screenOrientation="portrait"`). **Skip** `WindowSizeClass`, tablet, and foldable
adaptive layouts — out of scope here.

**Components:** the `ItemListCard` Quick Start below mirrors the real `DocumentRow`
(`ui/components/Components.kt`) — a 52×68 cover thumbnail (Coil `AsyncImage` over a `File`),
a title + created-date + page-count column, used in Home's Recent list and the Library.
Other shared pieces: `FilterThumbnail` (live filter preview tile) and `SectionCard`
(grouped settings/export card). Page viewers use `HorizontalPager`; bitmaps render via
`bitmap.asImageBitmap()`.

**Constraints:** dependencies are AndroidX/Compose + Room + DataStore + Coil + ML Kit
(`play-services-mlkit-document-scanner`, `text-recognition`). **No analytics/network for
data** — the app is 100% offline. Heavy work (image filtering, OCR, PDF) runs on
`Dispatchers.Default`/`IO`; keep `imaging/ImageProcessor.kt` and the `service/` layer free
of Compose imports. Persistence is split: document/page **metadata in Room**, page
**images/thumbnails as JPEG files** under `filesDir/Scans/<documentId>/` (filenames only in
the DB). Capture uses the ML Kit Document Scanner with a system Photo Picker fallback
(`ui/Capture.kt`).

## Best Practices

1. **Use Material Theme**: Access colors via `MaterialTheme.colorScheme` for automatic dark mode support
2. **Support Dynamic Color**: Enable dynamic color on Android 12+ for personalization
3. **Adaptive Layouts**: Use `WindowSizeClass` for responsive designs
4. **Content Descriptions**: Add `contentDescription` to all interactive elements
5. **Touch Targets**: Minimum 48dp touch targets for accessibility
6. **State Hoisting**: Hoist state to make components reusable and testable
7. **Remember Properly**: Use `remember` and `rememberSaveable` appropriately
8. **Preview Annotations**: Add `@Preview` with different configurations

## Common Issues

- **Recomposition Issues**: Avoid passing unstable lambdas; use `remember`
- **State Loss**: Use `rememberSaveable` for configuration changes
- **Performance**: Use `LazyColumn` instead of `Column` for long lists
- **Theme Leaks**: Ensure `MaterialTheme` wraps all composables
- **Navigation Crashes**: Handle back press and deep links properly
- **Memory Leaks**: Cancel coroutines in `DisposableEffect`
