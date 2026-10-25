# App UI design and motion reference

This is the persistent implementation memory for UI work in this repository. Read it before every UI/design/motion task, then inspect the current source because implementations can change. EchoPods is a GPL-licensed LibrePods derivative, but its public-facing visual identity must be distinct; upstream is a source and behavior reference, not a pixel-perfect branding template.

## Sources of truth

- Upstream reference: [LibrePods](https://github.com/librepods-org/librepods), currently compared against source revision `89356c0` during the Fold7 review.
- Shared components to reuse: `presentation/components/StyledButton.kt`, `StyledIconButton.kt`, `StyledList.kt`, `StyledListItem.kt`, `StyledToggle.kt`, `StyledSwitch.kt`, `StyledScaffold.kt`, and `presentation/overlays/IslandWindow.kt`. Compare their upstream counterparts for behavior and accessibility; do not copy upstream branding or make a new surface pixel-identical just for fidelity.
- Theme and design selection: `presentation/theme/Theme.kt`, `Type.kt`, `LocalDesignSystem.kt`, and `presentation/viewmodel/AppSettingsViewModel.kt`.
- Onboarding and update-guide structure: `presentation/screens/onboarding/OnboardingScreen.kt`, `presentation/screens/ReleaseNotesScreen.kt`, and `data/updates/Updates.kt`.
- Fold7 visual checks and comparison captures were made on Samsung SM-F966N. Captures are task artifacts and may not stay in `/tmp`; use the actual code plus this document as the durable reference.

## Design modes and color

- The app offers two clearly named appearances: **iOS** (Apple-inspired) and **Android** (Material 3 Expressive). The stored preference key is `m3e_enabled`; absent preference currently resolves to `false`, so first-time installs use the iOS-inspired appearance. Never overwrite an explicit user selection.
- In iOS-inspired light mode, the app background is approximately `#F2F2F7` and grouped surfaces are white. Dark mode uses a black background and `#1C1C1E` grouped surfaces. EchoPods uses its own sea-glass accent: light `#007D70` / pale `#CDEFE9`, dark `#8FE2D2` / `#244A47`. Use theme tokens rather than hardcoding these colors into each screen.
- Android mode uses the device's dynamic Material color scheme and Material 3 Expressive components.
- Typeface: use Android system sans-serif. The `sfProFamily` alias intentionally resolves to `FontFamily.SansSerif`; do not bundle Apple-owned SF Pro assets.
- Navigation and device behavior remain Android-native even when the visual mode is iOS-inspired. Do not promise an exact copy of an iOS system window.

## EchoPods identity and third-party separation

- Keep the app name, launcher mark, accent palette, listing images, and in-app attribution clearly EchoPods-branded. The launcher mark should make the earbud-and-case purpose recognizable at a glance while using original geometry and EchoPods colors. Do not use the LibrePods name/logo as product branding or imply an endorsement by LibrePods or Apple.
- The iOS-inspired mode may reuse familiar platform conventions (grouped rows, restrained materials, clear hierarchy), but screens, popups, widgets, artwork, and store screenshots must not be presented as Apple system UI or as pixel-perfect LibrePods copies.
- Use independently created or license-verified product artwork. Do not ship Apple system screenshots, extracted Apple UI assets, unlicensed fonts, or screenshots from other companion apps.
- Be accurate about source lineage: disclose the GPL-3.0-or-later LibrePods-derived Android code in the open-source/licensing area and make the source corresponding to each public build available. Do not remove upstream copyright/license notices from derived files.
- The EchoPods accent is a deliberate brand distinction layered onto the user's selected iOS-inspired appearance; do not switch users to Android/Material styling or change their saved design preference.

## Grouped settings rows

- Build settings using `StyledList`/`StyledListScope` and its `StyledListItem`, `StyledToggle`, and related extensions. Avoid hand-built rows when an equivalent shared component exists.
- In iOS mode, a group is one rounded surface with thin inset separators. For an n-row group: first row rounds only the top corners, middle rows are square, and the final row rounds only the bottom corners. A single-row group rounds all corners. Pressed/selected states must clip to the row shape without changing the resting group silhouette.
- Android mode uses the shared Material segmented-list behavior. Keep section titles, supporting text, row heights, and spacing consistent within a screen.
- When adding/removing rows, test first/middle/last/single-row shapes; do not give every row independent rounded corners.

## Buttons and touch feedback

- Prefer `StyledButton` and `StyledIconButton` so both design modes, backdrop treatment, enabled state, press response, and haptics remain consistent.
- iOS-inspired controls use the existing translucent/backdrop surface treatment, rounded capsule/card geometry, restrained highlight/scale response, and the component's existing haptic feedback. Keep button size and hit target accessible.
- Material mode should use the appropriate Material filled, tonal, outlined, or text style via `MaterialButtonStyle`; do not imitate iOS glass in Android mode.
- A custom interaction is justified only when no shared component fits. If one is added, document its mode-specific behavior and reduced-motion/accessibility behavior here.

## Connection popup and product art

- The selected popup style is user-configurable: bottom sheet or top island. Keep both options routed through the shared preference and `IslandWindow`/popup infrastructure rather than showing a second in-app copy of the same connection event.
- Preserve the distinction between the compact top-island state and its expanded/product-detail state. Keep content hierarchy, safe insets, and hit targets correct on Samsung foldable and narrow screens.
- Embedded island video must use a view-composited surface (`TextureView`) rather than `SurfaceView`/`VideoView`, so parent entrance and exit transforms apply to the artwork. Keep the static product-art fallback visible until a decoded video frame is ready, then crossfade; on decode failure, retain the fallback without a modal error.
- Low-battery alerts are independent per component and only use fresh measured samples. Queue component alerts rather than stacking windows, use notifications when the overlay cannot be shown, and clear the close-animation state if a new alert refreshes a fading window.
- Use existing, licensed product artwork and the project's layered artwork/animation assets. Do not substitute a random AirPods drawing, stretch a flat case image into a 3D motion, or use Apple system screenshots as app assets. If a model-specific asset is unavailable, show the supported neutral artwork rather than inventing a model-specific silhouette.
- Keep battery values, source/freshness, case state, and charging indicators tied to actual data; the visual design must not imply unavailable telemetry.

## Battery widget iconography

- Keep each battery component in its own ring; never merge left and right earbuds into one summary value.
- Use a compact single-earbud glyph inside each earbud ring and a distinct closed-case glyph inside the case ring. Keep the glyphs vector-based so they remain crisp at launcher widget sizes.
- Show a small green charging bolt centered at the 12-o'clock edge of a component ring only when that component has a fresh charging sample. Do not show a bolt based only on a remembered percentage.
- Preserve the widget's translucent adaptive background and keep its layout free of percentage labels; unknown values must not be represented as 0%.
- Render the battery widget's circles, icons, and adaptive glass surface together on a proportionally fitted Canvas bitmap. Keep the square grid's semantic order as host phone, right earbud, left earbud, case; map the Apple reference's laptop glyph to the Galaxy phone battery rather than implying that a Mac battery is being read.
- Use a red arc at 20% and below, a green arc above 20%, and a gray track with no progress for missing/invalid samples. Draw arcs clockwise from 12 o'clock with round caps; maintain four-across and four-down layouts only for wide and tall widget sizes.
- Android AppWidget cannot sample/blur wallpaper pixels as iOS does. Tint the translucent surface from `WallpaperColors` and preserve the same dark contrast in light/dark system modes; do not claim live wallpaper blur.
- Keep the 95/90/15-charging/85 visual fixture in the debug-only `BatteryWidgetDesignPreviewActivity`; never feed sample values to the live widget or release build.

## Onboarding and release notes

- First-run guide and version-specific What's New are separate data sets but share the same page visual language. They use `UpdateItem` records with a localized title, concise explanation, and a real feature screenshot (`screenshotRes`).
- The LibrePods release-note reference is a horizontally swipeable, single-advance carousel: centered title and underlined version link; one large rounded page card; a wavy outlined device/screenshot frame near the top; an overlapping organic Arch-shaped explanation panel near the bottom; and a final morphing arrow page to finish. Preserve this information hierarchy and gestures.
- Every feature page must show a real, legible screenshot—not a blank placeholder, unrelated app screen, or tiny strip. Choose/crop source images for the device-frame aspect ratio; use `ContentScale.Fit` when cropping would remove meaningful UI, and inspect the actual result on device. The current `guide_update.png` is square specifically to avoid its former wide-strip presentation.
- Keep guide copy localized, specific to the feature shown, and short enough to fit the Arch panel. First-run copy should explain useful actions, not repeat generic marketing. Version guide title and release link must resolve to the app's actual version/release.
- The final-page organic shape/arrow treatment is source-derived. Do not add unrelated infinite animations to feature pages. Keep the carousel usable with TalkBack, back navigation, reduced motion, and Fold cover/inner displays.
- Reference source: `android/app/src/main/java/me/kavishdevar/librepods/presentation/screens/ReleaseNotesScreen.kt` in LibrePods revision `89356c0`.

## Motion principles

- Reuse the motion already defined by a shared component instead of layering a second animation on top of it. Avoid repeated bounce, idle wiggle, or decorative motion that obscures state.
- Product opening/closing motion should communicate actual case/earbud state: lid changes first, then both visible earbuds move in a coordinated and symmetric way; battery labels appear only after the product motion settles. Maintain equal left/right scale and do not let layers intersect unnaturally.
- For numeric battery changes, animate only a newly received valid value; do not animate stale/unknown transitions as if they were measurements.
- Respect Android animator duration scale/reduced-motion settings. If the platform disables animations, use a short fade/crossfade or immediate state change.
- The top-island's spring/fling behavior and timing constants are Android implementation parameters, not measured Apple source values. Motion evidence and measurement fields live in `docs/MOTION_REVERSE_ENGINEERING.md`; do not describe inferred timings as Apple's official values.

## UI change checklist

Before marking a visual task complete:

- Check both iOS and Android appearance when the modified shared component supports both.
- Check light and dark themes, long Korean strings, system font scaling, Fold7 inner/cover widths, and status/navigation insets where relevant.
- Verify that screenshots/artwork are visible at full quality and correspond to the page text.
- Capture before/after screenshots on a device/emulator for major visual changes and inspect them rather than relying only on a successful build.
- Run relevant unit/UI tests and a build. Report device-specific limitations honestly; a screenshot check does not prove Bluetooth or media functionality.
