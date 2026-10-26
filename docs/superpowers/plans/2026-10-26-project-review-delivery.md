# ScrollFit Project Review Delivery Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans when implementation is requested. Execute one task's Implementation phase, hand it off, then execute its QA phase separately. Checkboxes track future work; this document does not execute it.

**Goal:** Correct reliability gaps and qualify ScrollFit before selectively implementing the review's optional features.

**Architecture:** Preserve `app → platform → core` and existing controller/accounting policy. Add focused Android orchestration seams, explicit ownership/recovery contracts and evidence-driven qualification. Optional features reuse current stores and visible-history semantics.

**Tech Stack:** Existing Kotlin/JVM 17, Compose, CameraX, MediaPipe, Room, DataStore, JUnit, Android instrumentation and Markdown evidence.

**Spec:** [delivery specification](../specs/2026-10-26-project-review-delivery-design.md). Also read the [register/dependencies](../../PROJECT_REVIEW_DELIVERY_TASKS_2026-10-26.md) and [QA scenarios](../../qa/project-review/2026-10-26-scenarios.md).

## Global Constraints

- Every task has exactly two delivery phases: Implementation and QA. Implementation performs no Gradle commands, builds, Gradle lint, emulator tests, instrumentation, or physical-device acceptance. Write relevant tests/fixtures during Implementation; execute them during QA.
- Retain the inspected configuration: minSdk 28, compileSdk 37, targetSdk 35, JVM target 17, versionCode 2, versionName `0.2.0-t02`.
- Retain pinned libraries; I01 adds coroutine JVM core/test artifacts at existing `1.10.2`, with no Android dependency in `core`.
- Preserve `BudgetEngine` authority, counters, immutable challenge terms, unique awards, usage cursor and unknown-period handling. No restoration of unfinished repetitions.
- Preserve offline processing, package-only accessibility, protected surfaces, immediate blocker dismissal, queue capacity 64 and maximum trusted observation age 5,000 ms.
- Persian RTL/English/French, light/dark, 48 dp actions, 320 dp width at 200% font and 640×360 dp landscape; Start/End/Stop/recovery remain reachable.
- No private frames, landmarks, screen/node text, raw events or app timelines in diagnostics/evidence. Required FAIL/BLOCKED/NOT RUN prevents acceptance.

## Review Focus

1. Retry/disable/disconnect while initialization or storage is suspended: late work cannot restore enabled/healthy state (I05, I06, F-A).
2. Recycled inputs and identity-alias cleanup after native errors/close: no recycled input reaches native inference (I02).
3. Rapid protected-role changes, 65 queued events and observations older than 5,000 ms: immediate safe navigation, gap and no guessed spending (I06, I09, I03).
4. Midnight/zone change/hidden history while reading or exporting: preserve original record identities and visibility (I03, I12, F-D, F-E, F-G).
5. Permission/connection shown as enforcement, or stale evidence reused after code changes: honest recovery/freshness and exact source/artifact identity (I10, F-A, F-F).

## File conventions and QA commands

All paths are relative to repository root. Prefixes used only to keep task entries readable:

- `A` = `app/src/main/java/com/example/scrollfit`
- `C` = `core/src/main/kotlin/com/example/scrollfit/core`
- `P` = `platform/src/main/kotlin/com/example/scrollfit/platform`
- `CT` = `core/src/test/kotlin/com/example/scrollfit/core`
- `AT` = `app/src/test/java/com/example/scrollfit`
- `AIT` = `app/src/androidTest/java/com/example/scrollfit`
- `PIT` = `platform/src/androidTest/kotlin/com/example/scrollfit/platform`
- `RES` = `app/src/main/res`; localized additions always cover `values`, `values-en`, `values-fr`.

Files marked Create are proposed new files; inspect current execution-time source before creating them. Each task also produces a handoff and a QA result at `docs/qa/project-review/results/<ID>.md` (created during QA, not prefilled with success).

**QA-only command profiles**, run from repository root. Record exact command/environment and exit code. Select connected-device serial explicitly in the QA environment when more than one exists. Never substitute filtered tests for a full gate.

| Profile | Command | Passing evidence |
| --- | --- | --- |
| DOC | `git diff --check` plus local Markdown link/anchor and task-coverage inspection | Changed/new docs resolve; all task/phase/scenario IDs accounted for |
| SRC | `python3 verification/check_source.py` | Exit 0; structural checks only |
| CORE | `./gradlew -p verification/core :core:test --console=plain` | Exit 0; unfiltered suite, totals with zero failures/errors and explained skips |
| APP | `./gradlew :app:testDebugUnitTest --console=plain` | Exit 0; all app JVM tests compiled/executed and XML totals retained |
| BUILD | `./gradlew :app:assembleDebug :app:assembleRelease :app:lintDebug :app:assembleDebugAndroidTest :platform:assembleDebugAndroidTest --console=plain` | Exit 0; inspect warnings, artifact identities, test APKs and lint report |
| DEVICE | `./gradlew :app:connectedDebugAndroidTest :platform:connectedDebugAndroidTest --console=plain` | Exit 0 plus complete instrumentation reports, discovered/executed totals and no native crash |
| POSE | `./gradlew :platform:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.example.scrollfit.platform.pose.OfflinePoseTest --console=plain` | All native pose cases finish, no recycled-bitmap/native-crash output; follow with DEVICE for the full gate |

