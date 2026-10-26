# I03–I05 implementation handoff

Status for all three tasks: **IMPLEMENTED — QA NOT RUN**. This handoff records implementation preparation only. It is not an acceptance report.

## Source identity

| Field | Value |
| --- | --- |
| Base revision | `121de29ed6c7f0f13feb63b62143793fd7125712` |
| Changed-source identity | `7a206bf2ee1dca3abe95dc5f12fd75566893f1e042ca742e45a7dc878d23ffef` — SHA-256 of the sorted per-file SHA-256 manifest for changed app/platform build files and Kotlin production/test fixtures listed below, comparing the working tree with the base revision; excludes docs and the two pre-existing user files |
| Pre-existing untracked user files | `app/release/app-release.apk`; `docs/qa/position-guide/evidence/pg01/counter-tests.txt` |
| Gradle/build/device commands run during Implementation | None |

## I03 — production monitoring and reward loop

Changed files: `app/src/androidTest/java/com/example/scrollfit/monitoring/ProductionLoopTest.kt`, `app/build.gradle.kts`, `docs/PROJECT_REVIEW_DELIVERY_TASKS_2026-10-26.md`, `docs/superpowers/plans/2026-10-26-project-review-delivery.md`, `docs/qa/project-review/2026-10-26-scenarios.md`, and this handoff.

Prepared `ProductionLoopTest.kt` with temporary Room databases and production `BudgetEngine`/`RoomStore` flows. Cases cover per-app and shared ceilings, duplicate award callbacks across engine recreation, all four exercises across Advance and Just-in-Time modes, exercise-only no-credit behavior, reduced/refused capacity, session prompt wait, and persisted cursor/usage. Existing CORE `MonitoringControllerTest` fixtures provide midnight splitting (`midnightSplitsIntoOriginalDailyRowsAndExpiresOldRewardChallenge`), access-loss recovery (`healthLossPausesAndPermissionRecoveryNeedsFreshForegroundObservation`), and process-restart/reboot without backfill (`processRestartAndRebootExcludeUnknownDowntimeAndKeepDurableBudget`). They are suite-level fixtures, not current Android service acceptance: this Room test does not drive Android's accessibility service, prove real foreground eligibility, or simulate a bound service kill/rebind.

Applicable scenarios: I03-01–I03-06; source task checklists T02, T05, T11, T13 and T16. QA remains gated by I01, I02 and I05–I09 acceptance; the final recognition claim also needs I04.

Tests/fixtures written but unrun: `ProductionLoopTest.perAppAndCombinedCeilingsPersistAcrossEngineRecreation`, `ProductionLoopTest.perAppBudgetBlocksBeforeSharedCeiling`, `ProductionLoopTest.eachExerciseAndEarningModeCommitsOneDurableAward`, and `ProductionLoopTest.sessionPromptWaitDoesNotSpendAndSecondResolutionIsStale`.

QA commands: `./gradlew :core:test :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest --console=plain`; `./gradlew :app:connectedDebugAndroidTest --console=plain`. Then run I03-01–I03-06 on the intended Xiaomi Mi 11 Lite NE / Android 14 API 34 if available; retain exact T02/T05/T11/T13/T16 results. The intended-device and service-level claims stay blocked until that evidence exists.

## I04 — position-guide qualification

Changed files: `app/src/test/java/com/example/scrollfit/challenge/GuideJourneyQualificationTest.kt`, `docs/qa/position-guide/I04-qualification-matrix.md`, `docs/PROJECT_REVIEW_DELIVERY_TASKS_2026-10-26.md`, `docs/superpowers/plans/2026-10-26-project-review-delivery.md`, `docs/qa/project-review/2026-10-26-scenarios.md`, and this handoff. No guide production source was changed in this task; the listed recovery and paused-demo behavior was present at review time and still needs qualification.

Prepared the [I04 row-by-row worksheet](../position-guide/I04-qualification-matrix.md), retaining historical PG01 results and giving the two previously failed scenarios explicit retest IDs. It lists 24 journey rows, four standalone trials, eight exercise/lens recognition rows with three trial columns, 24 locale/theme/layout combinations and 20 anonymous participant/exercise rows. All results are `NOT RUN`.

