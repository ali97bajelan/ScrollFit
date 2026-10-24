# PG04 Guided Workout Flow Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task; use superpowers:subagent-driven-development only if explicitly selected. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Start only explicitly after positioning and exclude all practice/countdown state from saved workouts.

**Architecture:** A testable app journey coordinator owns tutorial/countdown state and delegates challenge creation/cancellation to existing flow/engine authority. Each positioning or active phase has a distinct camera generation and counter.

**Tech Stack:** Existing Kotlin/coroutines/JUnit 4, Compose, CameraViewport factory seam and BudgetEngine/ChallengeSession.

**Spec:** [PG04](../specs/2026-10-24-pg04-workout-flow-design.md); [shared requirements](../../EXERCISE_POSITION_GUIDE_TASKS.md).

## Global Constraints

- Keep pinned dependencies, minSdk 28, target/compileSdk 37 and JVM target 17.
- Keep immutable offers, fresh UUIDs, existing interruption/day policies and exactly-once rewards.
- Learn/Position/countdown reps never enter ChallengeSession. No unfinished progress restoration.
- Ready requires 1,000 ms stable observations with age/gap <=250 ms; watchdog interval 100 ms. Countdown lasts 3,000 ms after Started.

## Review Focus

- Engine start suspends while camera/day/navigation changes (step 1).
- Duplicate Start and delayed position callbacks can cross into a fresh active run (steps 1, 4).
- Countdown is canceled with storage failure before returning to Position (step 1).
- Completion wins a race with End/background and must stay committed once (step 4).
- Exercise-only/blocker/standalone trial must follow their own persistence rules (step 4).

## Task: Coordinator and entry-point integration

**Files:**

- Create `app/src/main/java/com/example/scrollfit/challenge/GuideJourney.kt`.
- Modify `ChallengeFlow.kt`, `ChallengeScreen.kt`, `ChallengeActivity.kt` in the same directory.
- Modify `app/src/main/java/com/example/scrollfit/camera/CameraScreen.kt`, `CameraViewport.kt`; adjust `app/src/main/java/com/example/scrollfit/ui/ScrollFitApp.kt` only for necessary guide-route wiring.
- Create `app/src/test/java/com/example/scrollfit/challenge/GuideJourneyTest.kt`.
- Create `app/src/androidTest/java/com/example/scrollfit/challenge/GuideIsolationTest.kt`.
- Extend `core/src/test/kotlin/com/example/scrollfit/core/challenge/ChallengeSessionTest.kt` only if a domain regression needs coverage; keep core production policy unchanged.

**Interfaces:** Consumes PG02 readiness and PG03 content. Produces PG04 `GuideStage`/`GuideJourney` methods and `ChallengeFlow.activateStartedChallenge(id: String)`. Coordinator constructor injects `MonotonicClock`, `start: suspend () -> StartResult`, `cancel: suspend (String) -> Unit`, `activate: (String) -> Unit`; flow retains offer lookup/UUID creation and generation validation. Trial mode uses the same stage reducer with a no-persistence start adapter and never calls engine start.

- [ ] **1. Add deterministic journey tests.** Use a fake clock and suspended delegates (no sleeps). Assert: Learn/Position cause zero start calls; readiness alone never calls start; three trial reps + countdown leave active repetitions zero; rapid double Start calls start once; Started at t=200 enters countdown until t=3200; OfferChanged returns to terms; invalid framing cancels the row; failed cancel blocks Start until successful retry; background/day change while start is suspended cancels any late Started result; old-run events cannot change current readiness/count; countdown end with stale pose cannot activate. Also assert changed framingResetSerial with a latest FRAMED issue resets Position readiness, invalidates suspended Starting (canceling a late row), and cancels Countdown. Name each case after the transition.
- [ ] **2. Run red.** `./gradlew :app:testDebugUnitTest --tests '*GuideJourneyTest' --console=plain`. Expect coordinator/API failures; configuration failure is BLOCKED rather than a red behavioral result.
- [ ] **3. Implement journey and flow split.** Use a separate journey generation from speech preference revisions. Guard every suspended result. Keep start locked through Starting/Countdown. Poll readiness freshness without replaying observations; track PG02 continuity serial per run and compare against the serial captured on Start before countdown/activation. On activation dispose positioning camera before opening fresh challenge UUID run and beginning rep feedback. Both ID and Exercise stage guard `cameraStateChanged`; start rep audio only at activation. Keep cancellation pending/error recovery and commit-wins semantics.
- [ ] **4. Integrate/test entry paths.** Wire main advance, blocker, exercise-only and trial hosts. Fake-camera instrumentation asserts zero position/countdown persistence, fresh counter on activation, no late events after disposal, lens/exercise changes reset readiness, no active camera selector, Help preserves active ID, system background cancels, and End remains available during starting/countdown/saving. Inject repository failures and rapid completion/exit; inspect exactly one award/activity for committed success and no rows for trials.
- [ ] **5. Verify.** Focused app unit tests, full core suite, `./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug --console=plain`, then connected app tests. Execute [PG04 QA](../../qa/position-guide/PG04.md). Observe each mode on a physical device before accepting lifecycle behavior.
- [ ] **6. Review/checkpoint.** Record changed state transitions and evidence. Suggested commit `feat: isolate positioning and countdown from workout counting` under session authorization.

**Handoff:** PG05 routes guide/countdown prompts through existing audio ownership. PG06 validates real camera/session behavior.