POSE is a diagnostic/targeted regression run, never a replacement for the unfiltered platform run. If instrumentation process dies, account for not-executed cases explicitly. A missing toolchain/device/network capability is BLOCKED at its actual stage. Do not automatically upgrade dependencies or recreate old build blockers. Device acceptance uses owner-controlled access grants; a rejected grant is a blocked check, not an enforcement pass.

During Implementation, source reading, XML/TOML parsing, Markdown validation and `git diff --check` are allowed. Do not run any of the Gradle profiles. At handoff list tests as **written, NOT RUN**; QA establishes behavior, and regression sensitivity may be checked against a pre-fix revision in a disposable copy during QA only.

## Task I01 — Core test classpath

**Files:** Modify `gradle/libs.versions.toml`, `core/build.gradle.kts`; inspect `CT/usage/CoverageRecorderTest.kt` and `verification/core/settings.gradle.kts`. No production API change.

**Interfaces:** Existing catalog version `coroutines = "1.10.2"` supplies proposed aliases `kotlinx-coroutines-core` and `kotlinx-coroutines-test`, yielding `libs.kotlinx.coroutines.core/test`. Existing tests consume JVM artifacts only.

### Phase 1 — Implementation

- [x] Add both aliases using `version.ref = "coroutines"`; add required `testImplementation` entries in core alongside JUnit.
- [x] Inspect existing imports against the new classpath and remove no tests or assertions. Document unfiltered command/profile CORE and the previously failing compiler stage in the handoff. Do not execute Gradle.

### Phase 2 — QA

- [ ] Execute I01-01–I01-03 and CORE; verify `CoverageRecorderTest` is discovered/executed alongside the entire suite. Compare XML totals, not an expected historic fixed count.
- [ ] Inspect resolved test dependencies for Android independence; record compilation/result totals and any skips. Mark accepted only with complete unfiltered evidence.

## Task I02 — Caller-owned bitmap inference

**Files:** Modify `P/pose/MediaPipePoseEstimator.kt`, `P/pose/CameraPoseSession.kt`, `C/pose/PoseContracts.kt` contract comments and `PIT/pose/OfflinePoseTest.kt`; create `PIT/pose/BitmapOwnershipTest.kt` for alias/error/cleanup coverage where separate fixtures help.

**Interfaces:** Preserve `PoseEstimator<Bitmap>.estimate(frame: Bitmap, timestampMonotonicMs: Long): PoseSample?` and `close()`. Caller retains input ownership; estimator owns its MediaPipe copy. Keep all operations on the serial worker.

### Phase 1 — Implementation

- [x] Copy valid caller input to owned software ARGB_8888 storage; reject recycled inputs; capture dimensions before disposal and preserve strict timestamp checks.
- [x] Close wrapper/copy on success, null result and exceptions, including construction failure. Deduplicate camera bitmap cleanup by object identity and retain `ImageProxy.close()` in final cleanup. Queue close behind inference.
- [x] Write `sameCallerBitmapRemainsUsableAcrossInference`, `recycledInputRejectedBeforeNativeEntry`, `cropRotationAliasesReleasedOnce` and `closeWaitsForInference` assertions covering input usability, dimensions, 0/90/180/270 rotations and exact cleanup. Keep blank-image repeated inference. Do not run them yet.

### Phase 2 — QA

- [ ] Execute I02-01–I02-04 with POSE, DEVICE and BUILD; inspect native output and completion count. Simulate error/constructor failure using the smallest internal factory seam necessary; production entry point remains unchanged.
- [ ] Measure allocations/cadence over ten minutes with both available lenses; preserve frame release and report copy cost. Any contract alternative returns to Implementation and repeats native QA.

## Task I05 — Initialization recovery

**Files:** Modify `A/monitoring/MonitoringService.kt`, `MonitorRuntime.kt`; create `A/monitoring/MonitorLifecycle.kt`, `MonitorDependencies.kt`, `AT/monitoring/MonitorLifecycleTest.kt`, `AIT/monitoring/MonitoringServiceLifecycleTest.kt`. The lifecycle class serializes startup only; it does not duplicate the core observation controller.

**Interfaces:** Proposed `MonitorInitState { STOPPED, INITIALIZING, FAILED, READY }`; service `retryInitialization(): Unit`; `MonitorRuntime.retryMonitoring(): Unit` routes to the live service. `MonitorDependencies` supplies existing clock/access/boot/foreground/overlay/persistence operations with injectable implementations. Publish init state separately from health/permission facts.

### Phase 1 — Implementation