Source reconciliation confirms `ExerciseGuideContent` now renders localized recovery copy and paused-by-default Play/Pause/Previous/Next controls with an 800 ms step interval. `CameraScreen`'s standalone path constructs an in-memory exercise-only challenge and does not call the persistence engine; the four no-save trials remain required QA. Existing `CameraRunOwnershipTest` covers help/audio recomposition, late camera callbacks and release.

Added `GuideJourneyQualificationTest` cases for stale position callbacks, permission loss during countdown and failed explicit start. PG01–PG06 and I11 shared rows still require their source/build/device checks and physical evidence.

Applicable scenarios: I04-01–I04-04, PG01–PG06, and shared I11 rows. Tests/fixtures written but unrun: `GuideJourneyQualificationTest.oldPositionRunCallbackCannotMakeNewRunReady`, `GuideJourneyQualificationTest.permissionLossDuringCountdownCancelsStartedChallengeBeforeActivation`, and `GuideJourneyQualificationTest.failedExplicitStartReturnsToPositionWithoutStartingCountdown`.

QA commands: `python3 verification/check_source.py`; `./gradlew -p verification/core :core:test --console=plain`; `./gradlew :app:testDebugUnitTest :app:assembleDebug :app:assembleRelease :app:lintDebug :platform:assembleDebugAndroidTest :app:assembleDebugAndroidTest --console=plain`; `./gradlew :app:connectedDebugAndroidTest :platform:connectedDebugAndroidTest --console=plain`. Then execute PG01–PG06 and I04-01–I04-04; record physical recognition, ten-second readiness, ten-minute mixed use, and five-participant aggregates. Create `QUALIFICATION_REPORT.md` during QA only.

## I05 — retryable initialization

Changed files: `app/src/main/java/com/example/scrollfit/monitoring/MonitorLifecycle.kt`, `MonitorDependencies.kt`, `MonitoringService.kt`, `MonitorRuntime.kt`, `BudgetOverlay.kt`, `ForegroundReconciler.kt`; fixtures in `app/src/test/java/com/example/scrollfit/monitoring/MonitorLifecycleTest.kt` and `app/src/androidTest/java/com/example/scrollfit/monitoring/MonitoringServiceLifecycleTest.kt`; `platform/build.gradle.kts` exports Room's public supertype dependency; `docs/PROJECT_REVIEW_DELIVERY_TASKS_2026-10-26.md`, `docs/superpowers/plans/2026-10-26-project-review-delivery.md`, `docs/qa/project-review/2026-10-26-scenarios.md`, and this handoff.

Added separate `STOPPED / INITIALIZING / FAILED / READY` lifecycle state, an explicit `MonitorRuntime.retryMonitoring()` route, serialized/coalesced attempts, generation checks around each boot/persistence/metadata dependency, and a single command consumer per service connection. A stopped or disconnected attempt cannot publish partial resources or late `READY`. The service adapter now supplies clocks, boot/access, metadata/safety lookups, persistence, and overlay creation. Initialization does not enable monitoring or acquire the ownership lease; the existing readiness, controller and presentation policies remain in place.

Tests/fixtures written but unrun: `MonitorLifecycleTest.failOnceThenRetryReachesReady`, `retryBurstCreatesOneWorker`, `disableDuringInitRejectsLateResult`, `retryPreservesBudgetAndGap`, and `MonitoringServiceLifecycleTest.retriesBootFailureWithoutCreatingASecondConsumerOrLease`. The Android-run fixture exercises the production lifecycle coordinator with injected boot failure; it does not bind a real `AccessibilityService` or verify Android owner permissions.

Applicable scenarios: I05-01–I05-04, plus I05 retry/disable regressions at the integrated I03/F-A checkpoint. QA commands: `./gradlew :app:testDebugUnitTest :core:test --console=plain`; `./gradlew :app:assembleDebug :app:assembleDebugAndroidTest --console=plain`; `./gradlew :app:connectedDebugAndroidTest --console=plain`. On device, inject boot identity, persistence and metadata fail-once paths separately; confirm a retry does not change balances/cursors, records a gap, begins from fresh observations and never creates a second consumer or lease.

No result file under `docs/qa/project-review/results/` was created; those files are reserved for QA execution.
