# U4 History and Weekly Review Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans or, if explicitly selected by the owner, superpowers:subagent-driven-development to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Provide an honest compact weekly overview with lazy daily detail and bounded older-history loading.

**Architecture:** Keep existing aggregators; add transactional date-window/page reads and a pure display projection, then a single lazy history screen.

**Tech Stack:** Kotlin/JUnit 4, Room, existing coroutines, Compose Material 3 and U1 test/formatting contracts; no new runtime dependency.

**Spec:** [U4 design](../specs/2026-10-17-ux-history-design.md).

## Global Constraints

- Keep minSdk 28, compileSdk/targetSdk 37, and JVM target 17. Use the repository's pinned dependencies; no production-library upgrades are part of these plans.
- All persisted accounting durations are integer milliseconds. Only `BudgetEngine` grants rewards and spends usage; UI projections never change that authority.
- Monitoring requires explicit enablement. Stop remains available with invalid drafts or missing access. Permission connectivity is not proof of enforcement.
- Retain immutable challenge terms, unique award identities, non-restorable interrupted progress, protected-app filtering, local-midnight expiry, and hidden-history safeguards.
- Support Persian (`values`), English (`values-en`), and French (`values-fr`), RTL, localized numbers, ASCII/Persian/Arabic digit entry, and system light/dark themes. Gregorian calendar remains the display policy for this package.
- Keep processing offline and local. Do not collect camera frames, other-app content, or raw app-use histories as product events. Feature discovery uses optional pilot observation and manual aggregate notes.

## Review Focus

- Activity-only/coverage-only days, hidden current day, and removed app labels (tasks 1–2).
- Unknown and measured zero have different chart semantics (task 2).
- Time-zone changes make yesterday's scope unfit for comparison (task 2).
- Clear-history/midnight during a delayed older-page read invalidates it (task 3).
- One date has many rows and 1,000 older dates exist (tasks 1, 3).

### Task 1: Bounded transactional history queries

**Files:** Modify `core/src/main/kotlin/com/example/scrollfit/core/data/DataRepository.kt`, `Models.kt`, `DurableStore.kt`, `DurableRepository.kt`, `core/src/test/kotlin/com/example/scrollfit/core/data/MemoryStore.kt`; modify `platform/src/main/kotlin/com/example/scrollfit/platform/data/ExerciseGateDao.kt`, `RoomStore.kt`; create `core/src/test/kotlin/com/example/scrollfit/core/data/HistoryPageTest.kt` and `platform/src/androidTest/kotlin/com/example/scrollfit/platform/data/HistoryPagePersistenceTest.kt`.

**Interfaces:** The two repository methods and `HistoryPage` are defined in H3. Add transaction ports `visibleDatesBefore(beforeDay: String, limit: Int): List<String>`, `visibleDatesInRange(fromDay: String, throughDay: String): List<String>`, and `visibleHistoryForDays(days: Set<String>): VisibleHistory`; the latter includes visibility metadata and only relevant activity modes. For slice, select visible dates within inclusive range and fetch in the same transaction. Room queries must filter at SQL level and preserve hidden markers.

- [ ] Add core/Room tests with 1,000 dates: first 14 distinct days, exclusive next cursor, no duplicates over pages, limit validation 0/32 rejected, activity-only/coverage-only included, same-day multiple apps kept together, deleted rows never returned, and retained enforcement counters unaffected.
- [ ] Run focused `HistoryPageTest` via the isolated core Gradle project and connected platform test for `com.example.scrollfit.platform.data.HistoryPagePersistenceTest`; observe missing API failures.
- [ ] Implement DAO union-of-visible-dates/IN-date reads and repository assembly in one transaction. Preserve existing full-history method and award identity retention. Do not change schema merely for a UI projection; add an index migration only if measured query plans justify one, recording that separately.
- [ ] Rerun focused tests plus existing persistence/coverage tests; expect pass. Review/commit: `feat: add bounded visible-history reads`.

### Task 2: Honest overview and eligibility projection

**Files:** Create `core/src/main/kotlin/com/example/scrollfit/core/outcomes/HistoryPresentation.kt` and `core/src/test/kotlin/com/example/scrollfit/core/outcomes/HistoryPresentationTest.kt`; retain `OutcomeAggregator.kt` and `CoverageAssessment.kt` policy.