- [x] Move startup into one guarded attempt with generation invalidation; repeated retry coalesces. Create the command consumer exactly once per service lifecycle and acquire at most one existing ownership lease through normal eligible observation processing.
- [x] Route retry while !ready to initialization; route ready-state refresh through existing configuration/reconciliation. Disable/disconnect cancels partial work and cannot be undone by late initialization completion.
- [x] Write `failOnceThenRetryReachesReady`, `retryBurstCreatesOneWorker`, `disableDuringInitRejectsLateResult`, `retryPreservesBudgetAndGap` with fake suspended dependencies and exact worker/lease counts. Prepare Android lifecycle fixture, no test execution.

### Phase 2 — QA

- [ ] Execute I05-01–I05-04 with APP, CORE and service instrumentation; exercise boot, persistence and metadata fail-once paths separately.
- [ ] Verify recovered processing begins with fresh samples and unchanged durable balances, not a backfilled startup interval. Use DEVICE/BUILD at the integrated service checkpoint.

## Task I06 — Service exception boundaries

**Files:** Modify `A/monitoring/MonitoringService.kt`, `MonitorRuntime.kt`, `ForegroundReconciler.kt`, `MonitorDependencies.kt`; create `AT/monitoring/MonitorFailureBoundaryTest.kt` and extend service instrumentation.

**Interfaces:** Create `MonitorFailurePhase` for BOOT, REGISTRATION, PREFERENCES, METADATA, RECONCILIATION, QUEUE, BUDGET, COVERAGE, OVERLAY, NAVIGATION; typed failure code/category crosses runtime boundary without exception messages. Reuse I05 init state/retry.

### Phase 1 — Implementation

- [x] Guard each Android/asynchronous boundary, rethrow `CancellationException`, and classify failures by phase. On failure invalidate processing/coverage and publish truthful health.
- [x] Track usage-listener/receiver registration independently. Cleanup handles partial setup and continues releasing resources after an unregister error; preserve the overlay-removal lease contract.
- [x] Write `nonSecurityReconcileFailureStopsHealth`, `secondRegistrationFailureCleansFirst`, `cleanupContinuesAfterUnregisterThrows`, `cancellationPropagates`, `removalFailureRetainsOwnership` with no test execution.

### Phase 2 — QA

- [ ] Execute I06-01–I06-04 with APP, CORE and Android boundary fixtures. Verify no unhandled reconciliation coroutine exception and exactly-once cleanup of registered resources.
- [ ] Re-run I05 retry/disable regressions and the safe-surface suspended-storage case before acceptance.

## Task I07 — Local diagnostics

**Files:** Create `A/monitoring/MonitorDiagnostics.kt`, `AT/monitoring/MonitorDiagnosticsTest.kt`; modify `MonitoringService.kt`, `MonitorRuntime.kt` and dependencies' monotonic-clock hook.

**Interfaces:** Proposed immutable `MonitorDiagnosticSnapshot` exposes 128-record bounded sanitized ring, fixed-size phase/latency totals, queue high-water/drops and nullable monotonic observation age. `MonitorDiagnostics.snapshot(nowElapsedMs: Long)` never reads wall-clock time or private event payloads.

### Phase 1 — Implementation

- [x] Add non-suspending bounded recording for phase/code/retry results and aggregate queue/processing latency. Keep package/event/camera data out of fields and string formatting.
- [x] Distinguish last selected-app processing from reconciliation/checkpoints; reset process-local scope explicitly on restart and make cross-boot age Unknown.
- [x] Write `ringRetainsLast128`, `noPrivatePayloadInSnapshot`, `monotonicAgeIgnoresWallClock`, `dropsAndLatencyAggregateWithoutGrowth`; record tests as unrun.

### Phase 2 — QA

- [ ] Execute I07-01–I07-03 with APP; stress at least 10,000 synthetic records, inspect retained size/counters and sanitize output. Confirm process restart scope on Android.
- [ ] Verify instrumentation overhead does not suspend or overflow the monitor under the same rapid-transition fixture used by I09.

## Task I08 — Backup/transfer exclusions

**Files:** Modify `app/src/main/AndroidManifest.xml`, `RES/xml/backup_rules.xml`, `RES/xml/data_extraction_rules.xml`; create `docs/PRIVACY_STORAGE_POLICY.md`, `PIT/data/RestoreSafetyTest.kt`; inspect `P/data/ExerciseGatePersistence.kt`, `PreferencesRepository.kt` and cursor boot identity.

**Interfaces:** No new exported component. Policy consumes exact app-owned database/DataStore locations and produces an explicit no-automatic-cloud/no-transfer contract. Restored state cannot satisfy monitoring readiness.

### Phase 1 — Implementation

- [x] Inventory all app data/storage domains and current backup configuration; implement explicit exclusions under the spec's proposed all-state exclusion policy. Include current/future feature stores and device-protected equivalents; document OEM qualification limits.
- [x] If old-restored enabled/cursor state can establish readiness or collide across devices, add a device-local non-backed-up install/timeline guard without resetting the current install's existing accounting.
- [x] Prepare synthetic old-backup and second-device fixtures asserting fresh readiness and no cursor suppression; document exact cloud/transfer test procedures, without running a device or Gradle command.

