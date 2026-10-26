# Position-guide qualification report — I04 / PG06

**Date:** 2026-10-26 (Asia/Tehran)  
**Decision:** **BLOCKED — limited emulator guide smoke only**  
**Source revision:** `d8d86219b7822d03f91ecad7a32ad7f405197213`

This report records the QA actually completed. It does not accept I04, PG01–PG06, or the recognition claims. The [I04 project-review report](../project-review/results/I04.md) contains the command log summary and issue details; the [qualification worksheet](I04-qualification-matrix.md) retains individual rows.

## Environment and artifact

| Field | Result |
| --- | --- |
| Emulator | Android Emulator 36.1.9; `Medium_Phone_API_36.1`; Android 16 / API 36.1; x86_64; 1080×2400; 420 dpi |
| Intended device | Xiaomi Mi 11 Lite NE / Android 14 / API 34 unavailable |
| Debug artifact | `app/build/outputs/apk/debug/app-debug.apk`; SHA-256 `a7f4fbb8ad25f7c7c565e60672e08f3f83ff04478e8a6864a8357079ef7c764e` |
| Permissions | Camera, Usage Access, and accessibility permissions were not granted or changed |

## Initial build and automated results

- `python3 verification/check_source.py` passed.
- `./gradlew :app:testDebugUnitTest :core:test --rerun-tasks --console=plain` passed: 22 app JVM tests and 311 core tests, including all three `GuideJourneyQualificationTest` cases.
- `./gradlew :app:assembleDebug :app:assembleRelease :app:lintDebug :platform:assembleDebugAndroidTest --console=plain` succeeded.
- `./gradlew :platform:connectedDebugAndroidTest --console=plain` ran 37 tests: 36 passed, 1 failed. `RestoreSafetyTest.restoredInstallGetsFreshDeviceLocalTimelineAndReadiness` received a new generated timeline ID after reopening `LocalInstallIdentity`.
- App Android-test compilation failed with 24 Kotlin diagnostics across six test files. The connected app journey did not start.

## Scenario disposition

| Scenario group | Status | Actual result |
| --- | --- | --- |
| I04-01 / PG01–PG05 retests | BLOCKED | A Push-ups guide smoke showed the Side view and instructions. The four-exercise recovery checks, camera framing, permission/settings return, countdown, save and interruption cases were not run. |
| I04-02 / 24 journeys and four standalone trials | BLOCKED | One Push-ups exercise-only setup screen displayed a two-repetition target and zero-second reward; its guide opened. No camera journey, completion, persistence check, or standalone trial was run. |
| I04-03 / eight exercise/lens recognition sets | BLOCKED | No physical pose, repetition-count, invalid/idle movement, readiness, or ten-minute mixed-use trial was run. |
| I04-04 / PG04, PG06, I11 and participant aggregate | BLOCKED | No interruption/save/midnight races, accessibility matrix, or five-person first-use study was run. |

The guide started paused at step 1 with a Play control. Playback advanced to step 2, and a short play/pause interaction returned it to the paused state. Scrolling revealed the full one-repetition and framing instructions. These UI observations are a smoke check only; all row-level matrix statuses remain `NOT RUN`.

Screenshots: [exercise list](../../verification/project-review/I04/emulator-exercises-list.png), [exercise-only terms](../../verification/project-review/I04/emulator-pushup-exercise-only.png), [guide](../../verification/project-review/I04/emulator-pushup-guide.png), [guide playback](../../verification/project-review/I04/emulator-pushup-guide-play.png), [paused guide](../../verification/project-review/I04/emulator-pushup-guide-explicit-pause.png), and [scrolled instructions](../../verification/project-review/I04/emulator-pushup-guide-scrolled.png).

## Initial QA issues and current gates

1. Initial QA found that `RestoreSafetyTest.restoredInstallGetsFreshDeviceLocalTimelineAndReadiness` reopened an identity file with a different timeline ID. The parser defect is fixed; the latest platform suite passed 37/37, including this case.
2. Initial app Android-test compilation found unresolved Compose semantics helpers and `compose.activity` references. Those test sources now compile, and the complete app suite passed 36/36, including workout accessibility/layout coverage.
3. I02 remains blocked on camera lifecycle and mixed-use evidence; I11 and the physical Xiaomi/API 34 qualification and participant rows remain unrun. These gates keep I04 unaccepted.

## Repair follow-up — 2026-10-26

On the repaired dirty source identity `fd23f7f583279e31e16c3b8b419c088a14b1e3ab1878406320fd50a6fd6404db` (base revision `d8d86219b7822d03f91ecad7a32ad7f405197213`), the app connected suite passed **36/36** and the platform connected suite passed **37/37** on Android 16 / API 36.1. The tested debug APK SHA-256 is `69c0a1d1656e0c9fd1b3d122b863286de71d30c06d137d6e6b826d32b407a708`. The full I04 qualification matrix remains unrun; the [current I04 result report](../project-review/results/I04.md) records the repair and remaining gates.
