# App Bug Fixes Implementation Plan

Execution update (2026-10-17): implementation for tasks 1–4 is present in the working tree. Core and source checks pass; Android build, Compose tests, device checks, and performance measurements are blocked in this environment. See [validation record](../../APP_BUG_FIX_VALIDATION_2026-10-17.md). Unchecked items remain open until their stated evidence is available.

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Use superpowers:subagent-driven-development only if the owner explicitly selects delegation. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Resolve BUG-01–BUG-03 from the 2026-10-17 app bug review with visible workout controls, accessible progress, and scalable history preparation/rendering.

**Architecture:** Separate camera ownership from workout presentation while preserving challenge authority. Add stable accessibility feedback, then index history once per snapshot and render days in one lazy list. The workout and history tracks are independently shippable; neither requires the full UX backlog.

**Tech Stack:** Existing Kotlin/JUnit 4, Compose Material 3, CameraX/MediaPipe, coroutines, and Android instrumentation. Add only BOM-aligned Compose test artifacts if absent.

**Spec:** [App bug fixes specification](../specs/2026-10-17-app-bug-fixes-design.md). Read it and the [source review](../../APP_BUG_REVIEW_2026-10-17.md) before execution.

## Global Constraints

- Keep minSdk 28, compileSdk/targetSdk 37, and JVM target 17. Use pinned dependencies; no production dependency upgrade is required.
- Only `BudgetEngine` grants rewards. Retain immutable challenge terms, unique award identities, fresh camera counters, and current interruption/local-midnight rules.
- Keep camera processing offline and local. Do not record or upload frames or add analytics.
- Support Persian (`values`), English (`values-en`), French (`values-fr`), RTL, localized numbers, and light/dark themes.
- Preserve hidden-history rules, unknown versus known-zero coverage, comparison blocking reasons, and removed-app package-name fallback.
- Keep unrelated working-tree files intact. Do not modify existing UX proposals, release artifacts, exported schemas, or task status as part of these fixes.

## Review Focus

- Camera callback arrives after exit/run replacement; Help or audio recomposition must not restart counting (task 1).
- Touch exploration changes mid-utterance or saving fails after the final repetition (task 2).
- Sparse, hidden, coverage-only, or out-of-order dates must preserve exact comparison/zero semantics (task 3).
- Refresh/history clear/day change races a background projection and must not restore an old list (task 4).
- Short landscape space, 200% text, French labels, and RTL can expose clipping unseen in default previews (tasks 1, 5).

## Execution boundaries and baseline

The owner authorized execution with “do it.” Implementation is underway inline in the current workspace; keep unrelated working-tree changes intact and record unavailable acceptance evidence honestly.

- [ ] Inspect `git status --short` and the relevant U1/U2/U4 implementations, if any. Reuse existing extracted camera/test/history components; do not duplicate the proposed names below. Preserve all unrelated changes.
- [ ] Capture the three report reproductions before editing. Record pending/blocked device checks honestly. Run the baseline commands listed in task 5 and save actual results, without assuming older README build blockers still apply.
- [ ] Complete tasks in order **1 → 2** and **3 → 4**, then task 5. Integrate shared resource/test dependency edits sequentially. Each task ends with focused verification and a scoped commit excluding unrelated files.

## File and responsibility map

Paths below are rooted at the repository. `appMain` means `app/src/main/java/com/example/scrollfit`; `appTest` means `app/src/test/java/com/example/scrollfit`; `appDevice` means `app/src/androidTest/java/com/example/scrollfit`; `coreMain`/`coreTest` mean `core/src/main/kotlin/com/example/scrollfit/core` / `core/src/test/kotlin/com/example/scrollfit/core`.

