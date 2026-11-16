# Motion reverse engineering log

이 문서는 iPhone 영상이나 실기기 비교 결과를 기록하기 위한 템플릿이다. Apple 내부 spring/duration 값으로 해석하지 않는다.

## Capture fields

- fps:
- source / device:
- scene: FIRST_PAIRING / KNOWN_DEVICE_STATUS
- popup_first_visible_frame:
- popup_settle_frame:
- product_first_visible_frame:
- product_settle_frame:
- battery_first_visible_frame:
- battery_settle_frame:
- status_change_frame:
- exit_start_frame:
- exit_end_frame:

좌표는 popup 높이에 대해 정규화한다.

```text
normalizedY = (currentY - finalY) / popupHeight
normalizedScale = currentScale / finalScale
progress = frame / totalFrames
```

## Verified implementation baseline

아래 값은 현재 프로젝트 코드에서 읽은 구현값이지 Apple 내부 애니메이션 값이나 iPhone 영상 측정 결과가 아니다. 코드를 바꾸면 이 표도 함께 갱신한다.

| Surface | Current behavior in source | Source |
|---|---|---|
| Top island entrance | Container fades from 0→1, scales 0.96→1.0, and moves from -20dp to rest over 360 ms with `DecelerateInterpolator(1.5)`. Decorative video playback starts only after both the entrance and video preparation complete, avoiding concurrent entrance animation and video decoding. This is an Android performance choice, not an Apple-measured timing. | `presentation/overlays/IslandWindow.kt` (`showIsland`) |
| iOS-style `StyledButton` / `StyledIconButton` | Press/drag highlight progress uses `spring(dampingRatio=0.5, stiffness=300, visibilityThreshold=0.001)`. Drag offset returns with `spring(dampingRatio=1, stiffness=300)`. Interactive highlight follows the press point; API 33+ uses a radial RuntimeShader, with a white-overlay fallback. | `presentation/components/StyledButton.kt`, `StyledIconButton.kt` |
| Button touch feedback | Click uses `ContextClick`; drag start/movement uses segment tick feedback; release/cancel uses reject feedback. | Same files |
| Top island expand | Velocity-scaled duration: nominal 600 ms, clamped to 300–700 ms. Container decelerates while custom stretch uses `OvershootInterpolator(0.5)`, then normalizes over 300 ms. | `presentation/overlays/IslandWindow.kt` (`animateExpandWithStretch`) |
| Top island dismiss fling | Nominal 400 ms, velocity-scaled and clamped to 200–500 ms; translate/fade/scale out with `DecelerateInterpolator(1.2)`. | `presentation/overlays/IslandWindow.kt` (`animateDismissWithInertia`) |
| App screen push/pop | Both appearance modes use a restrained paired horizontal push: new page enters from one twenty-fourth-width while the previous page shifts one forty-eighth-width; pop reverses the pair. The 240 ms `FastOutSlowInEasing` motion uses no page alpha blend so route text does not ghost through the incoming screen. Predictive back uses the same short edge-aware offsets. The shorter travel avoids full-width content overlap during route changes on foldable displays. Settings closes any live island-size preview before navigating away so a system overlay cannot linger over the destination. | `presentation/navigation/AppNavGraph.kt`, `presentation/screens/AppSettingsScreen.kt` |
| iOS `StyledSlider` drag | Uses a 44dp-minimum touch area around the track and retains the finger's continuous drag fraction while the displayed preference snaps to defined stops. The app setting and live overlay preview update from the same drag callback; this avoids resetting the thumb to a quantized value on every pointer event. | `presentation/components/StyledSlider.kt`, `presentation/screens/AppSettingsScreen.kt` |
| iOS-style top bar controls | Back control enters with a 0.92 scale and fades over 170–200 ms; actions move 10% horizontally and fade over 170–220 ms. Title crossfade is 160 ms. These shorter transitions keep the fixed scaffold aligned with the moving page. | `presentation/components/StyledScaffold.kt` |
| Release-guide finish CTA | Android appearance retains the `Cookie7Sided` → `SoftBurst` morph and 8° press tilt with a medium-bouncy/medium-stiffness spring; tapping advances to the app. iOS appearance uses the shared `liquidGlassSliderThumb` backdrop/lens renderer and `rememberMomentumAnimation` from `StyledSlider`, inside a 56dp circular thumb on a 68dp translucent track. During the drag, the thumb follows the finger, scales up to 1.5, responds to velocity, fades its milky surface into the sampled backdrop, and refracts the label below it through a 6dp lens. Chromatic aberration is disabled to avoid colored edge fringing. The threshold is 90%; an incomplete release returns with a 0.84 damping / 380 stiffness spring, and successful completion settles for 180ms before advancing. Segment/context haptics mark threshold and completion. The final page is rendered outside the pager subtree to avoid Fold4 RenderNode recursion; reduced-motion follows Android's animator-duration setting. These are EchoPods implementation values, not Apple measurements. | `presentation/screens/ReleaseNotesScreen.kt` (`MaterialFinishPage`, `IosFinishPage`, `LiquidGlassSlideToContinue`), `presentation/components/StyledSlider.kt` (`liquidGlassSliderThumb`, `rememberMomentumAnimation`) |

No frame-by-frame Apple popup motion measurements are currently recorded. Before describing any timing as Apple-like, capture the Apple reference and fill the capture fields above; otherwise call the values Android implementation parameters. New motion should follow `docs/APP_DESIGN_SYSTEM.md`, and reduced-motion behavior must be checked on device before sign-off.
