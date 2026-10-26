# ScrollFit — project review, improvements and feature priorities

Review date: **2026-10-26**, Asia/Tehran. Source snapshot: `f4a10bf` (`Improve onboarding and monitoring setup UX`).

## Recommendation

**Prioritize reliable monitoring, camera ownership and qualification before expanding the feature set.** The project already contains most of its intended core experience. The main uncertainty is whether those pieces work together reliably on the intended physical Android 14 phone.

**`MonitoringService.kt` has critical importance: 5/5.** It connects Android foreground observations to spending, blocking, short-session decisions and coverage history. A failure can leave selected apps usable, misrepresent monitoring health, or make outcome comparisons unavailable. Keep it small, observable and covered by lifecycle/failure tests. Its presence and enabled permission alone do not establish that enforcement works.

This is an assessment and proposed backlog, not an implementation plan or authorization to execute old task documents. Product code and existing task statuses were left unchanged.

## Review scope and confidence

Read the README, product brief, ordered T01–T16 tasks, current task status, earlier product/UX reviews, UX implementation package, positioning-guide register and QA records. Inspected the production monitoring package and its core controller/presentation/readiness contracts, budget and persistence integration, manifest/accessibility/backup settings, camera estimator/session, guide UI, product data loading and outcome/history implementation. This is a focused source review, not an exhaustive audit of every Kotlin file.

Evidence labels used below:

- **Source-confirmed:** visible directly in the checked-out code or configuration.
- **Recorded result:** a previous test/QA result inspected during this review; not rerun here.
- **Risk:** a plausible failure path needing a targeted reproduction.
- **Proposal:** a suggested improvement or feature; no claim of validated demand.

Fresh verification during this review: `python3 verification/check_source.py` passed. The pinned MediaPipe `tasks-core:1.0.0` AAR was inspected with `javap`; its bitmap container's `close()` calls `Bitmap.recycle()`, and `MPImage.close()` closes containers when its reference count reaches zero. No Gradle build, unit suite, emulator action or physical-device test was run in this documentation task. Existing untracked APK and evidence files were preserved.

## What is already present

| Area | Implementation observed | Remaining confidence gap |
| --- | --- | --- |
| Architecture | `app` contains UI/Android integration; `core` contains policy, counters and contracts; `platform` contains Room, DataStore and pose/camera adapters | Integration behavior needs current end-to-end evidence |
| Budgets and rewards | Per-app base/earned budgets, combined ceiling, caps, immutable challenge terms, award identities and persisted interval cursor | Full current JVM suite and target-device accounting checks |
| Monitoring | Package-only accessibility events, bounded reconciliation, overlay, explicit enable/disable and health state | Production enforcement, permission revocation, rebinding and OEM lifecycle qualification |
| Exercise | Four counters, bundled offline model, serial latest-frame processing and lifecycle ownership | Native inference ownership issue and physical recognition trials |
| Workout guidance | Learn/Position flow, framing/readiness, explicit countdown/start, help, demo controls and recovery copy | PG01 requalification; PG02–PG05 QA; PG06 qualification |
| Product UI | Onboarding/setup, dashboard, limits, settings, exercise-only and challenge flows; Persian/English/French resources | Actual small-screen, large-font, TalkBack and locale acceptance |
| Outcomes | Coverage-aware daily/weekly summaries, comparison gates, indexed aggregation and lazy history UI | Current data accuracy, coverage usability and longer-history device checks |

Preserve the existing strengths: accounting authority stays in `BudgetEngine`; awards are durable and unique; unknown usage is not invented; interrupted challenges cannot restore unfinished repetitions; camera processing stays offline; critical navigation surfaces remain excluded; exercise-only mode remains useful without credit.

### Documentation and evidence disagree in several places