| Task | Create | Modify |
| --- | --- | --- |
| 1 | `appMain/camera/CameraViewport.kt`, `appMain/challenge/WorkoutContent.kt`, `appDevice/challenge/WorkoutLayoutTest.kt`, `appDevice/camera/CameraRunOwnershipTest.kt` | `CameraScreen.kt`, `ChallengeScreen.kt`, `ChallengeFlow.kt`, `app/build.gradle.kts`, `gradle/libs.versions.toml`, localized camera/challenge resources |
| 2 | `appMain/challenge/WorkoutAccessibility.kt`, `appMain/feedback/FeedbackAccessibility.kt`, `appTest/challenge/TrackingAnnouncementGateTest.kt`, `appTest/feedback/FeedbackAccessibilityTest.kt`, `appDevice/challenge/WorkoutAccessibilityTest.kt` | Workout content/flow/screen, `appMain/feedback/ChallengeAudio.kt`, localized feedback/challenge resources |
| 3 | `coreTest/outcomes/OutcomeIndexRegressionTest.kt` | `coreMain/outcomes/OutcomeAggregator.kt`, existing `OutcomeAggregatorTest.kt`; keep `CoverageAssessment` behavior |
| 4 | `appMain/ui/HistoryPresentation.kt`, `appTest/ui/HistoryPresentationTest.kt`, `appDevice/ui/HistoryLazyListTest.kt` | `appMain/ui/HistoryScreen.kt` |
| 5 | `docs/APP_BUG_FIX_VALIDATION_2026-10-17.md` | Only fixes required by failed acceptance checks, with focused reruns |

Resource paths are `app/src/main/res/{values,values-en,values-fr}/{camera_strings,challenge_strings,feedback_strings}.xml`. Add only the strings each task needs. Test fixture helpers stay in test source sets.

## Task 1: Extract the camera surface and keep exit visible — BUG-01

**Interfaces:**

- Produce `@Composable fun CameraViewport(exercise: Exercise, camera: CameraChoice, runKey: String, modifier: Modifier = Modifier, onState: (CameraPoseState) -> Unit)`. Match the U2 interface if already present. It creates/disposes the real session and fresh exercise counter; it does not persist or accept a challenge.
- For ownership tests, extract an internal implementation taking `CameraRunFactory`, with `fun open(preview: PreviewView, owner: LifecycleOwner, exercise: Exercise, camera: CameraChoice, onState: (CameraPoseState) -> Unit): AutoCloseable`. The public viewport supplies the production factory; the factory captures application context and creates the existing session/counter. Tests inject the fake directly into the internal implementation.
- Produce `internal data class WorkoutUiState(val repetitions: Int, val required: Int?, val status: CameraPoseStatus)` and `@Composable internal fun WorkoutContent(state: WorkoutUiState, speechEnabled: Boolean, speechLoaded: Boolean, onSpeechChanged: (Boolean) -> Unit, onEnd: () -> Unit, help: @Composable (() -> Unit) -> Unit, preview: @Composable (Modifier) -> Unit, modifier: Modifier = Modifier)`. The Help slot receives a close-workout callback; the host supplies exercise/privacy/trial copy.
- Produce `ChallengeFlow.cameraStateChanged(state: CameraPoseState): Unit`, preserving the current order of tracking, `session.accept(progress, timestamp)`, progress/phase update, and model/error interruption. Guard non-running/obsolete runs at the callback boundary; do not admit each progress twice.
- Preserve the public `ChallengeScreen` and `CameraScreen` entry signatures for callers. The challenge screen directly hosts `WorkoutContent` plus viewport; `CameraScreen` remains the standalone preference/permission/trial wrapper.

- [ ] Add test configuration, if absent: catalog entries for `androidx.compose.ui:ui-test-junit4` and `androidx.compose.ui:ui-test-manifest` without versions; add the Compose BOM to `androidTestImplementation`, UI test JUnit to `androidTestImplementation`, and test manifest to `debugImplementation`. Keep the current BOM. In `WorkoutLayoutTest`, inject a plain preview Box and test 320×480 and 640×320 dp at font scales 1 and 2, LTR/RTL, with French-length labels. Assert preview bounds ≥96×96 dp; count/status/audio/Help/End bounds inside the root; targets ≥48×48 dp; exactly one count; End invokes its callback once. Use a device surface large enough for the test root rather than silently constraining it.
- [ ] Run `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.example.scrollfit.challenge.WorkoutLayoutTest`. Expect missing content API or failed bounds before the fix; dependency/environment failure is blocked, not a red behavioral test.
- [ ] Extract existing `PreviewView`, required landmarks, counter creation, lifecycle effect, and optional debug overlay into `CameraViewport`. Keep the existing `CameraPoseSession` algorithm unchanged. Use updated callbacks without effect restart; key ownership by owner/exercise/camera/run key/permission/resumed state, and invalidate callbacks before closing. Make the challenge ID its run key. Keep standalone explicit lens/retry resets and saved last-exercise/camera behavior.
- [ ] Implement measured, bounded workout layouts per B1, with the Help sheet and persistent exit. Use the existing `feedback_enabled`, `challenge_close`, and `camera_cancel` strings where accurate; new helper/instruction labels go into all three locales. Move long text out of the active column and leave preparation disclosures intact. Keep current error, permission, interruption, and preference-save retry paths available.
- [ ] Add `CameraRunOwnershipTest` using a fake session factory at the ownership boundary: opening/closing Help and toggling audio leave create/close counts unchanged; run-key change closes once and creates once; disposed callback cannot update tracking/count/phase; one emitted progress is accepted once; exit closes once and dispatches interruption. Use a scoped internal factory seam, not a global mutable test hook. Run this connected class and rerun `WorkoutLayoutTest`; expect pass. Also run `./gradlew :app:assembleDebug :app:testDebugUnitTest`.
- [ ] Review camera extraction against the original file, then commit only task 1 files with `fix: keep workout camera and exit controls visible`.