### Phase 2 — QA

- [ ] Execute I08-01–I08-04, BUILD and targeted restore instrumentation on disposable app data. Test legacy API 28 backup plus API 31+ cloud/transfer and intended OEM transfer where available.
- [ ] Compare current-device balances before/after ordinary update. Inspect actual backup/restore contents and second-install readiness, not manifest flags alone. Unsupported/unavailable transfer remains BLOCKED.

## Task I09 — Metadata latency and safety snapshot

**Files:** Modify `A/monitoring/TargetApps.kt`, `MonitoringService.kt`; create `A/monitoring/SafetySnapshot.kt`, `AT/monitoring/SafetySnapshotTest.kt`, `AIT/monitoring/MonitorLatencyTest.kt`; reuse I07.

**Interfaces:** Immutable `SafetySnapshot` holds eligible/protected package sets plus revision; its adapter publishes only generation-matching refreshes. Eligibility is withheld while a required safety refresh is pending; callback immediate dismissal precedes all metadata work.

### Phase 1 — Implementation

- [x] Prepare a QA-only before/after timing harness for callback, metadata, queue and persistence delays with deterministic slow dependencies. Keep production collection bounded to I07 aggregates.
- [x] Move package/role queries off callbacks; invalidate on package/input-method/configuration broadcasts and foreground transitions that re-evaluate dynamic roles. Synchronize safe dismissal before async refresh and reject old revisions. A direct role-holder observer is privileged, so record that limitation.
- [x] Write `slowMetadataDoesNotDelayDismissal`, `roleChangeWithholdsEligibility`, `staleSafetyRefreshIgnored`, `overflowAndStaleSamplesPauseWithoutSpend`; include exactly 65 queued commands and 5,001 ms old sample. No benchmark/test run yet.

### Phase 2 — QA

- [ ] Execute I09-01–I09-04 with APP and latency instrumentation. Measure baseline revision and changed revision under matching workload; report median/p95/max/sample count and device/API.
- [ ] Confirm safe dismissal happens before releasing the fake metadata latch, queue stays bounded and recovery uses fresh samples. Do not label results an ANR without actual ANR evidence.

## Task I10 — Documentation and artifact provenance

**Files:** Modify `README.md`, current summary in `TASK_STATUS.md` without rewriting dated evidence; create `docs/qa/project-review/CURRENT_VERIFICATION.md`; link this task register.

**Interfaces:** Matrix rows contain source commit/dirty identity, artifact SHA-256/variant/version, SDK tuple, environment, command/date, executed/failure/skip counts and result/evidence. Unproven artifacts have Unknown provenance, not an inferred current hash/revision relationship.

### Phase 1 — Implementation

- [x] Reconcile current overview with existing source and dated T14/T15/build/guide records. State that full core/native/phone gates need current evidence.
- [x] Prepare current matrix with NOT RUN entries and actual build-config values. Record artifact hashes only when the inspected artifact exists, explicitly distinguishing inventory from tested provenance. Do not build an artifact here.

### Phase 2 — QA

- [ ] Execute I10-01–I10-03 and DOC. Inspect prior full instrumentation failure instead of describing it as a green persistence run.
- [ ] At each combined QA gate, update only rows supported by that run and refresh source/artifact identity. Required product results remain pending until their owning QA passes.

## Task I11 — Accessible physical controls

**Files:** Inspect/conditionally modify `A/ui/`, `A/guide/`, `A/challenge/`, `A/camera/`, `A/monitoring/BudgetOverlay.kt` and localized resources. Extend `AIT/challenge/WorkoutAccessibilityTest.kt`, `WorkoutLayoutTest.kt`; create `AIT/monitoring/BlockerAccessibilityTest.kt` and `AIT/ui/ProductAccessibilityTest.kt`.

**Interfaces:** Existing user actions/semantics remain stable; no new accounting interface. Share matrix row IDs/evidence with I04.

### Phase 1 — Implementation

- [x] Prepare explicit screen × locale/theme/size/font/audio/TalkBack matrix. Add focused fixtures checking reachable actions, reading order, disabled/recovery descriptions and guide transforms.
- [x] Correct source-confirmed layout/semantic defects if identified; record reproduction hypotheses for QA rather than presuming all old failures remain. Document unrun fixture names and control bounds.

### Phase 2 — QA

- [ ] Execute I11-01–I11-04, BUILD and Compose/blocker instrumentation; manually use actual TalkBack, mute/no-voice/reduced-motion flows and physical cameras.
- [ ] Record exact failing screen/locale/size/control, return fixes to Implementation, then retest affected rows and adjacent navigation. Shared evidence must identify exact revision and matrix conditions.

## Task I04 — Position-guide qualification

**Files:** Use `docs/qa/position-guide/PG01.md`–`PG06.md`; prepare `docs/qa/position-guide/QUALIFICATION_REPORT.md` during QA; update `docs/EXERCISE_POSITION_GUIDE_TASKS.md` only from actual results. Existing source changes are scoped to reproduced defects, not a blanket reimplementation.