1. **README delivery claims are stale.** It says T14–T16 are unimplemented and no APK/SDK exists. `TASK_STATUS.md` records T14/T15 implementation and a later successful debug build/lint plus partial emulator verification. SDK files and APK artifacts are present in this checkout. Artifact existence does not prove that the APK matches the current revision, is appropriately signed, or passes acceptance.
2. **The latest recorded full core test gate is broken.** `core/build.gradle.kts` declares only JUnit as a test dependency, while `CoverageRecorderTest.kt` imports `kotlinx.coroutines`. The task status and PG01 QA record an unfiltered compilation failure. The PG01 counter-only run excluded that file and passed 114 tests; it is not a passing full suite.
3. **Inspect the complete instrumentation result.** The saved 2026-10-23 platform XML reports 22 executed tests, 21 passes and one native pose-test failure. Successful persistence cases are useful evidence, but they do not make the entire run green. A native process crash also prevents treating this as proof that every discovered pose test completed.
4. **PG01 fixes exist but remain unqualified.** The QA baseline is 7 PASS / 2 FAIL. Its post-run note and current UI contain recovery copy and paused-by-default movement controls. Re-run the affected cases instead of filing those already-remediated omissions as new bugs.
5. **Build configuration differs from written assumptions.** `app/build.gradle.kts` has minSdk 28, compileSdk 37, targetSdk 35 and versionName `0.2.0-t02`; several plans say compile/target 37. Reconcile the intended target and artifact identity explicitly. This review does not recommend an automatic SDK/dependency upgrade.

Source records: [README](../README.md), [task status](../TASK_STATUS.md), [PG01 QA](qa/position-guide/PG01.md), [guide tasks](EXERCISE_POSITION_GUIDE_TASKS.md), [app build configuration](../app/build.gradle.kts), [core test configuration](../core/build.gradle.kts).

## How importance is determined

| Priority | Meaning | Scheduling rule |
| --- | --- | --- |
| **P0 — critical** | Core promise cannot be qualified, or a known failure blocks a required verification gate | Resolve before claiming a qualified candidate |
| **P1 — high** | Recovery, privacy, accessibility or maintainability issue with substantial user impact | Address before a wider pilot; qualify any remaining limitation |
| **P2 — medium** | Reduces setup friction or improves ongoing usefulness | Consider after P0/P1 evidence is satisfactory |
| **P3 — low / later** | Optional convenience or complex expansion with uncertain demand | Validate demand first |

Priority is delivery importance, not proof of a reproduced runtime defect. Effort estimates are relative: **S** = localized change, **M** = several components, **L** = substantial lifecycle/policy or device work. They are not time commitments. Existing task IDs and their historical priorities remain unchanged.

## Prioritized engineering and product improvements