## Task 2: Add progress semantics and controlled announcements — BUG-02

**Interfaces:**

- Add `internal enum class WorkoutTracking { INITIALIZING, SEARCHING, TRACKING, LOST, ERROR }` and `internal class TrackingAnnouncementGate` with `fun update(status: WorkoutTracking, nowMs: Long): WorkoutTracking?` and `fun reset(): Unit`. Return only a newly announceable category. SEARCHING/LOST share lost-position stability/deduplication. ERROR bypasses delay; exact error text is keyed separately by `CameraPoseStatus`.
- Add `internal class FeedbackAccessibility(private val applyEnabled: (Boolean) -> Unit)` with `fun update(speechEnabled: Boolean, touchExplorationEnabled: Boolean): Unit`. Its sole policy is effective audio enablement; never persist settings. Flow exposes `setTouchExplorationEnabled(value: Boolean): Unit` and routes both saved preference updates and accessibility changes through this policy.
- Add a lifecycle-aware Compose observer in `WorkoutAccessibility.kt` for accessibility/touch-exploration changes and a per-run gate/live-region presentation. Use the existing monotonic clock or an injected `nowMs` function in tests.

- [ ] Add `TrackingAnnouncementGateTest`: LOST at t=0/999 returns null, t=1000 returns LOST, t=2000 returns null; TRACKING then stable 1,000 ms returns TRACKING once; 500 ms flaps emit nothing; SEARCHING→LOST does not reset the shared loss timer; reset cancels pending/previous categories; ERROR emits immediately once. Add `FeedbackAccessibilityTest` asserting speech=true/touch=false enables; touch=true disables immediately; toggling the saved preference while touch=true stays disabled; touch=false restores only the current preference. Run `./gradlew :app:testDebugUnitTest --tests '*TrackingAnnouncementGateTest' --tests '*FeedbackAccessibilityTest'`; observe missing APIs/failing assertions.
- [ ] Add `WorkoutAccessibilityTest`: 3/10 exposes `ProgressBarRangeInfo(3f, 0f..10f, 0)` and localized state description; only one count node exists; a trial has count but no range; status has polite live-region semantics; audio has one labeled switch node and whole-row click. Use the Compose clock for 999/1,000 ms boundaries and unchanged recomposition. Completion fixture stays silent while saving/failed and exposes one committed success message per identity. Run the connected class and observe failures before implementing semantics.
- [ ] Implement B2: add count semantics to `WorkoutContent`; map states and drive the single stable status node with a cancellable per-run coroutine. Use a timer so a stable state can announce even without another frame. Exact error transitions announce immediately once. Add committed-result live-region semantics in `ChallengeScreen`; do not derive success from count. Preserve the actual awarded amount and exercise-only wording. Clear gate/jobs on run change, pause, and disposal.
- [ ] Implement accessibility observation and audio policy. On takeover, call the existing audio enablement path with false to stop playback/clear queued feedback; saved `speechEnabled` stays unchanged. On release, enable only future output. Remove observers with lifecycle/disposal. Add explanatory visual status for screen-reader-owned audio in all supported locales; retain current audio preference write/error behavior.
- [ ] Rerun the focused unit and connected tests. Run `./gradlew -p verification/core :core:test --tests '*ChallengeFeedbackTest'` for audio regressions. Manually verify mid-utterance takeover and late TTS/tone callback silence; JVM policy tests do not prove platform playback stops. Commit `fix: expose workout progress and stable accessibility feedback`.

