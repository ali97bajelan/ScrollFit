# P5 Local History Export Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans or, if explicitly selected by the owner, superpowers:subagent-driven-development to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Export only visible history as exact versioned CSV/JSON to a user-chosen document.

**Architecture:** A pure range/snapshot projection feeds streaming writers; an app coordinator stages locally and serializes clear-versus-copy ownership without locking monitoring.

**Tech Stack:** Existing Kotlin/JUnit 4, Room snapshot, Java Writer/Android JsonWriter, coroutines, Compose and Storage Access Framework; no new runtime dependencies.

**Spec:** [P5 design](../specs/2026-10-17-feature-export-design.md). Entry: U4 plus demonstrated demand recorded in `docs/UX_EXPORT_PHONE_TEST.md` before task 1.

## Global Constraints

- Keep minSdk 28, compileSdk/targetSdk 37, and JVM target 17. Use the repository's pinned dependencies; no production-library upgrades are part of these plans.
- All persisted accounting durations are integer milliseconds. Only `BudgetEngine` grants rewards and spends usage; UI projections never change that authority.
- Monitoring requires explicit enablement. Stop remains available with invalid drafts or missing access. Permission connectivity is not proof of enforcement.
- Retain immutable challenge terms, unique award identities, non-restorable interrupted progress, protected-app filtering, local-midnight expiry, and hidden-history safeguards.
- Support Persian (`values`), English (`values-en`), and French (`values-fr`), RTL, localized numbers, ASCII/Persian/Arabic digit entry, and system light/dark themes. Gregorian calendar remains the display policy for this package.
- Keep processing offline and local. Do not collect camera frames, other-app content, or raw app-use histories as product events. Feature discovery uses optional pilot observation and manual aggregate notes.

## Review Focus

- Hidden/deleted rows remain private while visible awarded totals may export (task 1).
- Unknown historical zones/coverage must not become current-zone/zero values (task 1).
- CSV formulas, quotes, Unicode/newlines and Long precision (task 2).
- History clear during picker/staging/copy and process death (task 3).
- Partial destination write or stream-close failure (task 3).

### Task 1: Versioned export projection

**Files:** Create `core/src/main/kotlin/com/example/scrollfit/core/outcomes/HistoryExport.kt` and `core/src/test/kotlin/com/example/scrollfit/core/outcomes/HistoryExportTest.kt`.

**Interfaces:** `ExportFormat { CSV, JSON }`; `HistoryExportSnapshot(history: VisibleHistory, fromDay: LocalDate, throughDay: LocalDate, exportedAtEpochMs: Long, exportZone: ZoneId, today: LocalDate)`; `HistoryExports.prepare(history: VisibleHistory, fromDay: LocalDate, throughDay: LocalDate, exportedAtEpochMs: Long, exportZone: ZoneId, today: LocalDate): HistoryExportSnapshot` validates range/filter/sorting and coverage assessment. No `Award` ledger input is accepted.

- [ ] Add tests for inclusive range, invalid/reversed range, visible totals, hidden/deleted omission, null historical zone, visible activity-only mode mapping, empty result metadata, deterministic row ordering and exact Long values. Run isolated `HistoryExportTest`; observe missing API failures.
- [ ] Implement E1/E2 projection using existing coverage assessment and repository visibility. Do not synthesize zero-use rows or export visibility markers.
- [ ] Rerun focused tests plus existing outcome/history-deletion tests; expect pass. Review/commit: `feat: define visible-history export schema`.

### Task 2: Exact streaming CSV/JSON

**Files:** Create `platform/src/main/kotlin/com/example/scrollfit/platform/data/HistoryExportWriter.kt` and `platform/src/androidTest/kotlin/com/example/scrollfit/platform/data/HistoryExportWriterTest.kt`.