| ID | Importance / effort | Finding and reason | Proposed action and completion evidence |
| --- | --- | --- | --- |
| I01 | **P0 / S** | **Source-confirmed + recorded result:** coroutine imports are absent from the core test classpath. This blocks confidence in monitoring, awards and new guide logic together. | Add the appropriate coroutine test dependency using the existing version catalog; run the unfiltered core suite with no excluded tests. Record revision, totals and command. Keep `core` independent of Android. |
| I02 | **P0 / M** | **Source-confirmed ownership conflict + recorded native crash:** estimator documentation says the caller owns the bitmap, but closing its MediaPipe wrapper recycles that bitmap in the pinned dependency. The offline test reuses it. | Define one ownership contract across estimator, caller and tests. Verify repeated inference without passing recycled images, rotation/crop aliasing, error cleanup and close during shutdown. Rerun the native pose suite before physical recognition trials. See detail below. |
| I03 | **P0 / L** | **Qualification gap:** production monitoring and the full exercise-to-award-to-reblock loop lack intended-device evidence. The earlier accessibility enable action was blocked in that session; it is not an enforcement pass. | Complete the existing T02/T05/T11/T13/T16 acceptance paths with the owner granting required access. Include two apps, both block reasons, all exercises, both earning paths, duplicate callbacks, kill/restart, permission loss, midnight and offline operation. Label unsupported/unrun cases honestly. |
| I04 | **P0 / M–L** | **Recorded gap:** PG01 needs retesting, PG02–PG05 QA is not run, and PG06 is TODO. Source implementation is ahead of qualification. | Reuse the existing QA scenarios and PG06 matrix. Cover front/rear cameras, all entry paths, practice/countdown isolation, stale callbacks, large text and TalkBack. Preserve the documented recognition/usability targets as acceptance targets, not measured results. |
| I05 | **P1 / M** | **Source-confirmed recovery gap:** initialization can return before `ready = true`; configuration and reconciliation entry points both reject `!ready`. A transient boot/storage/metadata initialization failure can require an external service rebind. | Provide an explicit, serialized initialization retry and a distinct initializing/failed/ready state. Keep one worker and one lease. Test fail-once/succeed-on-retry, repeated taps and disable/cleanup during initialization; do not infer downtime usage. |
| I06 | **P1 / M** | **Source-confirmed exception gaps; runtime impact unverified:** startup listener/receiver registration and reconciliation are not protected like the observation loop. Many other failures discard their exception and report `STORAGE` even for unrelated causes. | Guard each Android boundary, preserve cancellation and track registration for cleanup. Classify failures by phase. Test an injected non-security reconciliation exception, registration failure and partial cleanup. Publish a stopped/gap state instead of leaving a stale healthy state. |
| I07 | **P1 / M** | **Source-confirmed diagnostic limitation:** coarse process-local status does not explain when failure occurred, how stale the last observation is, or what recovery was attempted. This makes OEM and queue failures hard to reproduce. | Add bounded local diagnostics: failure phase/code, monotonic observation age, queue high-water mark, dropped observation count and aggregate processing latency. No screen content, raw event history or camera data. A user-visible support summary can follow; keep it optional and local. |
| I08 | **P1 / S–M** | **Source-confirmed backup ambiguity:** `allowBackup=true` plus template rules does not explicitly exclude usage history, selected packages, preferences or device-bound cursor state from backup/transfer. | Decide and document the backup policy. Specify cloud and device-transfer rules; test restore so monitoring needs fresh readiness and device-bound cursors cannot suppress usage on another device. Preserve current-device accounting. See privacy detail below. |
| I09 | **P1 / M** | **Source-confirmed metadata work on callbacks; performance risk:** package/role queries run from the main-thread service path, and role rechecks occur for eligible window events even when the package did not change. | Measure callback/queue/storage latency first. Move suitable metadata work off the main thread and use an invalidated safety snapshot while preserving immediate overlay dismissal. Stress rapid app changes and slow package metadata. Do not call this an ANR until measured. |
| I10 | **P1 / S–M** | **Source-confirmed documentation drift:** old build blockers, test totals and UI descriptions obscure the actual next task. | Make README a concise current overview; retain historical evidence under dated task sections. Add a current verification matrix with revision, artifact hash, device/API, command and result. Match SDK/version documentation to the chosen configuration. |
| I11 | **P1 / M** | **Qualification gap:** semantic/layout tests exist, but broad accessibility and physical use are not established. | Qualify reachable Start/End/Stop/recovery actions at 320 dp and 200% font, short landscape, Persian RTL, English/French, TalkBack and mute. Check the blocker as well as Compose screens. Include the new guide controls. |
| I12 | **P2 / M** | **Source-confirmed scaling opportunity:** history is already lazy and prepared off-main, but product loading still obtains all visible history and the Room adapter resolves coverage target sets per row. | Profile a realistic long history before introducing paging or range queries. If needed, fetch a bounded date range and batch coverage targets; retain history-deletion cutoffs and comparison semantics. Avoid repeating the already-completed lazy-list rewrite. |

### I02: bitmap ownership deserves a focused correction

Relevant files: [MediaPipePoseEstimator.kt](../platform/src/main/kotlin/com/example/scrollfit/platform/pose/MediaPipePoseEstimator.kt), [OfflinePoseTest.kt](../platform/src/androidTest/kotlin/com/example/scrollfit/platform/pose/OfflinePoseTest.kt), [CameraPoseSession.kt](../platform/src/main/kotlin/com/example/scrollfit/platform/pose/CameraPoseSession.kt).