## Task 3: Index outcome inputs without changing results — BUG-03 preparation

**Interfaces:** Preserve `OutcomeAggregator.summarize(history: VisibleHistory, today: LocalDate, currentZone: ZoneId): OutcomeSummary`. Add a private per-call index and memoized day-outcome lookup; no repository/schema/public-model changes.

- [ ] Add `OutcomeIndexRegressionTest`. Build fixed fixtures asserting exact `OutcomeSummary` fields for known zero/unknown, hidden usage/coverage marker, coverage-only day, today in progress, future day excluded, unsorted records, removed package, exercise-only mode, scope/zone mismatch, and inconsistent accounting. Retain existing tests for comparison-block priority and overflow. For a deterministic cost regression, wrap each input list in a test-only counting `AbstractList`, prepare 30 versus 1,095 dates (20 activities/day), reset counts before `summarize`, and assert each input list element is visited at most **4 times** regardless of date count. Count `get` calls, not elapsed time; expect current repeated scans to fail this bound.
- [ ] Run `./gradlew -p verification/core :core:test --tests '*OutcomeIndexRegressionTest' --tests '*OutcomeAggregatorTest' --tests '*CoverageAssessmentTest'`; confirm the scan-bound case fails on current code while characterization cases preserve current values.
- [ ] Group usage/activities/coverage by ISO day and retain first combined row per day in input order. Build hidden-day membership once. Compute each requested `DayOutcome` once, passing only its day's slices to `CoverageAssessment.assess` and aggregation. Route `zonesFor`, `scopesFor`, recent activity, daily and weekly comparisons through indexed slices/cache. Keep visible flags, hidden markers, source order, exact arithmetic, date filtering, and block precedence unchanged. Do not add persistent caches or alter comparison eligibility.
- [ ] Rerun the focused command, then `./gradlew -p verification/core :core:test`; expect all outcome, coverage, and existing core tests pass. Inspect that no helper still receives a full source list for each date. Commit `perf: index history inputs once for outcome summaries`.

## Task 4: Prepare snapshots off the UI thread and render days lazily — BUG-03 UI

**Interfaces:**

- Add `internal data class HistoryPresentation(val summary: OutcomeSummary, val activityDays: Set<String>, val appNames: Map<String, String>)` and `internal fun prepareHistory(snapshot: ProductSnapshot): HistoryPresentation`. It delegates to the unchanged aggregator and constructs shared lookup values once.
- Extract `@Composable internal fun HistoryContent(presentation: HistoryPresentation, loading: Boolean, onRefresh: () -> Unit, modifier: Modifier = Modifier)` as the stateless lazy-list surface. `DayCard` becomes `(day: DayOutcome, hasActivity: Boolean, appNames: Map<String, String>)`.
- `HistoryScreen()` remains the production entry and owns cancellable preparation. Keep `rememberProductData(history = true)` and existing full-history read; no changes to other `ProductPage` callers.

