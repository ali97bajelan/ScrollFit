# Current verification and artifact inventory

Recorded: **2026-10-26, Asia/Tehran**. This is the I10 implementation inventory for the current review-delivery source. It is not a QA result or release qualification.

## Current source identity

| Field | Value |
| --- | --- |
| Base commit | `a52035464db665e6acb7c586c831fbb5d8d541d7` (`master`) |
| Dirty source identity | `85c35166e7063c1f4e6f3663e97d6fe680f335a2b7fa1f2e5ed9e4c5cb13461b` |
| Identity method | SHA-256 over lexicographically sorted changed/added files under `app/src/` and `core/src/`, updating with each relative path, NUL, exact file bytes, NUL. Documentation and unrelated pre-existing user files are excluded. |
| Excluded pre-existing untracked files | `app/release/app-release.apk`, `docs/qa/position-guide/evidence/pg01/counter-tests.txt`, `docs/qa/project-review/COMMIT_MESSAGE_I06-I08.txt` |
| Current-task build/test commands | None run during this implementation phase. Current I09–I11 results: **NOT RUN**. |
| Environment/device/API for this implementation | Linux workspace; no app build, emulator, instrumentation or physical-device run. Device/API: not applicable to this source/document inventory. |

The dirty-source fingerprint covers these files:

- `app/src/androidTest/java/com/example/scrollfit/challenge/WorkoutAccessibilityTest.kt`
- `app/src/androidTest/java/com/example/scrollfit/challenge/WorkoutLayoutTest.kt`
- `app/src/androidTest/java/com/example/scrollfit/monitoring/BlockerAccessibilityTest.kt`
- `app/src/androidTest/java/com/example/scrollfit/monitoring/MonitorLatencyTest.kt`
- `app/src/androidTest/java/com/example/scrollfit/ui/ProductAccessibilityTest.kt`
- `app/src/main/java/com/example/scrollfit/monitoring/MonitorCommandQueue.kt`
- `app/src/main/java/com/example/scrollfit/monitoring/MonitorDependencies.kt`
- `app/src/main/java/com/example/scrollfit/monitoring/MonitorDiagnostics.kt`
- `app/src/main/java/com/example/scrollfit/monitoring/MonitorFailureBoundary.kt`
- `app/src/main/java/com/example/scrollfit/monitoring/MonitoringService.kt`
- `app/src/main/java/com/example/scrollfit/monitoring/SafetySnapshot.kt`
- `app/src/main/java/com/example/scrollfit/monitoring/TargetApps.kt`
- `app/src/test/java/com/example/scrollfit/monitoring/MonitorFailureBoundaryTest.kt`
- `app/src/test/java/com/example/scrollfit/monitoring/SafetySnapshotTest.kt`
- `core/src/main/kotlin/com/example/scrollfit/core/usage/MonitoringController.kt`
- `core/src/test/kotlin/com/example/scrollfit/core/pose/PoseProcessingTest.kt`
- `core/src/test/kotlin/com/example/scrollfit/core/usage/MonitoringControllerTest.kt`

## Build configuration and local APK

| Item | Observed value | Result / limitation |
| --- | --- | --- |
| Source build config | AGP 9.4.0; Gradle wrapper 9.6.0; Kotlin 2.4.10; minSdk 28; compileSdk 37; targetSdk 35; Java/Kotlin JVM target 17; versionCode 2; versionName `0.2.0-t02`; application ID `com.example.scrollfit` | Source configuration inventory only; no build was run. |
| Local artifact | `app/release/app-release.apk`; 91,644,205 bytes; modified 2026-10-26 11:11:16 +03:30 | File exists in the workspace; it is pre-existing user data and was not modified. |
| SHA-256 | `a4f2391635dfc3769751449560716bc7a1c78036d4b896cc3cd49c7b82a0eb52` | Same hash/size as ignored `app/build/outputs/apk/release/app-release.apk`. |
| Adjacent output metadata | Variant `release`; app ID `com.example.scrollfit`; versionCode **1**; versionName `0.2.0-t02`; min SDK for dexing 28 | The recorded versionCode differs from current source's versionCode 2. |
| Source relationship / signing / install provenance | **Unknown** | APK presence and matching output copies do not identify the source revision, signing identity, install state or QA result. Do not treat it as a qualified release candidate. |

