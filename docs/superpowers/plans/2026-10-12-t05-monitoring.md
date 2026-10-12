# T05 Monitoring Implementation Plan

> **For agentic workers:** Use superpowers:executing-plans for inline execution.

**Goal:** Connect foreground monitoring to T04 budgets and generate a commit message.

**Architecture:** A serialized JVM controller owns eligible intervals and block
decisions; a system-bound Android service adapts package/screen/access signals
and accessibility overlays. DataStore persists explicit enablement.

**Tech Stack:** Existing Kotlin, Room, DataStore, coroutines, Compose and JUnit.

**Spec:** `docs/T05_MONITORING.md`, T05 in `exercise_gate_tasks.md`.

## Global Constraints

- No content retrieval, unrelated gestures, broad app visibility or backend.
- No new dependency versions or permission to commit.
- Preserve counters and all existing changes.
- T02 intended-device evidence is pending; no enforcement reliability claim.

## Review Focus

- Rapid switching and duplicate timestamps: one eligible interval, one owner.
- Midnight, time-zone changes and delayed callbacks: correct dated rows or gaps.
- Permission/storage/overlay failure: pause and truthful health, without resets.
- Critical system surfaces: filter both configuration and service events.
- Process restart/reboot: same-boot cursor and persistent budgets, unknown downtime excluded.

### Task 1: Domain controller

Files: `core/.../usage/MonitoringController.kt`, `MonitorOwnership.kt`, tests,
and `core/.../budget/BudgetEngine.kt` monitor spending adapter.

Interfaces: `MonitoringController.observe(MonitorSignal): MonitorDecision`,
`BudgetEngine.spendObserved(UsageInterval)`, lease acquisition/release.

- [x] Write tests for eligibility, switching, health, caps, dedup, restart,
  rollover, clock jumps, delayed samples, full rollback and owner conflicts.
- [x] Observe failing tests, implement controller and run actual core suite.

### Task 2: Android integration and delivery

Files: main `monitoring/` service/adapters/setup screen, manifest/resources,
MainActivity/UI hooks, debug spike lease integration, preferences and clocks.

- [x] Wire service and metadata-only reconciliation; bounded queue/checkpoints.
- [x] Add persistent enablement, safe target validation, distinct overlays and setup.
- [x] Run JVM suite; attempt debug/release build, lint and instrumentation.
- [x] Review changes and fix important findings with regression coverage.
- [x] Record evidence/limits, phone steps, README/status and `T05_COMMIT_MESSAGE.txt`.


Execution record (2026-10-13, Asia/Tehran):

- Implemented inline in the current checkout, preserving existing work. No new
  worktree, git commit or direct index manipulation was performed.
- Added controller/ownership and observed-midnight spending; first suite 64/64.
- Independent reviewer found four important adapter/rollover failure paths. Fixed
  retained window state, immediate presentation revisions, latched preference
  readiness and guarded UI collection, plus original-day spending during storage
  rollover and explicit rejected-segment gaps. Added seven further regressions:
  final suite **71/71**, with no failures/errors/skips.
- Midnight regression ran RED→GREEN. An isolated fault probe confirmed four
  failure-state assertions detect deliberately unsafe variants; workspace stayed
  unchanged. No Android callback test or compilation is inferred from that probe.
- Android debug/release/lint/instrumentation attempt exited 1 before compilation.
  Evidence, delivery message and device limits are in `docs/T05_MONITORING.md`.
- T02 intended-device feasibility, OEM focus/rebind behavior, camera integration
  and session prompts remain explicitly pending/reserved for their required gates.