**Interfaces:** `HistoryPresentation.build(history: VisibleHistory, today: LocalDate, zone: ZoneId): HistoryPresentation` returns `summary: OutcomeSummary`, `emptyKind: HistoryEmptyKind?`, `dayVisuals: Map<LocalDate, DayUsageVisual>`, `dailyEligibleDays: Int`, `weeklyEligibleDays: Int`. `HistoryEmptyKind { FIRST_USE, NO_VISIBLE_HISTORY }`; `DayUsageVisual { UNKNOWN, PARTIAL, KNOWN_ZERO, KNOWN_POSITIVE }` is derived from coverage/in-progress/value together. Counts cover exactly today-2..today-1 and today-14..today-1 and use the existing comparability predicate, never current-day data.

- [ ] Test empty vs deleted markers, coverage-only known zero, partial positive usage, null unknown, current-day In-progress, exercise-only separation, removed app fallback data, and scope/zone change blocking comparison despite a positive eligible-day count. Pin weekly date bounds to a fixed today, including a DST week.

  First red test `firstUseDoesNotBecomeSevenZeroUsageDays`:

  ```kotlin
  val today = LocalDate.of(2026, 10, 17)
  val view = HistoryPresentation.build(VisibleHistory(emptyList(), emptyList(), emptyList()), today, ZoneId.of("UTC"))
  assertEquals(HistoryEmptyKind.FIRST_USE, view.emptyKind)
  assertEquals(DayUsageVisual.UNKNOWN, view.dayVisuals[today])
  assertEquals(0, view.weeklyEligibleDays)
  ```

- [ ] Run focused `HistoryPresentationTest`; observe missing API failures. Implement projection without duplicating comparison policy or converting null to zero.
- [ ] Run projection and existing `OutcomeAggregatorTest`/`CoverageAssessmentTest`; all existing rules must remain passing. Review/commit: `feat: project coverage-aware history overview`.

### Task 3: Lazy history screen and private reflection

**Files:** Modify `app/src/main/java/com/example/scrollfit/ui/HistoryScreen.kt`, `ProductData.kt`, `ScrollFitApp.kt`; create `HistoryData.kt`, `WeeklyReview.kt` in UI; create `app/src/test/java/com/example/scrollfit/ui/HistoryDataTest.kt`, `app/src/androidTest/java/com/example/scrollfit/ui/HistoryOverviewTest.kt`; update all three `ux_strings.xml`; create `docs/UX_HISTORY_PHONE_TEST.md`.

**Interfaces:** `HistoryData` replaces `rememberProductData(history=true)` only for History; it owns initial slice, older pages, generation, loading/error/cursor and refresh. `suspend fun loadOlder()` uses page size 14. `HistoryScreen(onExercise: () -> Unit, onSetup: () -> Unit)` provides direct actions; WeeklyReview consumes task 2 projection and retains only the current visit's reflection in saved state.

- [ ] Test delayed-page invalidation on refresh/history-clear/midnight/zone change with injected repository ports and a controllable completion; no stale append and no duplicate cursor request. Run `HistoryDataTest` before implementation and observe missing behavior.
- [ ] Add Compose tests for all four visual/semantic states, one expanded day, initial first-use actions, deleted empty copy, Try exercise/Adjust plan, load-more retry, and reflection skip with no persistence/network calls. Observe the failing `HistoryOverviewTest`.
- [ ] Implement one `LazyColumn` with date keys, 7-cell/text-equivalent overview, separate activity summary, exact eligibility explanations, one expanded day, and explicit older loading. Limit initial source query to today-14..today. Preserve old data with a stale/read-error label when refresh fails. Reset pages on every source generation change.
- [ ] Run source checks, full core tests, app unit/build/lint and connected app/platform tests. Seed 1,000 dates on a device; record baseline/new initial read size, load latency, list composition behavior and a high-row-count date. Verify TalkBack/RTL/large text manually; do not claim performance gains from lazy source alone.
- [ ] Review/commit: `feat: add compact history and private weekly review`.

## Exit

H1–H4 and recorded UI/performance checks pass. P2 goals and P5 export may consume the same visible-history semantics; neither may reinterpret incomplete coverage as zero.
