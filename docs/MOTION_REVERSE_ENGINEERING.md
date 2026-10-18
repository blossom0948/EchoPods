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
| Release-guide finish CTA | `Cookie7Sided` → `SoftBurst` morph and an 8° press tilt use a medium-bouncy/medium-stiffness spring. There is no idle animation; tapping advances to the app. | `presentation/screens/ReleaseNotesScreen.kt` (`FinishPage`) |

No frame-by-frame Apple popup motion measurements are currently recorded. Before describing any timing as Apple-like, capture the Apple reference and fill the capture fields above; otherwise call the values Android implementation parameters. New motion should follow `docs/APP_DESIGN_SYSTEM.md`, and reduced-motion behavior must be checked on device before sign-off.
