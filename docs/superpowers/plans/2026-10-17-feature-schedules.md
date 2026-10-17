# P4 Scheduled Plans Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans or, if explicitly selected by the owner, superpowers:subagent-driven-development to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Apply explicitly activated time-of-week limits without resetting whole-day accounting or relying on exact alarms.

**Architecture:** An immutable schedule revision feeds one pure resolver used by policy and UI. The monitor supplies timestamp provenance and splits observed checkpoints at transitions; a separate store retains schedule drafts/configuration.

**Tech Stack:** Existing Kotlin `java.time`, JUnit 4, DataStore, Room-backed engine, Compose and monitor queue; no alarm/runtime dependency additions.

**Spec:** [P4 design](../specs/2026-10-17-feature-schedules-design.md). **Entry gate:** demonstrated demand plus owner review of SC1–SC4 recorded before task 1. Until then this is a prepared proposal, not active implementation scope.

## Global Constraints

- Keep minSdk 28, compileSdk/targetSdk 37, and JVM target 17. Use the repository's pinned dependencies; no production-library upgrades are part of these plans.
- All persisted accounting durations are integer milliseconds. Only `BudgetEngine` grants rewards and spends usage; UI projections never change that authority.
- Monitoring requires explicit enablement. Stop remains available with invalid drafts or missing access. Permission connectivity is not proof of enforcement.
- Retain immutable challenge terms, unique award identities, non-restorable interrupted progress, protected-app filtering, local-midnight expiry, and hidden-history safeguards.
- Support Persian (`values`), English (`values-en`), and French (`values-fr`), RTL, localized numbers, ASCII/Persian/Arabic digit entry, and system light/dark themes. Gregorian calendar remains the display policy for this package.
- Keep processing offline and local. Do not collect camera frames, other-app content, or raw app-use histories as product events. Feature discovery uses optional pilot observation and manual aggregate notes.

## Review Focus

- Overnight rules overlap the next weekday or week wrap (task 1).
- DST/zone/manual-clock changes create repeated or nonexistent local minutes (tasks 1, 3).
- One checkpoint crosses a limit transition or a schedule edit revision (task 3).
- Lowering a limit/session boundary must not reset consumption (task 3).
- Schedule read failure/activation must not silently enable or relax monitoring (tasks 2, 4).

### Task 1: Deterministic schedule resolver

**Files:** Create `core/src/main/kotlin/com/example/scrollfit/core/settings/ScheduledPlans.kt`, `core/src/test/kotlin/com/example/scrollfit/core/settings/ScheduledPlansTest.kt`.

**Interfaces:** `ScheduleRule(id: ScheduleRuleId, weekdays: Set<DayOfWeek>, startMinute: Int, endMinute: Int, combinedCeilingMs: Long, sessionBoundaryMs: Long)`; `ScheduleRuleId { WORKDAY, EVENING }`; `ScheduleConfig(active: Boolean, revision: Long, rules: List<ScheduleRule>)`; `EffectivePlan(preferences: AppPreferences, ruleId: ScheduleRuleId?, nextTransition: Instant?)`; `ScheduledPlans.resolve(base: AppPreferences, schedule: ScheduleConfig, instant: Instant, zone: ZoneId): EffectivePlan`; `ScheduledPlans.transitionsBetween(start: Instant, end: Instant, zone: ZoneId, schedule: ScheduleConfig): List<Instant>`. Validate at construction; resolve never modifies base preferences.

- [ ] Add table tests for 09:00–17:00 weekday rule/fallback, 22:00–02:00 overnight, Sunday→Monday overlap rejection, adjacent end/start acceptance, empty/equal/invalid minutes rejection, and inactive schedule equality with base prefs.

  First red test `activeRuleOverridesLimitsWithoutEnablingMonitoring`:

  ```kotlin
  val base = AppPreferences(monitoringEnabled = false)
  val rule = ScheduleRule(ScheduleRuleId.WORKDAY, setOf(DayOfWeek.MONDAY),
      540, 1020, 1_800_000, 180_000)
  val plan = ScheduledPlans.resolve(base, ScheduleConfig(true, 1, listOf(rule)),
      Instant.parse("2026-10-19T12:00:00Z"), ZoneId.of("UTC"))
  assertEquals(1_800_000L, plan.preferences.combinedCeilingMs)
  assertFalse(plan.preferences.monitoringEnabled)
  ```

- [ ] Add fixed `America/New_York` spring/fall transition tests using actual instants and repeated local times; device-zone change must recompute active rule without rewriting dates. Run isolated `ScheduledPlansTest`; observe missing API failures.
- [ ] Implement interval validation and instant-based resolution/transition search per SC1/SC2. Assert no monitoring/reward/target fields are changed.
- [ ] Rerun focused tests; expect pass. Review/commit: `feat: define deterministic scheduled-plan policy`.

### Task 2: Atomic schedule store and policy adapter

**Files:** Create `platform/src/main/kotlin/com/example/scrollfit/platform/data/ScheduleRepository.kt`, `EffectivePlanProvider.kt`, and `platform/src/androidTest/kotlin/com/example/scrollfit/platform/data/SchedulePreferencesTest.kt`; modify `ExerciseGatePersistence.kt` in that directory.