- [ ] Add `HistoryPresentationTest`: exact summary equals `OutcomeAggregator.summarize` for captured today/zone; activity-presence dates come from records even when repetitions are zero; app-name lookup and package fallback remain correct. Add `HistoryLazyListTest` with 1,095 dates/21,900 activities: oldest card is not composed initially (inspect the unmerged semantics tree without triggering scroll); `performScrollToKey("day:<oldest-date>")` reaches it; recent/older keys are unique; contents match the fixture; refresh callback is enabled only when not loading. Run focused app unit/connected classes and observe missing API or eager-composition failure.
- [ ] Implement `prepareHistory` and launch it on `Dispatchers.Default` keyed by snapshot identity/day/zone and `data.revision`. Cancel prior work and check captured revision plus source identity before publishing; do not start/publish preparation while `data.loading` or `data.failed`. Include loading/error changes in effect invalidation so refresh cancels an old projection before the replacement snapshot arrives. Render loading/failure/retry from the existing `ProductData` contract plus preparation failure; never convert a failed read into zero data. Key list state to the published snapshot so replacing/clearing records cannot restore an invalid old index.
- [ ] Replace History's outer `ProductPage` with one `LazyColumn` filling host constraints, with 20 dp content padding and 12 dp spacing. Render summary/headings as bounded items; recent and older dates each use `items(..., key = { "day:${it.day}" }, contentType = { "day" })`. Pass the shared map and `day.day.toString() in activityDays` to cards. Remove full-history access/filtering from `DayCard` and avoid recomputing app names inside item bodies. Retain existing summaries, detail, coverage labels, comparisons, and refresh placement.
- [ ] Add suspended-projection tests through `@Composable internal fun HistoryHost(data: ProductData, prepare: suspend (ProductSnapshot) -> HistoryPresentation, modifier: Modifier = Modifier)`: refresh/clear replaces the snapshot while old work is blocked; releasing it must not publish old dates. Also test revision/loading invalidation before the new snapshot arrives. Repeat for local-day/zone replacement and leaving the screen. Recomposition of one snapshot invokes preparation once and preserves position; replacement resets list state. `HistoryScreen` supplies `prepareHistory` through this internal seam, with no production global hook. Run `HistoryPresentationTest`, `HistoryLazyListTest`, and `./gradlew :app:assembleDebug :app:lintDebug`; expect pass.
- [ ] Commit `perf: render history lazily from prepared snapshots`.

## Task 5: Verify the original findings and record closure

**Interfaces:** Produce `docs/APP_BUG_FIX_VALIDATION_2026-10-17.md` with BUG ID, build/device/OS, fixture, locale/theme/font/viewport, steps, expected/actual, result, and evidence location. Keep source review status intact; link the closure report from the implementation change description.

- [ ] Run from repository root and record exit codes/results:

  ```bash
  python3 verification/check_source.py
  ./gradlew -p verification/core :core:test
  ./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug
  ./gradlew :app:connectedDebugAndroidTest
  ```

  Expected: source checks, core/app unit tests, compilation, lint, and app instrumentation pass. If a structural source assertion encodes the old layout, update only that assertion to the new contract with review; do not weaken behavioral invariants. Preserve pre-existing owner edits to `verification/check_source.py`. Missing SDK/dependency/device access is blocked verification, not a product failure or test pass.

- [ ] Run B1 on a real camera: all four exercises, front/rear, main route and `ChallengeActivity`, earning/exercise-only/trial modes, Help, End, Back, background, permission denial, camera/model error, and retry. Confirm no duplicate reward/progress delivery and no camera retained after exit. Cover 320×480 and 640×320 content dp, 100%/200% fonts, Persian/English/French, RTL, light/dark; API 28 and API 37 where available. Document any unavailable combination rather than claiming full coverage.
- [ ] Run B2 with TalkBack focus on End: inspect current count on demand, sustain tracking loss/recovery, trigger a camera error, finish with a successful save and a failed save, toggle touch exploration mid-speech/tone, and switch app speech on/off. Record delivered announcements, absence of frame spam/competing app audio, and unchanged saved speech preference. Do not automate privacy-invasive frame/audio capture.
- [ ] Run B3's same-device before/after measurement: 1,095 dates and 21,900 activities, five cold History entries and five full scrolls per variant. Separate snapshot-read, projection, and first-content timings; report median, peak memory, initially composed card count, and slow-frame fraction against the recorded device frame deadline. Use test-only fixtures/probes or profiler traces. Confirm the oldest card and cleared-history state remain correct. Explain regressions before closure; keep repository paging outside this patch.
- [ ] Review every B1–B3 acceptance item against evidence. Mark each BUG fixed only after its own functional and required device gates pass; otherwise mark implementation complete / verification blocked or failing precisely. Review the scoped diff and commit the validation record with `docs: record app bug fix validation`. Do not mark the wider U2/U4 backlog complete.

## Handoff

Tasks 1–4 have implementation changes and focused test coverage in the working tree. The core JVM suite and structural/resource checks pass. Android compilation and all Compose/device acceptance remain unverified because Gradle cannot start reliably in the host environment and no adb/device is available. Do not call BUG-01–BUG-03 fully closed until the remaining validation gates pass; see the validation record for exact results and limits.