## Current run matrix

| Profile / scope | Command or procedure | Date / environment | Discovered | Executed | Pass | Failure / error | Skipped | Result and evidence |
| --- | --- | --- | ---: | ---: | ---: | ---: | ---: | --- |
| I09 source/JVM, Android and measurement fixtures | `./gradlew -p verification/core :core:test --console=plain`; `./gradlew :app:testDebugUnitTest --console=plain`; I09 instrumentation and matched-device measurement | Not run; no device/API | Not discovered | 0 | 0 | Not measured | Not measured | **NOT RUN**; fixtures are listed in [I09 handoff](implementation-handoff-I09.md). |
| I10 documentation/artifact review | Review current README, task register, Gradle config, APK and output metadata; run DOC profile during QA | Inventory recorded 2026-10-26; no build/device | N/A | N/A | N/A | N/A | N/A | Implementation inventory prepared; DOC QA **NOT RUN**. |
| I11 Compose/native blocker and physical accessibility | `./gradlew :app:assembleDebug :app:assembleRelease :app:lintDebug :app:assembleDebugAndroidTest :platform:assembleDebugAndroidTest --console=plain`; `./gradlew :app:connectedDebugAndroidTest :platform:connectedDebugAndroidTest --console=plain`; manual I11 matrix | Not run; device/API not applicable | Not discovered | 0 | 0 | Not measured | Not measured | **NOT RUN**; all 828 rows in [I11 matrix](I11-accessibility-matrix.md) are NOT RUN. |
| Structural source review | `python3 verification/check_source.py` | Linux workspace, 2026-10-26 | N/A | 1 check | Passed | 0 | N/A | XML/TOML, localized resource parity, string references, narrow accessibility capabilities and whitespace passed. This is not compilation, behavioral verification or the full DOC link/anchor review. |

## Preserved historical results

These older records are not evidence for the dirty source identity above and do not qualify I09–I11.

| Historical record | Command / environment | Discovered | Executed | Pass | Failure / error | Skipped | Result / evidence availability |
| --- | --- | ---: | ---: | ---: | ---: | ---: | --- |
| T01 core compile before I01 classpath correction | `:core:test`; compile failed at `CoverageRecorderTest.kt` for missing coroutine test symbols | Not reached | 0 | 0 | 1 compilation failure | 0 | Recorded in `TASK_STATUS.md`; not a behavioral test failure. |
| Unfiltered platform instrumentation, 2026-10-23 | `:platform:connectedDebugAndroidTest`; Android 16 emulator `Medium_Phone_API_36.1`, API 36.1, x86_64 | 22 | 22 | 21 | 1 native MediaPipe crash in `OfflinePoseTest.blankImageRunsPackagedNativeInferenceWithoutAPose` | 0 | Recorded in `TASK_STATUS.md`; the process crash is a failed/incomplete run, not a green remainder. The original log is not present in this checkout. |
| T14 Android-free core suite | `./gradlew -p verification/core :core:test --console=plain`; Kotlin 2.4.10, JUnit 4.13.2, Java 21 / JVM target 17 | 279 | 279 | 279 | 0 | 0 | Recorded in `TASK_STATUS.md`; preserved log/XML are not present in this checkout. It predates I09 source changes. |
| T15 source/resource review | Source/resource checks only; no Gradle test or instrumentation run at that delivery | No test discovery | 0 tests | N/A | N/A | N/A | Historical T15 record in `TASK_STATUS.md`; does not establish current UI/device behavior. |

The intended device is an Android 14 / API 34 phone. No result on that device is recorded here. The prior API 36.1 emulator run is not a substitute for that device.

## QA commands to run later

I09–I11 are implemented but require their QA phase. Run the unfiltered current suites and retain exact command, exit code, XML totals, crash/incomplete-run data, artifact hash and source identity:

```bash
./gradlew -p verification/core :core:test --console=plain
./gradlew :app:testDebugUnitTest --console=plain
./gradlew :app:assembleDebug :app:assembleRelease :app:lintDebug :app:assembleDebugAndroidTest :platform:assembleDebugAndroidTest --console=plain
./gradlew :app:connectedDebugAndroidTest :platform:connectedDebugAndroidTest --console=plain
python3 verification/check_source.py
```

For I09, compare baseline and changed source using the same rapid-switch workload, slow metadata/storage fixture, sample count and device/API; report median, p95 and max per stage. For I11, record each matrix row separately and include actual TalkBack, mute/offline-voice/reduced-motion, text contrast and camera-transform observations. Any new build artifact must get its own version, hash and provenance row; do not overwrite this inventory with inferred values.

## QA supplement — I01–I03, 2026-10-26

This supplement records the later QA run against the source revision below. It supersedes the “no current-task build/test commands” statement above for I01–I03 only; the I09–I11 rows remain unchanged.

| Field | Value |
| --- | --- |
| Tested source revision | `d8d86219b7822d03f91ecad7a32ad7f405197213`; no tracked source changes at test time |
| Pre-existing untracked user files | `app/release/app-release.apk`; `docs/qa/position-guide/evidence/pg01/counter-tests.txt`; neither was modified |
| Emulator | Android Emulator 36.1.9; `Medium_Phone_API_36.1`; Android 16 / API 36.1; x86_64; 1080×2400 at 420 dpi |
| Intended physical device | physical Android 14 / API 34 phone was unavailable |
| Tested debug APK | `app/build/outputs/apk/debug/app-debug.apk`; application ID `com.example.scrollfit`; versionCode 2; versionName `0.2.0-t02`; SHA-256 `a7f4fbb8ad25f7c7c565e60672e08f3f83ff04478e8a6864a8357079ef7c764e` |

| Task | Command/result | QA decision |
| --- | --- | --- |
| I01 | `./gradlew -p verification/core :core:test --console=plain`; **311 passed, 0 failed/errors/skipped**. Resolved coroutine core/test are 1.10.2; isolated core classpath has no Android runtime. | **PASS** — [report](results/I01.md) |
| I02 | `./gradlew :platform:connectedDebugAndroidTest --console=plain`; **37 total, 36 passed, 1 failed**. All 8 bitmap ownership and 3 offline pose tests passed. `RestoreSafetyTest.restoredInstallGetsFreshDeviceLocalTimelineAndReadiness` failed at line 28 because reopening the same identity file returned a different generated timeline ID. | **BLOCKED** — required camera lifecycle and ten-minute mixed-use qualification remains unrun; [report](results/I02.md) |
| I03 | App JVM tests **22/22 passed** and `:app:assembleDebug` succeeded. App instrumentation compilation failed with **24 Kotlin diagnostics** across six test files, including unresolved Compose test APIs and `compose.activity`; production loop/device scenarios did not run. | **FAIL** — [report](results/I03.md) |

QA logs, XML results, the platform failure logcat, and the emulator launch screenshot are under `docs/verification/project-review/I01/`, `I02/`, and `I03/`. The API 36.1 emulator result does not replace the missing intended-device qualification.

## QA supplement — I04–I05, 2026-10-26

These results use source revision `d8d86219b7822d03f91ecad7a32ad7f405197213`, Android Emulator 36.1.9 / `Medium_Phone_API_36.1` / Android 16 API 36.1 / x86_64 / 1080×2400 at 420 dpi, and debug APK SHA-256 `a7f4fbb8ad25f7c7c565e60672e08f3f83ff04478e8a6864a8357079ef7c764e`. The intended physical Android 14 / API 34 phone was unavailable. No camera, Usage Access, or accessibility permission was granted during the limited UI smoke.

