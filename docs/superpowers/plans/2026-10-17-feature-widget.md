# P6 Status Widget Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans or, if explicitly selected by the owner, superpowers:subagent-driven-development to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Show explicitly timestamped selected-app budget snapshots with honest unavailable/stale states.

**Architecture:** A pure cache/freshness projection feeds native RemoteViews; a provider reads persisted engine budgets and independent per-widget configuration, using existing app/monitor events for bounded refresh requests.

**Tech Stack:** Existing Kotlin/JUnit 4, platform AppWidgetProvider/RemoteViews/PendingIntent, DataStore, Compose configuration activity; no new runtime dependencies.

**Spec:** [P6 design](../specs/2026-10-17-feature-widget-design.md). Entry: U1/U3 plus recorded demand in `docs/UX_WIDGET_PHONE_TEST.md` before task 1.

## Global Constraints

- Keep minSdk 28, compileSdk/targetSdk 37, and JVM target 17. Use the repository's pinned dependencies; no production-library upgrades are part of these plans.
- All persisted accounting durations are integer milliseconds. Only `BudgetEngine` grants rewards and spends usage; UI projections never change that authority.
- Monitoring requires explicit enablement. Stop remains available with invalid drafts or missing access. Permission connectivity is not proof of enforcement.
- Retain immutable challenge terms, unique award identities, non-restorable interrupted progress, protected-app filtering, local-midnight expiry, and hidden-history safeguards.
- Support Persian (`values`), English (`values-en`), and French (`values-fr`), RTL, localized numbers, ASCII/Persian/Arabic digit entry, and system light/dark themes. Gregorian calendar remains the display policy for this package.
- Keep processing offline and local. Do not collect camera frames, other-app content, or raw app-use histories as product events. Feature discovery uses optional pilot observation and manual aggregate notes.

## Review Focus

- Cache age reaches 300,000 ms without host rerender (task 1 timestamp/cutoff contract).
- Local midnight/zone/boot/wall-clock regression invalidate old numbers (task 1).
- Multiple widgets and removed/unselected targets remain independent (task 2).
- Forged widget ID/intent must not affect budget or monitoring (task 3).
- Slow read/disconnection/Stop and launcher-delayed updates remain truthful (tasks 2–3).

### Task 1: Snapshot and freshness projection

**Files:** Create `core/src/main/kotlin/com/example/scrollfit/core/outcomes/WidgetSnapshot.kt` and `core/src/test/kotlin/com/example/scrollfit/core/outcomes/WidgetSnapshotTest.kt`.

**Interfaces:** `WidgetSnapshot(capturedAtEpochMs: Long, capturedAtElapsedMs: Long, bootId: String, day: String, zoneId: String, apps: List<WidgetAppValue>)`; `WidgetAppValue(packageName: String, usableMs: Long?, available: Boolean)`; `WidgetPresentation.project(snapshot: WidgetSnapshot?, configured: Boolean, nowEpochMs: Long, nowElapsedMs: Long, bootId: String, day: String, zoneId: String, enabled: Boolean, connected: Boolean, failed: Boolean): WidgetPresentation`. Result fields: `dataState: WidgetDataState`, `connectionState: WidgetConnection`, `apps: List<WidgetAppValue>`, `capturedAtEpochMs: Long?`, `refreshAfterEpochMs: Long?`. `WidgetDataState { UNCONFIGURED, LOADING, SNAPSHOT, OLDER_SNAPSHOT, DATA_UNAVAILABLE }`; `WidgetConnection { MONITORING_OFF, ACCESS_DISCONNECTED, CONNECTED_UNVERIFIED }`; individual unavailable apps retain their slot. Cutoff is exactly 300,000 ms, numeric values are never live decremented.

- [ ] Test ages 299,999/300,000, frozen host display retaining timestamp/cutoff/uncertainty, midnight/zone/boot mismatch removing numbers, wall/elapsed regression, known zero vs read error, off/disconnected with older-data label, and three wallets kept independent. Run isolated `WidgetSnapshotTest`; observe missing API failure.

  First red test `fiveMinuteCutoffMarksSnapshotOlderWithoutCountingDownWallet`:

  ```kotlin
  val snapshot = WidgetSnapshot(1_000_000, 1_000, "boot-a", "2026-10-17", "UTC",
      listOf(WidgetAppValue("a", 30_000, true)))
  val view = WidgetPresentation.project(snapshot, true, 1_300_000, 301_000,
      "boot-a", "2026-10-17", "UTC", true, true, false)
  assertEquals(WidgetDataState.OLDER_SNAPSHOT, view.dataState)
  assertEquals(30_000L, view.apps.single().usableMs)
  assertEquals(1_300_000L, view.refreshAfterEpochMs)
  ```