**Interfaces:** Consume existing PG01–PG05 behavior/tests. Produce 24 journey rows, eight recognition rows with individual trials, four standalone trials, accessibility and participant aggregates; share I11 evidence.

### Phase 1 — Implementation

- [x] Reconcile old PG01 two failures with current recovery/demo code; prepare retest IDs and all PG02–PG06 scenario links. Expand the full matrix as unrun, with environment fields ready.
- [x] Prepare deterministic stale-callback/countdown/help/permission/save fixtures where missing. No Gradle, emulator, participant or recognition trial execution in this phase.

### Phase 2 — QA

- [ ] Execute I04-01–I04-04 and PG01–PG06, SRC, CORE, APP, BUILD, DEVICE. Keep individual report totals and failures.
- [ ] Run physical recognition, ten-second readiness/ten-minute mixed-use and five-participant usability trials at spec thresholds. Missing hardware/participants prevents qualification; no fabricated pass. Return any counter/flow correction to its Implementation phase and rerun affected qualification.

## Task I03 — Production enforcement and reward loop

**Files:** Use `docs/T02_PHONE_TEST.md`, `T05_PHONE_TEST.md`, `T11_PHONE_TEST.md`, `T13_PHONE_TEST.md`, T16 in `exercise_gate_tasks.md`; create `AIT/monitoring/ProductionLoopTest.kt` for deterministic integration cases and `docs/qa/project-review/results/I03.md` during QA.

**Interfaces:** Consume existing BudgetEngine/challenge/session/repository contracts and I05–I09 failure seams; no debug reward bypass. Produce current intended-device acceptance with before/after persisted accounting assertions.

### Phase 1 — Implementation

- [x] Prepare two-app fixtures with per-app/combined limits, short sessions and eligible rewards for each exercise/mode. Prepare midnight, duplicate completion, access-loss and kill/rebind paths.
- [x] Add integration assertions for unchanged prompt-wait spending, exactly-once awards, no practice/exercise-only credit and preserved cursor/wallet across restart. Inventory required owner-controlled access steps and intended hardware; no device testing yet.

### Phase 2 — QA

- [ ] Execute I03-01–I03-06 on the production service, with CORE/APP/BUILD/DEVICE and physical Android 14 / API 34 phone acceptance. Use debug spike only for its separate T02 checks.
- [ ] Reconcile every T02/T05/T11/T13/T16 required path with evidence. Test both ceilings, eight earning paths, interruption/offline/midnight, overlay/queue/permission races and idle/special-window policy. Accept only with P0 gates met and no inferred downtime.

## Task F-A — Health and recovery center

**Files:** Create `A/monitoring/MonitoringHealthScreen.kt`, `MonitoringHealthPresentation.kt`, `AT/monitoring/MonitoringHealthPresentationTest.kt`, `AIT/monitoring/MonitoringHealthTest.kt`; modify `MonitoringSetup.kt`, `A/ui/ProductScreens.kt`, `SettingsScreen.kt`, `RES/values*/monitoring_strings.xml` in all three locales.

**Interfaces:** Consume I05 init state/retry, I06 phase/code and I07 snapshot. Proposed pure presentation maps these plus enabled/access/connection facts to copy/actions; never derives enforcement from permission alone.

### Phase 1 — Implementation

- [ ] Add phase-specific status/recovery UI, Unknown/last-confirmed age and optional since-process-start diagnostics. Connect startup retry versus normal reconciliation to correct state; keep Stop immediate and reachable.
- [ ] Write `permissionConnectedButFailedIsNotHealthy`, `retryTargetsFailedPhase`, `idleDoesNotPromiseLiveCounter`, `stopWinsOverSuspendedRetry`; prepare three-locale large-font semantics fixtures without running them.

### Phase 2 — QA

- [ ] Execute F-A-01–F-A-04, APP/BUILD and health instrumentation. Re-run I05 retry/disable and I11 affected rows using real access revocation/settings return.
- [ ] Confirm recovery works when the original fault clears and that displayed age/gap/phase never promises guaranteed enforcement.

## Task I12 — History performance, conditional query optimization

**Files:** Modify `A/ui/ProductData.kt`, `HistoryScreen.kt`, `C/data/DataRepository.kt`, `DurableRepository.kt`, `DurableStore.kt`, `P/data/RoomStore.kt`, `ExerciseGateDatabase.kt` only if measured trigger is met. Create `PIT/data/HistoryRangeTest.kt`, `AIT/ui/HistoryPerformanceTest.kt` and synthetic seed fixture.

**Interfaces:** Conditional proposed `visibleHistory(fromDay: String, throughDay: String): VisibleHistory`, inclusive ISO dates, preserves existing visibility rules; keep full-range API for explicit consumers. Batch target-set reads in Room, without per-row requests. Weekly comparison requests both windows, not just the visible week.

### Phase 1 — Implementation

- [x] Prepare 7-day baseline and 365/1,095-day × 20-package seed datasets including removed/hidden rows and coverage scope/zone differences; write visibility/equivalence assertions and timing/query-count harness. Do not profile yet.
- [x] No measured QA trigger has been returned, so this implementation phase ends with the harness only; query changes remain gated on I12 profiling.