| Task | Command/result | QA decision |
| --- | --- | --- |
| I04 | `python3 verification/check_source.py` passed. App JVM tests **22/22** and core tests **311/311** passed. `:app:assembleDebug`, `:app:assembleRelease`, `:app:lintDebug`, and `:platform:assembleDebugAndroidTest` succeeded. `:platform:connectedDebugAndroidTest` ran **37 tests: 36 passed, 1 failed** in `RestoreSafetyTest.restoredInstallGetsFreshDeviceLocalTimelineAndReadiness` because reopening returned a different generated timeline ID. A limited emulator smoke opened Push-ups exercise-only mode and the guide, but did not run camera recognition or qualification matrices. | **BLOCKED** — [report](results/I04.md); I02 and I11 gates, the Android-test compile issue, and intended-device evidence remain open |
| I05 | `:app:testDebugUnitTest :core:test --rerun-tasks` passed: **22 app tests and 311 core tests**, including all four `MonitorLifecycleTest` cases. `:app:connectedDebugAndroidTest` failed at `:app:compileDebugAndroidTestKotlin` with the same **24 Kotlin diagnostics across six app test files**; the service lifecycle instrumentation did not execute. Device boot/persistence/metadata failure injection was not run. | **BLOCKED** — [report](results/I05.md) |

The app Android-test compiler blocker includes unresolved Compose semantics helpers and `disposeContent`, unresolved `compose.activity` references, and one related type-inference error. The compiler diagnostics are logged at `docs/verification/project-review/I05/app-connected.log`; the platform test failure is in `docs/verification/project-review/I04/platform-connected.log`. Both task reports describe the evidence and remaining gates.

## QA repair follow-up — I02–I05, 2026-10-26

This follow-up supersedes the automated failure counts above for the repaired dirty source. The earlier report sections preserve the original findings and tested source identity.

| Field | Value |
| --- | --- |
| Base revision | `d8d86219b7822d03f91ecad7a32ad7f405197213` |
| Dirty-source identity | `fd23f7f583279e31e16c3b8b419c088a14b1e3ab1878406320fd50a6fd6404db` — SHA-256 over sorted modified files under `app/src/` and `platform/src/`, path and exact file bytes separated by NULs |
| Emulator | Android Emulator 36.1.9; `Medium_Phone_API_36.1`; Android 16 / API 36.1; x86_64; 1080×2400 at 420 dpi |
| Intended physical device | physical Android 14 / API 34 phone unavailable |
| Tested debug APK | `app/build/outputs/apk/debug/app-debug.apk`; application ID `com.example.scrollfit`; versionCode 2; versionName `0.2.0-t02`; SHA-256 `69c0a1d1656e0c9fd1b3d122b863286de71d30c06d137d6e6b826d32b407a708` |

| Task | Fresh result | Current QA status |
| --- | --- | --- |
| I02 | `./gradlew :platform:connectedDebugAndroidTest --console=plain` — **37/37 passed**, including the persisted install timeline reopening case. | **BLOCKED** — camera lifecycle and ten-minute mixed-use evidence remain unrun. |
| I03 | `./gradlew :app:connectedDebugAndroidTest --console=plain` — **36/36 passed**, including `ProductionLoopTest`; app test compilation succeeds. | **BLOCKED** — I03-01–I03-06, dependencies I02/I05–I09, and intended-device qualification remain open. |
| I04 | Same app and platform connected suites passed; workout accessibility/layout and restore-safety tests passed. | **BLOCKED** — PG01–PG06, recognition, physical journeys, participant and I11 rows remain unrun. |
| I05 | Same app connected suite passed, including both `MonitoringServiceLifecycleTest` cases. | **BLOCKED** — device failure injection and real service stop/destroy/rebind remain unrun. |

`./gradlew :app:testDebugUnitTest :core:test --rerun-tasks --console=plain` passed **22 app and 311 core tests**. `python3 verification/check_source.py` passed XML/TOML, localized resource parity, string references, narrow accessibility capability and whitespace checks. App and platform XML results are preserved in [the repair evidence directory](../../verification/project-review/2026-10-26-repair/). The automated source/test defects are resolved; the task-level acceptance gates listed above remain.
