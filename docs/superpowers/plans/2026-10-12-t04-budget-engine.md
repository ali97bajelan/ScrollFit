# T04 Budget Engine Implementation Plan

> **For agentic workers:** Use superpowers:executing-plans for inline implementation.

**Goal:** Implement T04 budgets/rewards and generate its commit message.

**Architecture:** Android-free policy over the existing transactional store, with
a durable monotonic cursor and Room v1→v2 migration. Android event integration
remains T05.

**Tech Stack:** Existing Kotlin/JVM, Room, DataStore and JUnit dependencies.

**Spec:** `docs/T04_BUDGET_ENGINE.md`, plus the T04 requirements in the task list.

## Global Constraints

- Integer milliseconds, injectable wall and monotonic clocks.
- No dependency changes, no backend, no camera media.
- Preserve existing changes and current-day counters; generate a message only.
- Old-day reward challenges cancel, with no new-day award.

## Review Focus

- Overlapping two-app callbacks: each elapsed segment charged once globally.
- Settings/re-add/history edits: retained daily counters and interval identity.
- Midnight/date reversal: terminal cancellation and earlier-day counters retained.
- Reduced cap after start: no silent reward change and no over-cap award.
- Storage failure: cursor, session, wallets and ledger roll back together.

### Task 1: Domain policy and tests

Files: `core/.../budget/BudgetEngine.kt`, `BudgetModels.kt`,
`core/.../data/Models.kt`, `DurableStore.kt`, `DataRepository.kt`, `DurableRepository.kt`,
`core/src/test/.../budget/BudgetEngineTest.kt`, reusable `MemoryStore.kt`.

Interfaces: `BudgetEngine.budget(packageName)`, `spend(UsageInterval)`,
`offer(packageName?, exercise, mode)`, `start(id, RewardOffer)`,
`complete(id, repetitions)`; transaction cursor get/put and dated cancellation.

- [x] Add tests for base-first boundaries, two-app overlap, partial rewards,
  snapshot edits, duplicate callbacks, caps, exercise-only, interruptions,
  midnight/date reversal, restart/reboot, history/re-add and rollback.
- [x] Run tests before implementation and retain the actual failure.
- [x] Implement policy and serialized cursor/ledger updates; run the core suite.

### Task 2: Android persistence and delivery

Files: platform Room entities/DAO/store/database/persistence owner; Android
`BudgetPersistenceTest.kt`; README, TASK_STATUS and T04 delivery docs.

- [x] Wire cursor/cancellation to Room and explicit preserving 1→2 migration.
- [x] Expose recovered engine factory with caller-supplied stable boot identity.
- [x] Prepare reopen/concurrency/migration tests; attempt Android build/lint/tests.
- [x] Review policy and migration; record actual test outcomes and unresolved gates.
- [x] Generate `docs/T04_COMMIT_MESSAGE.txt`; do not create a git commit.


Execution record:

- Implemented inline in the existing checkout to retain the uncommitted T03 work;
  no worktree, git commit, or index mutation was performed by the implementation.
- Initial pre-implementation test attempt stopped at the sandbox socket/IP service,
  rather than producing a policy-test failure. Subsequent outside-sandbox tests ran.
  Four meaningful midnight regressions were separately observed RED→GREEN.
- Independent reviewer identified stale read dates and raw repository policy bypass.
  Fixed current-day read reconciliation and a delegated safe data facade. Added
  late rollback and concurrent duplicate-callback tests. Final suite: **49/49 pass**.
- Final verification and limits are recorded in `docs/T04_BUDGET_ENGINE.md`.
  Android build/configuration exits 1; genuine Room migration verification remains
  pending. The six prepared Android tests were not counted as passes.