- [ ] Implement V1 projection with checked/saturating cutoff arithmetic and no Android dependency.
- [ ] Rerun focused tests; expect pass. Review/commit: `feat: define honest widget snapshot states`.

### Task 2: Configuration/cache and bounded provider reads

**Files:** Create `platform/src/main/kotlin/com/example/scrollfit/platform/data/WidgetRepository.kt`, `platform/src/androidTest/kotlin/com/example/scrollfit/platform/data/WidgetPreferencesTest.kt`, `app/src/main/java/com/example/scrollfit/widget/WidgetUpdater.kt`, and `app/src/test/java/com/example/scrollfit/widget/WidgetUpdaterTest.kt`; expose repository through `ExerciseGatePersistence.kt`.

**Interfaces:** `WidgetRepository.configurations: Flow<Map<Int, Set<String>>>`, `suspend fun save(id: Int, packages: Set<String>)`, `suspend fun remove(id: Int)`; 1..3 packages validated. Cache is keyed by widget ID and includes V1 provenance. `WidgetUpdater.refresh(ids: Set<Int>, reason: WidgetRefreshReason)` rechecks selected eligibility/current access, reads engine budgets without usage-event polling, enforces 8,000 ms timeout and 60,000 ms spending-event throttle. Inject clock/read/publish ports for tests.

- [ ] Test two configurations/reopen/removal, empty/4 selections rejected, unselected app retained unavailable, timeout/error publication, every async completion finishes, boot/disconnect invalidation, and spending refresh throttling while explicit Refresh bypasses throttle. Run focused unit and connected preference tests; observe missing behavior.
- [ ] Implement separate local widget store and bounded app-scoped updater. Cache is explanatory only; never persist live connection truth or auto-start monitoring. P4 integration is conditional on that feature being installed.
- [ ] Rerun tests plus existing budget/monitor tests; expect pass. Review/commit: `feat: store widget selections and refresh snapshots safely`.

### Task 3: RemoteViews, host configuration and integration

**Files:** Create `app/src/main/java/com/example/scrollfit/widget/StatusWidgetProvider.kt`, `StatusWidgetConfigurationActivity.kt`, `StatusWidgetViews.kt`; create `app/src/main/res/layout/status_widget.xml`, `app/src/main/res/xml/status_widget_info.xml`, and `app/src/androidTest/java/com/example/scrollfit/widget/StatusWidgetTest.kt`; modify main manifest, `ScrollFitApplication.kt`, UI/monitor event publication points and main activity Home routing; update all three `ux_strings.xml`; create `docs/UX_WIDGET_PHONE_TEST.md`.

**Interfaces:** Provider declares updatePeriodMillis=1,800,000 and supports resizing. `StatusWidgetViews.render(context: Context, id: Int, model: WidgetPresentation): RemoteViews`. Configuration validates provider-owned widget ID, saves 1..3 selected eligible packages and returns result with appWidgetId. Explicit immutable per-ID intents open Home or request internal refresh; no challenge/reward/enable action is exposed.

- [ ] Add tests for invalid/unknown IDs returning cancelled/no mutation, independent per-ID PendingIntents, long labels/full descriptions, known zero vs unknown, old snapshot timestamp/absolute cutoff always present, removed target placeholder, system light/dark and RTL layout, and no summed-wallet total. Run connected `StatusWidgetTest`; observe failure.
- [ ] Implement V2/V3, using `goAsync` with guaranteed finish and timeout, native semantic tokens and 48 dp interactions. Hook explicit Refresh/configuration, resume, plan changes, Stop/disconnect, committed completion/history clear, and throttled spending. Do not add alarms/services or auto-enable monitoring.
- [ ] Run source checks, core tests, app unit/build/lint, connected widget tests and preferences tests. Record real launcher resizing/delayed delivery, process death/reboot/day/zone changes, API 28/37, French/Persian/TalkBack and measured battery/update behavior. A label rendered before cutoff is never described as automatically expiring.
- [ ] Review/commit: `feat: add timestamped selected-app status widget`.

## Exit

V1–V3 and launcher/device checks pass. The widget promises a captured snapshot and clear recovery, never a continuously observed countdown or guaranteed enforcement.