The estimator builds an `MPImage` from the supplied bitmap, runs inference and closes the image in `finally`. Inspection of the actual cached `tasks-core-1.0.0.aar` confirms that closing the final image reference recycles the wrapped bitmap. The test then passes that same bitmap to a second inference. The stored native log reports recycled-bitmap warnings, `AndroidBitmap_lockPixels()` failure and SIGSEGV. This is strong evidence of an ownership mismatch in the failing test path; a fresh native rerun is still required to prove the correction.

The camera path uses fresh frames, so the recorded test failure is not proof that every live workout crashes. However, it reads dimensions after estimation and also recycles aliases during cleanup. Resolve ownership consistently rather than merely deleting the second test call. Compare a consuming-frame contract with an estimator-owned copy; choose based on caller needs and measured allocation cost. Keep image dimensions available before disposal and avoid native calls on recycled input.

The ownership behavior also appears in the [MediaPipe upstream bitmap container](https://github.com/google-ai-edge/mediapipe/blob/master/mediapipe/java/com/google/mediapipe/framework/image/BitmapImageContainer.java); the local pinned artifact was checked so this finding does not rely on upstream `master` matching the dependency.

### I08: clarify what “local” means

The product brief excludes accounts, backend and remote camera upload. The manifest has no app network permission, which is a useful boundary, but Android system backup is separate from app network calls. Android's default backup coverage includes much internal application data and databases. Template rules do not establish an explicit local-only storage policy. [Android Auto Backup documentation](https://developer.android.com/identity/data/autobackup).

Choose whether selected apps and activity/usage history may leave the device through system backup. A reasonable default for this personal offline app is to exclude sensitive usage/accounting and device-bound state, then offer deliberate visible-history export later. If preferences or history are restored, document their scope and require fresh monitoring readiness. This is a proposed policy choice, not evidence that any data has actually been uploaded.

## `MonitoringService.kt`: importance and detailed recommendations

Source: [MonitoringService.kt](../app/src/main/java/com/example/scrollfit/monitoring/MonitoringService.kt).

| Responsibility | Source location | Importance and consequence |
| --- | --- | --- |
| Connect service, initialize persistence/controller, process a bounded command queue | `onServiceConnected`, lines 77–154 | **Critical:** no ready worker means no production observation processing |
| Observe package transitions and reconcile foreground metadata | `onAccessibilityEvent`, lines 156–163; `requestReconciliation`, 282–292 | **Critical:** determines which app may be charged or blocked |
| Carry generation checks through budget/coverage work | Observation loop, lines 118–149 | **Critical:** protects against stale asynchronous results after state changes |
| Execute Continue/Finish with a current prompt and budget revalidation | Lines 220–260 | **Critical:** session actions must neither spend prompt time nor manufacture credit |
| Dismiss/present blocker, schedule ticks and coverage checkpoints | Lines 323–365 | **Critical:** directly affects access and the evidence behind outcomes |
| Handle failures, permission/lifecycle changes and cleanup | Lines 56–75, 265–279, 389–419 | **Critical:** failure must remain recoverable without blocking safe navigation |
| Launch challenge or app only from an explicit visible-blocker tap | Lines 171–218 | **High:** user navigation crosses Android background-launch rules |

### Keep these safeguards

- Package metadata only; accessibility configuration disables content retrieval and gestures. The exported service is protected by `BIND_ACCESSIBILITY_SERVICE`; this is not an unprotected exported-service finding.
- Serialized observation/session commands, bounded queue, generation invalidation and presentation tickets.
- Immediate dismissal on safe/unknown/access-loss transitions, independent of suspended storage reads.
- Shared ownership with the debug spike; conservative protected-app filtering.
- Monotonic observations plus wall-clock/day checks; no charging of guessed process downtime.
- Durable budget/session decisions in the engine rather than independent overlay counters.
- Blocker retained until the destination Activity actually resumes after a launch request.

Android owns accessibility-service lifecycle. A connected accessibility service is an integration mechanism, not an availability guarantee; retain explicit health and recovery states. [AccessibilityService reference](https://developer.android.com/reference/android/accessibilityservice/AccessibilityService).

### Highest-value service changes

1. **P1: make initialization recoverable (I05).** The boot-identity catch returns at line 108, and subsequent initialization exceptions exit the worker. `reloadConfiguration()` and `requestReconciliation()` return while not ready. Separate startup retry from ordinary configuration reload; expose a working recovery action for that state.
2. **P1: close exception boundaries and preserve failure context (I06–I07).** `requestReconciliation()` launches a child with no general exception boundary; the helper catches `SecurityException` only. `SupervisorJob` isolates sibling cancellation but does not convert an uncaught exception into a usable monitoring status. Record phase/category without private payloads. Runtime failure from these paths remains a test hypothesis.
3. **P1: test service orchestration directly.** Existing JVM tests verify controller, presentation and readiness contracts. Add injectable time, access, foreground lookup, overlay and persistence seams around the orchestration so race/failure cases do not depend entirely on granting device accessibility access. Complement these tests with actual Android service/overlay tests.
4. **P1: measure metadata and persistence latency (I09).** A 64-item queue and 5-second maximum observation age are deliberate correctness controls. Add deterministic slow-operation/overflow scenarios and confirm that failures stop charging, hide overlays and invalidate coverage. Keep the queue bounded; increasing it alone can create older decisions.
5. **P2: extract responsibilities after regression coverage.** Extract a serialized monitoring coordinator, foreground/safety adapter, blocker navigation helper and diagnostic recorder if complexity warrants it. Leave Android lifecycle ownership in the service. The existing `core` controller is already a useful boundary; avoid duplicating it or rewriting working accounting policy.

Challenge and app launches duplicate API-level `PendingIntent` setup. Consolidation is a small maintainability improvement after navigation tests. Preserve explicit user taps, immutable one-shot intents and the existing failure behavior; validate Android 14 and the configured newer API branches. Platform rules vary with Android/target versions. [Android background activity launch guidance](https://developer.android.com/guide/components/activities/secure-bal).

### Required service acceptance scenarios

| Scenario | Expected behavior |
| --- | --- |
| Initialization fails once; user retries | Fresh readiness, one worker/lease, no reset or backfilled downtime |
| Storage suspends, then target changes to Settings/Home/own UI | Blocker dismisses immediately; late result cannot restore it |
| Usage Access revoked during a suspended operation | Stop eligibility, invalidate coverage, expose recovery, preserve counters |
| More than queue capacity or stale observations | Report queue failure/gap, discard uncertain observations, permit fresh recovery |
| Continue/Finish races configuration, midnight or budget exhaustion | Revalidate current session/day/budget; no extra award or free continuation |
| Overlay removal fails | No duplicate overlay; ownership follows the existing removal-safety contract |
| Process kill/rebind/reboot | Persisted counters survive; cursor identity is appropriate; unknown downtime is not charged |
| Screen lock/unlock, keyboard/dialogs, split-screen and PiP | Match the documented foreground-only policy; critical surfaces remain usable |
| Long idle/non-target period | Coverage checkpoints and freshness remain truthful without implying live countdowns |

Existing acceptance owners: [T05 phone checks](T05_PHONE_TEST.md), [T13 phone checks](T13_PHONE_TEST.md), [T16 tasks](../exercise_gate_tasks.md), [PG06 QA](qa/position-guide/PG06.md).

## Suggested features, ranked after reliability work

These are proposals. Reuse the existing specs where available and reconcile them with the current code before implementation. A written feature spec is not evidence that the feature has shipped.

| Rank / feature | Importance / effort | User benefit and initial scope | Acceptance / dependency |
| --- | --- | --- | --- |
| F-A: Monitoring health and recovery center | **P1 / M** | Expand existing health UI with last confirmed observation age, specific failure reason and a recovery action that actually retries the failed phase | I05–I07; separate enabled permission, connected service and fresh processing. No claim of guaranteed enforcement |
| F-B: Editable setup presets | **P2 / M** | Preview a simple starting plan before choosing it: per-app allowance, combined ceiling, session duration and earning rules | Preserve consumption/credit; apply through existing validation/save flow. [Existing preset spec](superpowers/specs/2026-10-17-feature-presets-design.md) |
| F-C: On-demand intentional break | **P2 / M** | A non-camera pause with Continue/Finish when exercise or camera use is inconvenient | No exercise credit; explicit interaction; preserve the budget. Validate usefulness in a pilot. [Existing break spec](superpowers/specs/2026-10-17-feature-break-design.md) |
| F-D: Personal weekly goals and reflection | **P2 / M** | Optional activity-day or intentional-finish goal plus one weekly reflection; exercise-only activity counts independently | Reliable records first; missing monitoring remains unknown, and reward difficulty never escalates automatically. [Existing goals spec](superpowers/specs/2026-10-17-feature-goals-design.md) |
| F-E: Deliberate local history export | **P3 / M** | User-triggered CSV/JSON containing visible usage/activity, units, time zone and coverage metadata | Exclude hidden history, device cursor internals and all camera data. [Existing export spec](superpowers/specs/2026-10-17-feature-export-design.md) |
| F-F: Home-screen status widget | **P3 / M** | Remaining usable time snapshot and a shortcut into ScrollFit | Show timestamp/stale/disconnected states; do not display an old snapshot as a live counter. [Existing widget spec](superpowers/specs/2026-10-17-feature-widget-design.md) |
| F-G: Scheduled plans | **P3 / L** | Optional workday/evening rules for users who demonstrate this need | Resolve overlaps, midnight/time-zone transitions and existing-credit policy; no silent enablement or accounting reset. [Existing schedule spec](superpowers/specs/2026-10-17-feature-schedules-design.md) |

The camera coach, explicit positioning/start flow, workout help, accessible status, lazy history and coverage-aware weekly comparison are already present in source. Their next work is qualification or incremental refinement, not rebuilding them as new features. Avoid adding accounts, cloud video, leaderboards, automatic exercise escalation or elaborate anti-cheat: they do not address the current reliability gaps and conflict with parts of the existing scope.

## Recommended delivery sequence

1. **Restore trustworthy evidence:** I01 test classpath, I02 ownership correction, I10 current status/artifact identity. Exit: full automated results recorded without exclusions or stale “all pass” claims.
2. **Stabilize service recovery:** I05–I09 with focused orchestration tests. Exit: injected failures leave truthful health, safe navigation and intact accounting; recovery succeeds when the fault clears.
3. **Qualify the existing product:** I03/I04/I11 using T16 and PG06, then a small owner-led pilot. Exit: required physical-device results and named remaining limitations; P0 failures stay open.
4. **Reduce setup friction:** F-A then F-B. Judge success by whether a user can configure, understand and recover the app without assistance.
5. **Validate optional usefulness:** test F-C/F-D with voluntary local pilot notes. Pursue export/widget/schedules only when a concrete need justifies them.

Measure first successful setup, first completed challenge by exercise/mode, time to stable framing, mismatched/extra repetition counts, interrupted attempts, time to recovery and chosen session finishes. Keep observations optional and aggregate. Do not use awarded minutes or increased restricted-app use as the main success metric, and do not infer improved health or reduced use from incomplete records.

## Evidence and limits for the next reviewer

- Snapshot and current dirty-state inventory were inspected; only this Markdown document was added by this review.
- Fresh source/resource checks passed; they do not prove Kotlin compilation, persistence, accessibility, native inference or enforcement.
- Historical platform result inspected: `platform/build/outputs/androidTest-results/connected/debug/TEST-Medium_Phone_API_36.1(AVD) - 16.xml` — 22 executed / 1 failure, dated 2026-10-23. Associated `OfflinePoseTest` log contains the recycled-bitmap/native crash evidence. Build outputs are local and may be regenerated.
- Pinned `com.google.mediapipe:tasks-core:1.0.0` bytecode confirms bitmap recycling on final image closure. A fresh native regression run is required after any correction.
- No fresh unit/instrumentation totals, release readiness, recognition accuracy, battery performance or physical-device enforcement result is claimed here.
- Historical execution instructions embedded in task/brief files were reviewed as project context; this request was for assessment and a prioritized document.
