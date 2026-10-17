# U2 Recoverable Workout Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans or, if explicitly selected by the owner, superpowers:subagent-driven-development to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ship a readable workout, honest offer, optional camera practice, and safe same-screen restart.

**Architecture:** Preserve engine/session authority; add exact offer projections and read-only recovery. Extract camera preview ownership and compose a state-based workout UI around it.

**Tech Stack:** Existing Kotlin/JUnit 4, Compose Material 3, CameraX/MediaPipe, Room, DataStore; U1 Compose UI test setup.

**Spec:** [U2 design](../specs/2026-10-17-ux-workout-design.md). Depends on U1; U3 later supplies the guided-setup destination.

## Global Constraints

- Keep minSdk 28, compileSdk/targetSdk 37, and JVM target 17. Use the repository's pinned dependencies; no production-library upgrades are part of these plans.
- All persisted accounting durations are integer milliseconds. Only `BudgetEngine` grants rewards and spends usage; UI projections never change that authority.
- Monitoring requires explicit enablement. Stop remains available with invalid drafts or missing access. Permission connectivity is not proof of enforcement.
- Retain immutable challenge terms, unique award identities, non-restorable interrupted progress, protected-app filtering, local-midnight expiry, and hidden-history safeguards.
- Support Persian (`values`), English (`values-en`), and French (`values-fr`), RTL, localized numbers, ASCII/Persian/Arabic digit entry, and system light/dark themes. Gregorian calendar remains the display policy for this package.
- Keep processing offline and local. Do not collect camera frames, other-app content, or raw app-use histories as product events. Feature discovery uses optional pilot observation and manual aggregate notes.

## Review Focus

- Completion commits immediately before pause/disposal or a recovery read fails (task 2).
- Old progress callbacks arrive after new intent/restart (tasks 2–3).
- Wallet addition overflows Long or wallet already covers shared remainder (task 1).
- Practice camera rebind leaks repetitions into rewarded counting (task 3).
- TalkBack turns on mid-utterance, or preview consumes control space at 200% font (task 4).

### Task 1: Transactional reward disclosure

**Files:** Modify `core/src/main/kotlin/com/example/scrollfit/core/budget/BudgetModels.kt`, `BudgetEngine.kt`, and `core/src/test/kotlin/com/example/scrollfit/core/budget/BudgetEngineTest.kt`.

**Interfaces:** Extend `RewardOffer` with `usableBeforeMs: Long = 0` and `usableAfterMs: Long = 0`. Existing `offer(packageName: String?, exercise: Exercise, mode: ChallengeMode): OfferResult` and `start(id: String, displayed: RewardOffer): StartResult` suspend signatures remain. Both new values participate in quote equality. Callers consume U1 `ProductFormatters`.

- [ ] Add engine tests asserting reward=120,000/shared=30,000/wallet=0 → before=0/after=30,000; wallet=40,000 → before=after=30,000; award cap 50,000 → credit=50,000; saturated wallet addition never overflows; exercise-only produces all zero money/time fields. Assert a wallet change between display and start returns `OfferChanged`.

  First red test uses the existing `BudgetEngineTest` fixture:

  ```kotlin
  @Test fun creditCanExceedTimeUsableUnderSharedLimit() = run {
      setup(base = 0, cap = 200_000)
      prefs = prefs.copy(combinedCeilingMs = 30_000,
          rewardRules = prefs.rewardRules.mapValues { RewardRule(it.value.repetitions, 120) })
      val quote = offer()
      assertEquals(120_000L, quote.rewardMs)
      assertEquals(0L, quote.usableBeforeMs)
      assertEquals(30_000L, quote.usableAfterMs)
  }
  ```

- [ ] Run `./gradlew -p verification/core :core:test --tests '*BudgetEngineTest'`; observe missing fields or failed values.
- [ ] Compute projection in `offerFor` from the same budget snapshot, saturating every nonnegative addition. Preserve cap clipping and existing completion validation.
- [ ] Rerun the command; expect old and new engine cases passing. Review/commit: `feat: disclose credit and usable time separately`.

### Task 2: Settled-result recovery and fresh attempts

**Files:** Modify `core/src/main/kotlin/com/example/scrollfit/core/budget/BudgetEngine.kt`, existing engine/session tests, and `app/src/main/java/com/example/scrollfit/challenge/ChallengeFlow.kt`; create `app/src/main/java/com/example/scrollfit/challenge/ChallengeRecovery.kt` and `app/src/test/java/com/example/scrollfit/challenge/ChallengeRecoveryTest.kt`.

**Interfaces:** Engine produces `suspend fun savedCompletion(id: String): CompletionResult?`. Add `RecoveryState { CHECKING, SAVED, NOT_SAVED, READ_FAILED }` and `InterruptionReason` from W2. Extract `ChallengeRecovery` with injected `cancel: suspend (String) -> Unit` and `lookup: suspend (String) -> CompletionResult?`; `suspend fun settle(id: String, completionJob: Job?): CompletionResult?` waits, cancels if active, then reads. Flow exposes `activeChallengeId: String?`, `recover(id: String)`, `interrupt(reason: InterruptionReason)`, `checkSavedResult()`, `prepareRestart(chooseExercise: Boolean = false)`; retain no-arg cancellation adapters for existing activity hooks until task 4.