### Phase 2 — QA

- [ ] Execute I12-01–I12-03 on matching small/large histories, record timing/memory/query count and compare to spec trigger. If optimization is warranted, return to Implementation then rerun equivalence/performance cases.
- [ ] Accept measured no-change outcome or verified improvement with no hidden-history/comparison regressions; CORE/BUILD/DEVICE cover any query change.

## Task F-B — Editable setup presets

**Files:** Create `C/settings/PlanPresets.kt`, `C/settings/SetupDraft.kt`, `CT/settings/PlanPresetsTest.kt`, `A/monitoring/SetupCoordinator.kt`, `AT/monitoring/SetupCoordinatorTest.kt`; modify `A/monitoring/MonitoringSetup.kt`, localized product/monitoring resources; create `AIT/monitoring/PlanPresetTest.kt`.

**Interfaces:** Proposed pure `PlanPresets.apply(preset: PresetId, draft: SetupDraft): SetupDraft`; shared draft model lives in `C/settings/` and Android coordinator in app (core never depends on app). `PresetId { SHORT, BALANCED }`; coordinator validates/saves draft through current repositories and returns read-back actual state on partial failure. Match existing preset spec PR1–PR3 values and override rules.

### Phase 1 — Implementation

- [ ] Reconcile current setup save/enable implementation; create the missing draft/coordinator boundary in the correct modules before wiring preview. Implement exact SHORT/BALANCED values from spec and explicit replacement confirmation.
- [ ] Preserve unavailable apps, per-app edits, rewards/camera/speech/monitoring and consumed/earned/session/cursor data. Preview performs zero writes; save and enable remain separate.
- [ ] Write `balancedTwoAppsShare3600000`, `previewWritesNothing`, `earningOffPreservesEarned`, `partialSaveReadsActualState`, `newAppSeedsWithoutOverwritingEditedApp`; prepare localized input/large-font fixtures.

### Phase 2 — QA

- [ ] Execute F-B-01–F-B-04, CORE/APP/BUILD and setup instrumentation with Persian/Arabic digits, cancelled replacement and partial failure.
- [ ] Re-run current-day exhausted-limit/active-offer cases and I11 setup rows. Record observed setup comprehension without claiming a numerical uplift.

## Task F-C — Voluntary break

**Files:** Create `A/breaks/BreakActivity.kt`, `BreakScreen.kt`, `BreakFlow.kt`, `AT/breaks/BreakFlowTest.kt`, `AIT/breaks/BreakJourneyTest.kt`; modify app manifest (private Activity), Home/blocker entry and three-locale resources.

**Interfaces:** Proposed `BreakFlow` uses monotonic time and `PAUSING/READY/FINISHED` plus validated optional target and fresh visit identity. It has no budget/challenge/session write dependency. Reuse explicit navigation safety.

### Phase 1 — Implementation

- [ ] Implement immediate Continue/Finish with optional 30,000 ms suggestion, target revalidation, Home fallback, lifecycle restart copy and failed-launch recovery. Timer completion only updates presentation.
- [ ] Write `continueAndFinishAtZero`, `expiryNeverNavigates`, `invalidTargetNeverLaunches`, `breakMakesZeroAccountingWrites`, `recreationStartsFreshSuggestion`; no test execution.

### Phase 2 — QA

- [ ] Execute F-C-01–F-C-03, APP/BUILD/device journey; compare wallets/session/challenge/activity before/after and check normal blocker can reappear.
- [ ] Verify TalkBack/mute/200% font, process recreation and no camera/notification/alarm permissions. Reassess optional pilot need.

## Task F-D — Goals and reflection

**Files:** Create `C/outcomes/WeeklyGoals.kt`, `CT/outcomes/WeeklyGoalsTest.kt`, `P/data/WeeklyGoalRepository.kt`, `PIT/data/WeeklyGoalRepositoryTest.kt`, `A/ui/WeeklyGoalContent.kt`; modify repository/Room query for visible completed-activity dates, History/Home/Settings and three-locale resources.

**Interfaces:** Existing proposal `WeeklyGoal(targetDays: Int, effectiveFromDay: String)` with target 1..7; pure progress consumes transactional visible COMPLETED challenge dates. Reflection choice Useful/Not useful/Skip keyed by ISO week and zone; retain latest 12 only. Clear-history integration clears reflection choices and visible contributions, retaining goal configuration.

### Phase 1 — Implementation

- [ ] Implement separate goal store and distinct-date completion query, Monday window/starts-today/Off semantics, unavailable state and immutable recorded-date handling.
- [ ] Add optional reflection with once-per-week suppression and bounded retention; defer intentional-finish goals as specified. No telemetry/reward/reminder coupling.
- [ ] Write `twoWorkoutsSameDayCountOnce`, `exerciseOnlyCountsButCancelledDoesNot`, `editStartsToday`, `clearDropsVisibleProgressAndReflection`, `zoneChangeKeepsRecordedDates`, `skipSuppressesWeekAndRetentionIs12`.

