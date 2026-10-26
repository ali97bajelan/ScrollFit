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

The intended physical device remains Xiaomi Mi 11 Lite NE / Android 14 / API 34. No result on that device is recorded here. The prior API 36.1 emulator run is not a substitute for that device.

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
