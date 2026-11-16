# Project instructions

## UI and interaction work

Before changing or adding any screen, popup, control, onboarding page, release note, or animation:

1. Read [`docs/APP_DESIGN_SYSTEM.md`](docs/APP_DESIGN_SYSTEM.md).
2. For motion work, also read [`docs/MOTION_REVERSE_ENGINEERING.md`](docs/MOTION_REVERSE_ENGINEERING.md).
3. Inspect and reuse the existing shared Compose components and the corresponding LibrePods reference implementation before writing a new one-off component.
4. Preserve the selected Android/iOS appearance and existing user preference. Do not silently switch design systems or redesign unrelated surfaces.
5. Verify the result on a real device or emulator at the relevant screen sizes; for visual changes, capture and inspect screenshots. Update the design notes when a deliberate deviation establishes a new reusable rule.

The design documents record verified project behavior and reference patterns; they are not permission to copy proprietary Apple assets or to claim that Android can reproduce an iOS system surface exactly.

## Publishing and update introductions

- Before each public app release, capture the changed screens from the running app on a device or emulator, remove status-bar/personal notification details, inspect the captures, and include at least one matching real screenshot for every feature/fix page in the version-specific in-app update introduction (`Updates.kt` and its screenshot resources). Keep the public release description consistent with the same changes and screenshots. Do this automatically whenever a release is requested; do not wait for the user to remind you.