**Interfaces:** `ScheduleRepository.values: Flow<ScheduleConfig>`; `suspend fun save(rules: List<ScheduleRule>)` increments revision without activating; `suspend fun setActive(active: Boolean)` changes schedule selection only. `EffectivePlanProvider.current(): EffectivePlan` and `at(base: AppPreferences, schedule: ScheduleConfig, instant: Instant, zone: ZoneId): EffectivePlan` share task 1. A separate `scheduled-plan-preferences` DataStore is absent/inactive by default.

- [ ] Test fresh/upgrade defaults inactive, save preserves activation state, activation changes no AppPreferences flag, malformed/read failure propagates, and one revision is captured atomically. Run connected `SchedulePreferencesTest` and observe missing behavior.
- [ ] Implement encoded rule validation and provider. Preserve raw manual prefs separately; expose the effective adapter for task 3, without replacing the engine's manual-preference supplier globally. Exercise-only flow loads its unrelated rules without requiring schedule access.
- [ ] Rerun focused tests and existing preferences tests; expect pass. Review/commit: `feat: persist explicit scheduled-plan selection`.

### Task 3: Timestamped checkpoints and policy-consistent accounting

**Files:** Modify `core/src/main/kotlin/com/example/scrollfit/core/budget/BudgetModels.kt`, `BudgetEngine.kt`, `core/src/main/kotlin/com/example/scrollfit/core/usage/MonitoringController.kt`, `UsageContracts.kt`, and `app/src/main/java/com/example/scrollfit/monitoring/MonitoringService.kt`; create `core/src/test/kotlin/com/example/scrollfit/core/budget/ScheduledAccountingTest.kt`; extend coverage/session tests and platform `BudgetPersistenceTest.kt`.

**Interfaces:** `ObservedUsageInterval(usage: UsageInterval, startEpochMs: Long, endEpochMs: Long, zoneId: String, scheduleRevision: Long)`. Add monitor-only `suspend fun spendObserved(interval: ObservedUsageInterval, policy: ObservedPolicySnapshot): SpendResult`, where `ObservedPolicySnapshot(base: AppPreferences, schedule: ScheduleConfig)` is immutable for the checkpoint and validated against the queued command revision. Retain the existing overload for unscheduled behavior/regression fixtures. Add `EffectivePolicyResolver.resolve(base: AppPreferences, instant: Instant, zone: ZoneId): AppPreferences` as a suspend port; add an engine constructor overload accepting this resolver while retaining the old constructor with an identity resolver. Manual preferences remain the original supplier. Invoke the effective resolver only for budget/session/spending/earning decisions; exercise-only offer/start/completion and saved-result lookup bypass it. Engine splits observed intervals at transition instants using their captured policy, applying task 1 preferences per segment; preserve cursor serialization and five-second total bound.

- [ ] Write tests: 2,000 ms checkpoint split 1,000/1,000 across a rule boundary uses each applicable limit; midnight preserves existing yesterday/day guards; old-revision queued command does not use newly edited schedule; clock/epoch inconsistency produces an unmonitored gap; duplicate/replayed checkpoint spends once. Lowered ceiling and shorter boundary block/prompt without resetting usage/session; raised ceiling exposes remaining whole-day capacity only. A transition during offer/start requires redisplay; completion revalidates active shared limit. A throwing schedule resolver blocks earning/usage decisions but exercise-only offer/start/completion and saved-result lookup still succeed from manual rules/committed records.
- [ ] Run `ScheduledAccountingTest` plus existing engine/usage/coverage/session tests; observe intended failures.
- [ ] Implement SC3 splitting and revision propagation through the existing serialized monitor queue. Do not infer usage during disabled/idle/gap periods. Resolve read failures to the existing storage-failure/pause path, not permissive fallback. Update platform engine tests with actual Room transactions.
- [ ] Rerun full core tests and platform connected budget/coverage tests; expect pass. Review/commit: `feat: apply scheduled policies to observed intervals safely`.

### Task 4: Explicit editor, review, active status and release gate

**Files:** Create `app/src/main/java/com/example/scrollfit/ui/ScheduleScreen.kt`, `app/src/androidTest/java/com/example/scrollfit/ui/ScheduleScreenTest.kt`; modify `ScrollFitApp.kt`, `ProductScreens.kt`, `SettingsScreen.kt`, `ProductData.kt` and monitoring `SetupContent.kt`/`PresetChooser.kt`; update all three `ux_strings.xml`; create `docs/UX_SCHEDULES_PHONE_TEST.md`.

**Interfaces:** `ScheduleScreen(onClose: () -> Unit)` uses U3 editor-exit semantics. Product snapshot carries `effectivePlan: EffectivePlan` alongside saved manual preferences; all active-limit labels use effective values with the rule/fallback name. Editor exposes Save, Activate schedule, Use manual plan and Stop as distinct commands.

- [ ] Add tests for invalid overlap blocking Save, manual fallback preview, save-without-activation, activation with monitoring off staying off, schedule read error/retry, preset fallback-only warning, current/next rule zone, and retained consumed usage after transitions. Run connected `ScheduleScreenTest`; observe failure.
- [ ] Implement SC4 navigation/status and explicit activation; show rule-transition limitations without development notes. Do not request exact alarms or use alarm callbacks to enable monitoring.
- [ ] Run source checks, full core tests, app unit/build/lint and connected suites. Record physical clock/zone/DST simulation, disable/Stop, process restart and new-intent challenge behavior. Require accounting and policy review before wider pilot.
- [ ] Review/commit: `feat: expose explicit scheduled plans and fallback status`.

## Exit

SC1–SC4 pass with demand and policy approval recorded. All accounting-sensitive tests are required; no partial UI-only schedule release.
