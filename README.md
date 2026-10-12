# ScrollFit — Exercise Gate

Native Kotlin/Jetpack Compose app for limiting selected apps and earning time through on-device exercise. Current work covers **T01 and the T02 feasibility experiment**. The product UI remains Persian and RTL; communication and these delivery instructions are in English.

The full product specification is in [the brief](exercise_gate_codex_brief.md), ordered tasks in [the task list](exercise_gate_tasks.md), and actual progress in [TASK_STATUS.md](TASK_STATUS.md). T03–T16 are not implemented.

## Current delivery status

The source includes onboarding, dashboard, four-exercise introduction, empty history, proposed defaults, and a debug-only **30-second single-app monitoring/blocking spike**. The spike has an app picker, explicit Start/Stop controls, Usage Access and Accessibility settings links, countdown banner, and blocking overlay with Home/system-panel/Stop controls. Camera, exercise counting, daily budgets and rewards are not implemented.

**There is no APK yet.** The Android build fails during configuration because the AGP 9.4.0 plugin cannot be resolved. Official Google Maven and Android SDK downloads returned HTTP 404 from this environment, including outside the sandbox. Android SDK, adb and emulator are absent. Gradle 9.6.0 and a temporary Temurin JDK 21 run successfully. T01 and T02 remain blocked at delivery/verification gates.

The actual Kotlin/JVM core compiled and **10 countdown tests passed**, with zero failures/errors/skips, through the isolated core build. The Android service, Compose screen, manifest merge and overlay have **not** compiled or run on a phone/emulator. Source XML, resource references and restricted service configuration passed structural checks. These checks do not prove enforcement.

Evidence: [T02 Android build log](docs/verification/T02-build.log), [core test log](docs/verification/T02-core-tests.log), [actual JUnit results](docs/verification/T02-countdown-tests.xml). T01 evidence remains in [its build log](docs/verification/T01-build.log) and [tool version output](docs/verification/T01-gradle-version.log).

Intended device: **Xiaomi Mi 11 Lite NE, Android 14 / API 34**, provided by the owner. No manufacturer behavior or device test outcome has been confirmed.

## Build and install

Use Android Studio compatible with AGP 9.4, with **JDK 21** selected as Gradle JDK. Install **Android SDK Platform 37**, **Build Tools 36.0.0** and **Platform Tools** through SDK Manager. Android Studio records the SDK location in untracked `local.properties`. Linux/WSL needs Linux tools; Windows needs Windows tools. Initial builds require access to Google Maven, Maven Central, Gradle Plugin Portal and Gradle distributions.

On Linux/WSL:

```bash
export JAVA_HOME=/path/to/jdk-21
export ANDROID_HOME=/path/to/android-sdk
./gradlew :app:assembleDebug :app:lintDebug :core:test --console=plain
```

On Windows PowerShell with the Windows JDK/SDK configured:

```powershell
.\gradlew.bat :app:assembleDebug :app:lintDebug :core:test --console=plain
```

The expected artifact after **BUILD SUCCESSFUL** is `app/build/outputs/apk/debug/app-debug.apk`. That file does not currently exist. After it is built, transfer the APK to your phone, open it, and allow installation from that file manager/browser. With USB debugging enabled:

```bash
adb devices -l
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Follow [the T02 phone test](docs/T02_PHONE_TEST.md). The full product acceptance checklist remains in the brief for T16.

To run real core sources/tests without configuring Android modules:

```bash
./gradlew -p verification/core :core:test --console=plain
```

The isolated settings use the same `core/build.gradle.kts`, source files, tests and version catalog; they do not replace Android verification. For this session the temporary paths are `/tmp/scrollfit-toolchain/jdk-21.0.12.1+1` and `/tmp/scrollfit-gradle`; they are not part of the repository and may be deleted by the host. Java and Kotlin bytecode targets are both 17.

## T02 implementation and limits

The experiment is **off by default** and available through the dashboard's 30-second test button in debug builds. Its AccessibilityService declaration, special access permission, picker queries and Android implementation are in `app/src/debug`; release sources contain only a no-op bridge. Release manifest/build isolation is source-inspected, not yet verified in a built APK.

Select one launchable non-system app, grant both required accesses, then explicitly start a new test. System and preinstalled apps, this app, launchers, settings and dialers are excluded conservatively. There is no `QUERY_ALL_PACKAGES` permission. The picker intentionally has less coverage than the future production picker.

The system-bound AccessibilityService receives only `TYPE_WINDOW_STATE_CHANGED`. Its configuration disables window-content retrieval and gestures and declares `isAccessibilityTool=false`: this is a self-control experiment. Code reads event package metadata, never event text, source nodes or another app's screen tree. Temporary usage-event reconciliation on unlock reads only foreground/background metadata; it neither logs nor stores other apps' package history. Limited package queries follow [Android package visibility guidance](https://developer.android.com/training/package-visibility/declaring).

The blocking surface uses [TYPE_ACCESSIBILITY_OVERLAY](https://developer.android.com/reference/android/view/WindowManager.LayoutParams#TYPE_ACCESSIBILITY_OVERLAY), supported for a connected service. It stays within the app window area, does not request fullscreen/immersive mode, and leaves system navigation available. Home and quick-settings actions are triggered by visible buttons. The service never starts an Activity from the background, avoiding reliance on [background activity launch exceptions](https://developer.android.com/guide/components/activities/secure-bal). Special-access screens and the target app are opened only from the visible Activity.

A monotonic countdown charges prior eligible intervals once. The intended signals pause it outside the selected app, while the screen is off/locked, while the blocker is shown, and when required access is missing. Small non-touchable banners update at most once per second, only while target use is observed; there is no continuous usage-statistics polling, foreground service, wake lock or boot receiver. [UsageEvents queries](https://developer.android.com/reference/android/app/usage/UsageStatsManager#queryEvents(long,%20long)) are bounded to five minutes. [Screen broadcasts](https://developer.android.com/develop/background-work/background-tasks/broadcasts) and UI lifecycle callbacks trigger pauses/reconciliation.

Spike state uses private SharedPreferences for target, enabled state and consumed milliseconds, persisted on ticks/transitions. Reconnection retains consumption and pauses unknown downtime; monitoring is not assumed healthy merely because its permission is enabled. An abrupt process kill can lose approximately the last one-second persistence interval plus scheduling/storage delay; callback delays and actual OEM behavior are unverified. This store is not the production daily budget or award ledger.

**Start new test resets the experimental allowance.** That control is debug-only, explicitly labeled, and grants no production credit. Stop removes the spike overlay without changing access permissions. Permission loss, service disconnect, overlay/storage failure and interruption have visible states; an unavailable blocker is not reported as successful enforcement.

Only a single fullscreen target is in scope for this spike. Split-screen, PiP, window/dialog/keyboard event ordering and metadata suppression need real-device investigation; event observation can fail or pause conservatively. No reliable reboot recovery is claimed. Force stop, revocation and uninstall can bypass limits. Emergency/system navigation and unrestricted apps must remain usable. Battery restrictions have not been observed yet; record them before prescribing Xiaomi-specific changes.

## Architecture

| Module | Responsibility |
| --- | --- |
| `:app` | MainActivity, Persian Compose navigation, screens and debug-only T02 service/overlay/setup UI. |
| `:core` | Android-free Kotlin/JVM contracts for pose/counters/clocks/monitoring/blocking, four exercise types and configurable proposals; separate experimental countdown with tests. Production accounting belongs to T04. |
| `:platform` | Android clock adapters and pinned CameraX/Room/DataStore/MediaPipe dependencies. Persistence and pose implementations belong to T03/T06. |

Dependencies flow `app → platform → core` and `app → core`. Time units are explicit. There is no backend or DI framework. Each future exercise detector will have independent replaceable state. Room schema/KSP are deferred to the actual T03 schema.

## Dependency decisions and official sources

Documentation checked on **2026-10-12**. These are pinned documented choices; final Android artifact resolution and compatibility remain unverified because the build is blocked.

| Component | Selection | Source |
| --- | --- | --- |
| AGP / Gradle / SDK | 9.4.0 / 9.6.0 / compile and target 37; existing minimum 28 retained | [AGP compatibility](https://developer.android.com/build/releases/agp-9-4-0-release-notes) |
| Kotlin / Compose compiler | 2.4.10, with explicit KGP classpath; AGP built-in Kotlin | [Kotlin releases](https://kotlinlang.org/docs/releases.html), [built-in Kotlin](https://developer.android.com/build/migrate-to-built-in-kotlin), [compiler compatibility](https://developer.android.com/jetpack/androidx/releases/compose-kotlin) |
| Compose | Stable BOM 2026.09.00, Material 3 | [Compose BOM](https://developer.android.com/develop/ui/compose/bom) |
| Activity / Navigation | 1.13.0 / 2.10.2 | [Activity](https://developer.android.com/jetpack/androidx/releases/activity), [Navigation](https://developer.android.com/jetpack/androidx/releases/navigation) |
| CameraX | 1.6.2 for camera2/lifecycle/view | [CameraX](https://developer.android.com/jetpack/androidx/releases/camera) |
| Room / DataStore Preferences | 2.8.5 / 1.2.1 | [Room](https://developer.android.com/jetpack/androidx/releases/room), [DataStore](https://developer.android.com/jetpack/androidx/releases/datastore) |
| Coroutines Android | 1.10.2 | [Official release](https://github.com/Kotlin/kotlinx.coroutines/releases/tag/1.10.2) |
| Pose engine | MediaPipe Tasks Vision 1.0.0 | [Official Google sample](https://github.com/google-ai-edge/mediapipe-samples/blob/main/examples/pose_landmarker/android/app/build.gradle), [pose guide](https://developers.google.com/edge/mediapipe/solutions/vision/pose_landmarker/android), [Android requirements](https://developers.google.com/edge/mediapipe/solutions/setup_android) |

Pose provisioning choice: Lite float16 model version 1, one body and CPU delegate. In T06 the [official model](https://storage.googleapis.com/mediapipe-models/pose_landmarker/pose_landmarker_lite/float16/1/pose_landmarker_lite.task) will be obtained at build preparation, with checksum/terms recorded, and bundled as `platform/src/main/assets/pose_landmarker_lite.task`. The model is **not yet downloaded or packaged**; only its path/URL are defined. T06 must enforce asset presence/integrity and test the first exercise launch after installation in airplane mode. There will be no first-run runtime download, recordings or frame uploads. [ML Kit pose](https://developers.google.com/ml-kit/vision/pose-detection/android) was evaluated; its beta status and the explicit MediaPipe asset workflow informed this choice.

## Configurable product proposals

Per-app base allowance: 30 minutes/day. Combined usage ceiling: 60 minutes/day. Per-app daily award cap: 20 minutes awarded, including already consumed awards. Short session: 5 minutes. Zero-base mode: off. Speech: on. Suggested camera: rear. Rewards: 2 push-ups, 10 squats, 20 jumping jacks or 20 alternating knee raises, each for 2 minutes. These are editable product proposals, not exercise prescriptions or equivalence claims.

Future rules: local-midnight allowance reset/credit expiry; old-day reward challenges cancel at rollover without new-day awards. Elapsed use uses monotonic time. Deliberate device-clock abuse is not hardened in v1. Production split-screen/PiP accounting, reboot recovery and durable accounting-loss bounds await T05 evidence.
