# P2 Weekly Activity Goals Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans or, if explicitly selected by the owner, superpowers:subagent-driven-development to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let users optionally choose and inspect a weekly committed-activity-day goal.

**Architecture:** A separate preferences store owns the goal; a read-only repository query supplies visible committed days; a pure week projection drives UI.

**Tech Stack:** Existing Kotlin/JUnit 4, Room, DataStore and Compose; no new runtime dependencies.

**Spec:** [P2 design](../specs/2026-10-17-feature-goals-design.md). Complete the entry gate before task 1; record pilot evidence in `docs/UX_GOALS_PHONE_TEST.md`.

## Global Constraints

- Keep minSdk 28, compileSdk/targetSdk 37, and JVM target 17. Use the repository's pinned dependencies; no production-library upgrades are part of these plans.
- All persisted accounting durations are integer milliseconds. Only `BudgetEngine` grants rewards and spends usage; UI projections never change that authority.
- Monitoring requires explicit enablement. Stop remains available with invalid drafts or missing access. Permission connectivity is not proof of enforcement.
- Retain immutable challenge terms, unique award identities, non-restorable interrupted progress, protected-app filtering, local-midnight expiry, and hidden-history safeguards.
- Support Persian (`values`), English (`values-en`), and French (`values-fr`), RTL, localized numbers, ASCII/Persian/Arabic digit entry, and system light/dark themes. Gregorian calendar remains the display policy for this package.
- Keep processing offline and local. Do not collect camera frames, other-app content, or raw app-use histories as product events. Feature discovery uses optional pilot observation and manual aggregate notes.

## Review Focus

- Monday/week/year boundary and goal edits today (task 1).
- Repeated completion, practice, cancelled/manual or future rows (task 2).
- Clear history must remove progress without touching awards (task 2).
- Goal-store/read failure must not become zero progress (task 3).
- Zone changes must not rewrite historical activity days (tasks 1, 3).

### Task 1: Goal model and durable preferences

**Files:** Create `core/src/main/kotlin/com/example/scrollfit/core/outcomes/WeeklyGoal.kt`, matching `WeeklyGoalTest.kt`; create `platform/src/main/kotlin/com/example/scrollfit/platform/data/WeeklyGoalRepository.kt` and `platform/src/androidTest/kotlin/com/example/scrollfit/platform/data/WeeklyGoalPreferencesTest.kt`.

**Interfaces:** `WeeklyGoal(targetDays: Int, effectiveFromDay: String)`; `WeeklyGoalProgress(goal: WeeklyGoal, weekStart: LocalDate, weekEnd: LocalDate, completedDays: Set<LocalDate>)`; `WeeklyGoals.progress(goal: WeeklyGoal, today: LocalDate, committedDays: Set<LocalDate>): WeeklyGoalProgress`. Repository exposes `values: Flow<WeeklyGoal?>`, `suspend fun set(targetDays: Int?, today: LocalDate)`; a separate `weekly-goal-preferences` DataStore, absent key means Off.

- [ ] Add assertions for 1/7 valid, 0/8 invalid, Sunday→Monday rollover, December→January, midweek edit excluding previous dates, duplicate/future-date filtering, above-target actual count, and unchanged stored days when today/zone context changes. Verify preference upgrade defaults Off and set/clear survives reopening.

  First red test `midweekGoalCountsOnlyDatesOnOrAfterActivation`:

  ```kotlin
  val today = LocalDate.of(2026, 10, 17)
  val progress = WeeklyGoals.progress(WeeklyGoal(3, "2026-10-15"), today,
      setOf(today.minusDays(3), today.minusDays(2), today, today.plusDays(1)))
  assertEquals(setOf(today.minusDays(2), today), progress.completedDays)
  assertEquals(LocalDate.of(2026, 10, 12), progress.weekStart)
  ```

- [ ] Run focused core and connected preference tests; observe missing API failures. Implement G1/G2 week math and non-destructive DataStore encoding.
- [ ] Rerun focused tests; expect pass. Review/commit: `feat: define optional weekly activity-day goals`.

### Task 2: Committed visible activity-day query

**Files:** Modify `DataRepository.kt`, `DurableRepository.kt`, `DurableStore.kt` under `core/src/main/kotlin/com/example/scrollfit/core/data`, the core test `MemoryStore.kt`, and platform `ExerciseGateDao.kt`/`RoomStore.kt`; create `core/src/test/kotlin/com/example/scrollfit/core/outcomes/GoalActivityTest.kt` and `platform/src/androidTest/kotlin/com/example/scrollfit/platform/data/GoalActivityPersistenceTest.kt`.

**Interfaces:** `suspend fun completedActivityDays(fromDay: String, throughDay: String): Set<String>` returns distinct visible activity dates joined to completed challenge identities. Mirror it in DataTransaction so filtering is SQL/transactional rather than a full-history UI scan.

- [ ] Test two completions/day→one; both earning and exercise-only qualify; practice/no challenge/cancelled rows→none; clearHistory→none while retained award/counters remain. Run the focused core/platform tests and observe missing method failures.
- [ ] Implement bounded join and in-memory fixture semantics. No schema change or raw award scan is needed.
- [ ] Rerun focused tests and existing history-deletion/budget tests; expect pass. Review/commit: `feat: read visible committed activity days`.

### Task 3: Goal choice and truthful progress

**Files:** Create `app/src/main/java/com/example/scrollfit/ui/WeeklyGoalCard.kt` and `app/src/androidTest/java/com/example/scrollfit/ui/WeeklyGoalScreenTest.kt`; modify `ProductScreens.kt`, `HistoryScreen.kt`, `SettingsScreen.kt`, `WeeklyReview.kt` in UI; expose goal repository from `platform/src/main/kotlin/com/example/scrollfit/platform/data/ExerciseGatePersistence.kt`; update all three `ux_strings.xml`; create `docs/UX_GOALS_PHONE_TEST.md`.

**Interfaces:** `WeeklyGoalCard(goal: WeeklyGoal?, progress: WeeklyGoalProgress?, loading: Boolean, failed: Boolean, onSet: (Int?) -> Unit, onRetry: () -> Unit)` consumes tasks 1–2. Refresh on completion, resume, midnight/zone change and history clear; keep error distinct from empty data.

- [ ] Add UI tests for optional Off, start-today explanation, actual 4/target3 semantics, failed save retaining draft, Progress unavailable on read failure, history deletion removal, no monitoring/reward writes, and zone refresh. Run focused connected test; observe failures.
- [ ] Implement G3 in weekly review/Home/History, add deletion consequences to Settings, and use U1 dates/numbers. No reminders, streaks, speech bursts or analytics.
- [ ] Run source checks, full core tests, app unit/build/lint and relevant connected tests. Record TalkBack/RTL/large-font behavior and voluntary usefulness feedback before wider rollout.
- [ ] Review/commit: `feat: show optional weekly activity goals`.

## Exit

G1–G3 pass and pilot usefulness is recorded. Do not equate goal participation with improved wellbeing or reduced app use.