**Interfaces:** `HistoryExportWriter.write(snapshot: HistoryExportSnapshot, format: ExportFormat, writer: Writer, cancelled: () -> Boolean = { false })` streams rows and propagates cancellation/I/O failures. Caller owns stream close. JSON uses `android.util.JsonWriter`; CSV uses fixed E2 header and correct quoting/text-cell escaping. No unpinned serialization library.

- [ ] Add tests parsing JSON back with `org.json`: schema/arrays/nulls exact, ms/reps are decimal strings including `Long.MAX_VALUE`; verify CSV header/metadata row, row order, commas/quote/newline round trip, and text starting `=SUM`, ` +1`, `@`, tab or CR is prefixed. Verify unknown fields remain empty/null, never 0. Run connected writer tests before implementation and observe failure.

  Name the Long-precision test `jsonPreservesMillisecondsBeyondJavascriptIntegerPrecision`. Create a visible usage row with `baseConsumedMs = Long.MAX_VALUE`, zero earned/awarded, then write its prepared snapshot:

  ```kotlin
  val output = StringWriter()
  HistoryExportWriter.write(snapshot, ExportFormat.JSON, output)
  val json = JSONObject(output.toString())
  assertEquals("decimal_string", json.getString("integer_encoding"))
  assertEquals(Long.MAX_VALUE.toString(), json.getJSONArray("usage").getJSONObject(0).getString("base_consumed_ms"))
  assertTrue(json.getJSONArray("usage").getJSONObject(0).isNull("zone_id"))
  ```

- [ ] Implement E2 formats with cancellation checks between rows. Keep headers stable and locale-independent, including a Persian resource context. Do not write app labels/fields outside the schema.
- [ ] Rerun connected `HistoryExportWriterTest`; expect pass. Review/commit: `feat: stream exact CSV and JSON history exports`.

### Task 3: Explicit destination and clear-safe export lifecycle

**Files:** Create `app/src/main/java/com/example/scrollfit/ui/HistoryExportScreen.kt`, `HistoryExportCoordinator.kt`, `app/src/test/java/com/example/scrollfit/ui/HistoryExportCoordinatorTest.kt`, `app/src/androidTest/java/com/example/scrollfit/ui/HistoryExportScreenTest.kt`; modify UI `HistoryScreen.kt`, `SettingsScreen.kt`, `ScrollFitApp.kt` and `app/src/main/java/com/example/scrollfit/ScrollFitApplication.kt`; update all three `ux_strings.xml`; create `docs/UX_EXPORT_PHONE_TEST.md`.

**Interfaces:** Application-scoped coordinator owns `suspend fun prepare(format: ExportFormat, range: ClosedRange<LocalDate>)`, `suspend fun copyTo(uri: Uri)`, `cancel()`, and `suspend fun clearVisibleHistory()` used by Settings. It stages only after picker returns/current snapshot is captured; prepared generation invalidates on clear. Copy/clear share a coordinator mutex, never a Room transaction/monitor lock. Inject URI stream/cache/read ports for unit races.

- [ ] Add tests for picker cancelled→no file/write, clear before copy→prepared invalid, clear during copy waits/cancel cleans temp, read failure→retry/no success, close failure→incomplete message, startup cleanup/no auto-resume, and final review after picker even when row count is unchanged. Run coordinator/UI tests and observe intended failures.
- [ ] Implement format/date review, CreateDocument intent, final snapshot/staging and explicit final range/count/timestamp confirmation, off-main streaming and best-effort just-created-document cleanup. Success follows closed stream only. Show exported-file retention explanation and link schema/units/coverage notes in accessible UI help.
- [ ] Run source checks, core tests, app unit/build/lint, platform writer/visibility and app connected tests. Exercise local/provider destinations, cancellation/full/denied storage, process kill and clear ordering on device; record exact outcomes. Do not claim remote provider atomicity.
- [ ] Review/commit: `feat: export visible history through a chosen document`.

## Exit

E1–E3 pass; export does not read hidden ledgers or alter monitoring. Existing external files remain outside subsequent in-app history deletion.