### Phase 2 — QA

- [ ] Execute F-D-01–F-D-04, CORE/BUILD and Room/DataStore/UI instrumentation with future/hidden/manual records and storage failure.
- [ ] Check locale/TalkBack/large-font and pilot opt-out. Verify goals/reflection never alter budget/reward state or imply usage completeness.

## Task F-E — Visible-history export

**Files:** Create `C/export/HistoryExport.kt`, `CT/export/HistoryExportTest.kt`, `A/export/HistoryExportCoordinator.kt`, `ExportScreen.kt`, `AT/export/HistoryExportCoordinatorTest.kt`, `AIT/export/HistoryExportTest.kt`; integrate History/Settings clear coordinator and localized strings.

**Interfaces:** Preserve exact E1–E3 format fields/order from the existing export spec, schema version 1. Coordinator owns staging/destination/copy ordering with history clear; serialization consumes only immutable `VisibleHistory`, never private ledger/cursor APIs.

### Phase 1 — Implementation

- [ ] Implement deterministic CSV/JSON serialization, exact integer preservation, missing-zone/coverage semantics and reversible formula escaping; add golden fixtures for empty/special/large-value snapshots.
- [ ] Add range/format review, SAF destination, final post-picker snapshot/confirmation and clear/export serialization. Cleanup staging on every exit/startup; no Room transaction held while copying.
- [ ] Write `csvHeadersMatchV1`, `longValuesRoundTrip`, `hiddenRowsNeverExport`, `clearDuringPickerInvalidatesSnapshot`, `clearWaitsForOwnedCopy`, `closeFailureNeverReportsSuccess`.

### Phase 2 — QA

- [ ] Execute F-E-01–F-E-04, CORE/APP/BUILD and local/provider device tests, including denial/full-storage/cancel/clear/process death.
- [ ] Compare export to visible history under identical snapshot/range, inspect files for excluded data and verify staging cleanup. Explain provider syncing and that clear cannot recall an already exported file.

## Task F-F — Honest widget

**Files:** Create `A/widget/StatusWidgetProvider.kt`, `WidgetConfigurationActivity.kt`, `WidgetSnapshot.kt`, `P/data/WidgetRepository.kt`, `AT/widget/WidgetSnapshotTest.kt`, `AIT/widget/StatusWidgetTest.kt`, `RES/xml/status_widget_info.xml` and responsive RemoteViews layouts; modify manifest and localized strings.

**Interfaces:** Independent configuration by validated widget ID, one to three selected apps; engine read-only budgets plus current access/connection facts. Implement existing V1–V3 timing/state/intent contract exactly; no new usage observer.

### Phase 1 — Implementation

- [ ] Implement timestamped snapshot/five-minute cutoff, currentness invalidation on day/zone/boot changes, explicit stale/unavailable/off states and no summed balance.
- [ ] Wire configuration/Refresh/Open intents, 30-minute periodic requests, 60-second spending throttle and 8-second async timeout; ensure broadcast completion on all paths. Do not treat host render timing as exact.
- [ ] Write `crossDayHidesNumbers`, `bootMismatchIsNotConnected`, `twoWidgetsKeepIndependentSelection`, `unknownIdRejected`, `asyncTimeoutFinishesBroadcast`, `throttleIs60000`.

### Phase 2 — QA

- [ ] Execute F-F-01–F-F-04, APP/BUILD/device launcher tests on API 28 and available newer/intended device. Include resize, RTL, TalkBack and delayed host refresh.
- [ ] Re-run disconnect/Stop/clear/completion updates; no widget click may enable monitoring, grant credit or launch a restricted target.

## Task F-G — Scheduled plans

**Files:** Create `C/settings/ScheduledPlans.kt`, `CT/settings/ScheduledPlansTest.kt`, `P/data/ScheduleRepository.kt`, `A/ui/ScheduledPlansScreen.kt`, `PIT/data/SchedulePolicyTest.kt`; modify engine effective-policy adapter, observed interval contract/controller, ProductData/setup/quotes and localized resources.

**Interfaces:** Use existing exact proposal `ScheduledPlans.resolve(base: AppPreferences, schedule: ScheduleConfig, instant: Instant, zone: ZoneId): EffectivePlan` and `transitionsBetween(start: Instant, end: Instant, zone: ZoneId, schedule: ScheduleConfig): List<Instant>`. Define immutable `ScheduleConfig` revision/rules and `EffectivePlan` effective prefs, active rule ID and next transition in the same pure file. Pass captured zone/epoch mapping/revision with observed intervals.

### Phase 1 — Implementation

- [ ] Record the demand/policy entry gate; implement SC1–SC4 validation, two-rule storage, preview/activation/manual fallback and one shared resolver. Do not overwrite manual preferences with effective values.
- [ ] Split only confidently observed intervals at rule/midnight boundaries using one revision per checkpoint; reject missing mappings as gaps. Ensure all usage/quote/completion paths use the same policy, while exercise-only bypasses schedule read failures.
- [ ] Write ordinary/overnight overlap, DST gap/overlap, zone/clock regression, `twoSecondIntervalSplitsAtTransition`, `editCannotRetroactivelyChangeQueuedInterval`, `lowerLimitBlocksWithoutReset`, `corruptSchedulePausesButStopWorks` and immutable-offer tests. No execution yet.