- [ ] Add core tests that saved lookup returns an existing award or completed exercise-only result without writes, and still returns committed status after history deletion without recreating activity. Add app coordinator tests with a suspended completion: commit before pause → Saved; cancellation before commit → Not saved; failed lookup → Read failed; old-generation callback cannot reset current state; repeated restart generates distinct IDs and zero counts.
- [ ] Run focused engine/session tests and `./gradlew :app:testDebugUnitTest --tests '*ChallengeRecoveryTest'`; expect missing API/state failures.
- [ ] Implement read-only lookup using `store.transaction`, with no call to `complete`. Track completion Job and active identity in the flow; synchronously invalidate eligibility/audio on interrupt. Clear prior session/busy/error/count only after a successful Not-saved settlement. For Saved, preserve the committed result; for Read failed, keep identity and block restart.
- [ ] Rerun the focused tests. Use real durable in-memory transaction fixtures for core cases and suspend fakes with `runBlocking` for the coordinator; no timing sleeps. Review/commit: `fix: recover settled challenge results before restarting`.

### Task 3: Isolated positioning/practice and flexible camera surface

**Files:** Create `core/src/main/kotlin/com/example/scrollfit/core/pose/TrackingReadiness.kt` and matching core test; create `app/src/main/java/com/example/scrollfit/camera/CameraViewport.kt`; modify `CameraScreen.kt` in that directory and `app/src/main/java/com/example/scrollfit/challenge/ChallengeFlow.kt`; create `app/src/androidTest/java/com/example/scrollfit/challenge/PracticeIsolationTest.kt`.

**Interfaces:** `TrackingReadiness.update(timestampMs: Long, nowMs: Long, tracking: Boolean): Boolean`, `reset()`. Extract `CameraViewport(exercise: Exercise, camera: CameraChoice, runKey: String, modifier: Modifier = Modifier, onState: (CameraPoseState) -> Unit)`; it owns/disposes one `CameraPoseSession` and never persists preferences or invokes an engine. Practice/position/workout pass distinct run keys. Existing standalone `CameraScreen` remains an instruction/control wrapper for debug callers.

- [ ] Test readiness: valid samples at 0/250/500/750/1000 with nowMs equal to timestamp → ready; gap 251, age 251, future sample, false tracking, timestamp regression, or reset → false. Repeated/old callbacks do not count toward readiness. Run focused `TrackingReadinessTest` and observe failure.
- [ ] Implement readiness and extract viewport, retaining landmark sets, alternate push-up side, preview projection, permission handling, lifecycle disposal, and main-thread callbacks. Construct a new exercise counter for each run key. Separate all preference writes from the viewport.
- [ ] Add fake-camera integration tests: three practice reps produce zero persistence calls; rewarded start rebinds/reset count to zero; a late practice/old-exercise callback is ignored; camera switches in Position reset readiness; Exercise has no switch action. Run the connected test for `com.example.scrollfit.challenge.PracticeIsolationTest` and focused core tests; expect pass.
- [ ] Verify real front/rear preview and all four exercises on a phone before claiming camera parity. Review/commit: `feat: add isolated camera positioning and practice`.

### Task 4: Unified workout stages, actions and semantics

**Files:** Modify `app/src/main/java/com/example/scrollfit/challenge/ChallengeScreen.kt`, `ChallengeFlow.kt`, `ChallengeActivity.kt`; create `WorkoutStage.kt`, `WorkoutContent.kt`, `WorkoutAccessibility.kt` in the challenge directory; modify `app/src/main/java/com/example/scrollfit/ui/ProductScreens.kt`, `ScrollFitApp.kt`, `app/src/main/java/com/example/scrollfit/monitoring/BudgetOverlay.kt`, and `app/src/main/java/com/example/scrollfit/feedback/ChallengeAudio.kt`; create `app/src/androidTest/java/com/example/scrollfit/challenge/WorkoutScreenTest.kt`; update the three `ux_strings.xml` locale bundles; create `docs/UX_WORKOUT_PHONE_TEST.md`.

**Interfaces:** `WorkoutStage` from W1. Add `onChooseApps: () -> Unit` and `onExerciseOnly: (Exercise) -> Unit` callbacks to `ChallengeScreen`, implemented in both entry points. Preserve active ID with `rememberSaveable`; restoration invokes recovery, never progress restoration. Add `ChallengeAudio.setAccessibilitySpeechOwned(owned: Boolean)` which stops/suppresses app speech without changing stored enabled preference. `WorkoutContent` consumes stage/state plus callbacks and a preview slot, allowing tests without a camera.

- [ ] Write UI tests for one progress node, End reachable at 320 dp/200% font, selection-group roles, full-row mute, no reward copy in exercise-only, refusal → exercise-only preserving exercise, missing targets → Choose apps, 120,000/30,000 disclosure, and zero-increment warning. Add recovery tests for Checking/Read-failed disabled restart, Saved success amount, Start-again → terms before Start, and denied camera permission. Run `WorkoutScreenTest`; observe failures before wiring.
- [ ] Implement W1–W5 using tasks 1–3. Reserve control space before preview; `keepScreenOn` is true only for resumed Exercise and restored on disposal. Store no images/progress. Main Choose-apps navigates to setup; the private activity launches the main app's internal setup destination. Deep-link input must not enable monitoring automatically.
- [ ] Add accessibility tests for one coherent progress value, deduplicated stable state announcements, mute visibility and TalkBack ownership switching mid-run. Keep app TTS rep messages off live regions. Add launch-failure test keeping saved result and Done. Run connected tests and record actual TalkBack speech-on/off behavior manually.
- [ ] Run `python3 verification/check_source.py`, full core tests, `./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug`, and connected app/platform tests. Record interruption races, force-stop/restore, midnight, all four exercises/cameras, both entry points, and narrow/large/RTL layout in the phone checklist.
- [ ] Review/commit: `feat: ship accessible recoverable workout journey`.

## Exit

Every W1–W5 criterion must have test or recorded phone evidence. Do not describe unmeasured framing/recognition or contrast as validated. Pass U3 the setup callbacks and U4 the unchanged completion/history semantics.
