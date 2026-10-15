# T11 Challenge Lifecycle Implementation Plan

> **For agentic workers:** Use superpowers:executing-plans inline for this plan.

**Goal:** Connect all four counters to both earning paths and exercise-only activity.

**Architecture:** Reuse BudgetEngine snapshots and ledger. ChallengeSession owns
fresh cumulative progress and synchronous invalidation. Shared Compose preparation
and camera components serve dashboard and explicit blocker entry.

**Tech Stack:** Existing Kotlin/JVM, Compose, CameraX, Room and DataStore; no new dependency.

**Spec:** `docs/superpowers/specs/2026-10-15-t11-challenges-design.md`

## Global Constraints

- All four exercises and both earning modes are required.
- Persian RTL product text; English delivery documentation.
- No saved camera frames, runtime downloads, credit bypass or schema changes.
- Existing workspace/index changes are preserved. No git commit is requested.
- T11 only; Android and intended-device evidence must be reported honestly.

## Review Focus

- Background/cancel during a suspended transaction must invalidate unfinished credit.
- Restarted camera counts must not be combined with an old challenge's progress.
- Failed activity writes must roll back wallet/status and allow a same-ID retry.
- Changed offers must be redisplayed and explicitly accepted before start.
- Reopening/navigation must never manufacture progress or another award.

### Task 1: Core lifecycle and atomic activity

Files: `core/.../challenge/ChallengeSession.kt`, `core/.../budget/BudgetEngine.kt`,
`core/src/test/.../challenge/ChallengeSessionTest.kt`, test MemoryStore.

Interfaces: consume `RewardOffer`, `Challenge`, `CountingProgress`, `BudgetEngine`.
Produce `ChallengeSession.accept(progress, timestampMs)`, `interrupt()`,
`complete()` and `checkDay()`; completion returns existing `CompletionResult`.

- [x] Restore temporary Java/Kotlin runner and run baseline core tests: 203 passed.
- [x] Add lifecycle/accounting tests. Reconstructed old engine produces the expected
  atomic-activity failure (204 run, 1 failure); no preimplementation lifecycle RED is claimed.
- [x] Implement atomic activity plus guard checks and session progress/duration.
- [x] Run all core tests: 221 passed; duplicates, rollover, recovery and rollback covered.

### Task 2: Shared challenge screen and camera integration

Files: `app/.../challenge/ChallengeScreen.kt`, `ChallengeActivity.kt`,
`app/.../camera/CameraScreen.kt`, `ScrollFitApplication.kt`, new strings/manifest.

Interfaces: screen consumes optional target, mode, initial exercise and cancel.
Camera consumes optional challenge, reports progress and restart/pause/failure.

- [x] Implement target-before-exercise selection, current offers and remembered choices.
- [x] Start snapshots with UUID after permission; reject stale starts on pause/dispose.
- [x] Feed cumulative fresh detector counts; stop camera on completion/interruption.
- [x] Display persisted success/retry/refusal and explicit safe target return.
- [x] Check back, lens/retry, rotation and lifecycle source paths; device checks pending.

### Task 3: Dashboard/blocker entry and delivery evidence

Files: `ScrollFitApp.kt`, `BudgetOverlay.kt`, `MonitoringService.kt`, README,
TASK_STATUS and T11 documentation/phone checklist/commit-message text.

- [x] Add dashboard earning and exercise-only entry.
- [x] Fetch/display all four blocker offers, reject stale async results, launch only on click.
- [ ] Attempt Android build/lint/instrumentation and run final JVM/source checks.
- [x] Independent review of accounting/lifecycle/launch races; preparation-resume and
  Activity-stacking findings fixed and rechecked by reviewer.
- [ ] Record exact evidence and pending physical-device checks; generate commit text.

## Execution ledger

- Pre-flight: Task 2 consumes Task 1 session interfaces; Task 3 consumes Task 2 Activity.
  Both use existing policy result types and share no second wallet implementation.
- Ruling: execute inline without approval pauses, per project execution contract.
- Ruling: work in place; .git is read-only and pre-existing edits must be retained.
- Ruling: missing temporary tools made initial test execution unavailable. Used the
  project's exact Kotlin 2.4.10 compiler/JUnit 4.13.2 with temporary Temurin JRE21;
  real source/test compilation targets JVM17. No Android proof is inferred.
- Ruling: final daily cleanup can suspend, so policy now checks completion eligibility
  again after cleanup before returning its transaction block; two regressions cover it.
- Status: implementation and core verification finished; Android attempt and delivery
  documentation are being finalized.