### Phase 2 — QA

- [ ] Execute F-G-01–F-G-05, CORE/APP/BUILD/DEVICE and the full accounting regressions. Use at least one DST-observing zone plus Asia/Tehran; compare independent expected segment totals.
- [ ] Re-run I03 affected limits/prompts/awards/midnight/unknown-gap cases. No scheduled activation may enable monitoring or refill a wallet. Retain the feature gate if calendar/accounting behavior is not accepted.

## Task M01 — Blocker launch helper

**Files:** Create `A/monitoring/BlockerNavigator.kt`, `AIT/monitoring/BlockerNavigationTest.kt`; modify only duplicated app/challenge launch setup in `MonitoringService.kt`.

**Interfaces:** Proposed `BlockerNavigator.open(destination: BlockerDestination): Unit`, explicit sealed Main/Challenge destination; callers validate current visible blocker immediately before invocation. The helper owns unique request codes and current API-specific creator/sender options, not accounting or overlay dismissal.

### Phase 1 — Implementation

- [ ] Extract duplicated setup without changing immutable/one-shot flags, explicit target/exercise extras or error recovery. Keep blocker retention until Activity resume.
- [ ] Write repeated-tap/destination/failed-send fixtures and API 34/35/36+ branch assertions. No background launch or automatic retry behavior is added.

### Phase 2 — QA

- [ ] Execute M01-01–M01-03 with BUILD/device branch tests and I03 app/challenge navigation regressions. Unsupported API branch remains pending, not inferred from compilation.
- [ ] Verify silent platform rejection leaves blocker/recovery usable; matching resumed Activity dismisses it without stacked screens.

## Task M02 — Conditional service extraction

**Files:** Reuse I05–I09 files; if justified create `A/monitoring/MonitoringCoordinator.kt` and adapt existing `MonitorDependencies.kt`, `SafetySnapshot.kt`, `MonitorDiagnostics.kt`; retain lifecycle/registration in `MonitoringService.kt` and existing regression tests.

**Interfaces:** Expose current serialized observation/session/configuration/stop operations through a coordinator without changing core contracts or queue capacity. Do not invent a second policy authority. Exact extracted signatures mirror the tested service seam rather than requiring a new event model.

### Phase 1 — Implementation

- [ ] Document remaining complexity after I05–I09. Either record why no further extraction is useful or move focused responsibilities without changing queue/generation/presentation/lease behavior.
- [ ] Keep all prior race tests and write only missing boundary-equivalence assertions. No Gradle/emulator verification in this phase.

### Phase 2 — QA

- [ ] Execute M02-01–M02-03; for changed code run APP/CORE/BUILD/DEVICE plus I03 lifecycle/overlay and I05–I09 regressions.
- [ ] Compare pre/post observable decisions, cleanup and accounting. A no-change result needs documented review evidence; refactored code needs fresh integrated QA.

## Task P01 — Pilot and usefulness evidence

**Files:** Create `docs/qa/project-review/PILOT_PROTOCOL.md` during preparation, `PILOT_REPORT.md` during QA; update optional-feature gate notes in this register only from actual local observations. No production analytics API.

**Interfaces:** Consume accepted I01–I11/F-A evidence and existing PG06 participant metrics. Produce aggregate attempts/outcomes/denominators, limitations and recorded user needs for F-C–F-G; no identifying participant or package event data.

### Phase 1 — Implementation

- [ ] Prepare opt-in protocol, exit/stop instructions, metric definitions and empty aggregate worksheet for setup, first challenge/mode, framing, recognition, interruptions, recovery and deliberate finishes. Separate availability gaps from observed outcomes.
- [ ] Define the optional-feature questions and report format; do not recruit/run trials or invent target improvements in preparation.

### Phase 2 — QA

- [ ] Execute P01-01–P01-03 after the baseline gate, using consenting participants/owner-led use. Preserve stopped attempts and record participant count, observation window and coverage limits.
- [ ] Report usability/recovery problems and concrete optional-feature needs. Do not infer health improvement/reduced use from incomplete data. Any product fix re-enters its implementation/QA cycle.

## Handoff and completion rules

Each Implementation handoff lists changed files, proposed versus existing interfaces, tests written/unrun, source checks actually run, exact QA scenario IDs and unresolved risks. Commit creation is optional under the active execution request; do not make unrequested commits while preparing this documentation.

Each QA report follows the scenario pack template and links exact evidence. Task acceptance requires its own required cases, not every optional feature. Qualified current-product claims require I01–I11 baseline gates (including intended hardware) and honest I10 provenance; optional P2/P3 features are not qualification prerequisites. Any proposed policy left gated remains explicitly gated. This planning delivery ends with these documents and does not start product implementation.
