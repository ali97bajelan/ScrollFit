# P5 — Local visible-history export (F07)

Date: 2026-10-17. Status: demand-gated Stage D proposal. Entry: U4 acceptance and a recorded CSV/JSON use case. Read the [package contract](../../UX_REVIEW_IMPLEMENTATION_2026-10-17.md).

## Intent and boundary

Let the person export the same visible usage, activity and coverage they can inspect, without an account. Offer CSV and JSON, date range defaulting to the visible last seven dates, with All visible history as an explicit option. Processing stays on device. Use a user-selected document destination through [Android's Storage Access Framework](https://developer.android.com/training/data-storage/shared/documents-files); no broad storage permission or automatic upload/share. The chosen provider can sync the destination under the user's own provider settings.

## E1 — Snapshot and visibility

Capture a single immutable `VisibleHistory` snapshot after destination selection and range validation. Apply the same visibility rules as History; do not read the retained private award ledger, hidden usage, deleted activities, camera data, internal monitoring failure logs, or deletion-marker dates into the export. Visible `DailyUsage.awardedMs` is allowed; retained `Award` rows are not. Include visible activity mode only when the related visible activity is present. Missing historical zone for usage/activity is Unknown, never filled from current device zone. Coverage rows carry their actual recorded zone and selected-package scope.

Serialize to a private temporary cache file first. Show range/format/row count in a review before the document picker and capture the actual final snapshot after picker return, so clearing history while the picker is open cannot leak an old prepared snapshot. Always show the final captured range/count and timestamp for confirmation before copying, even if the count is unchanged. A shared export/history-clear coordinator invalidates prepared files on clear. A final ownership check orders file-copy start against clear; once copy begins, clear waits for that copy or cancellation, without holding a Room transaction or monitor queue lock. Deleting history does not recall a file already exported; say so in the delete/export explanation. Purge staging files on success, failure/cancel, and next process startup. Do not silently restore a pending export after process death.

## E2 — Versioned formats

Both formats are UTF-8, schema version `1`, ISO Gregorian dates, ASCII decimal integer values in milliseconds, and stable enum names. Export UI is localized; machine headers/enum values are not. Date-only records retain `recorded_day`; current `export_zone_id` is metadata, not an assertion about historical zones.

JSON top-level fields: `schema_version` (1), `exported_at_epoch_ms` (decimal string), `export_zone_id`, `range_from_day`, `range_through_day`, `duration_unit` (`ms`), `integer_encoding` (`decimal_string`), `usage`, `combined`, `activities`, `coverage`. All millisecond/epoch/repetition counts are decimal strings to preserve exact large values in spreadsheet/JavaScript consumers. Missing historical fields are null.

| Array | Fields |
| --- | --- |
| usage | `package_name`, `recorded_day`, `base_consumed_ms`, `earned_consumed_ms`, `awarded_ms`, `zone_id` (null) |
| combined | `recorded_day`, `consumed_ms`, `zone_id` (null) |
| activities | `id`, `challenge_id` (nullable), `recorded_day`, `exercise`, `mode` (nullable), `repetitions`, `active_duration_ms`, `recorded_at_epoch_ms`, `zone_id` (null) |
| coverage | `recorded_day`, `zone_id`, `start_epoch_ms`, `end_epoch_ms`, `selected_packages` (sorted array), `coverage_status` |

`coverage_status` is the existing day assessment COMPLETE/PARTIAL/UNKNOWN for the exported date using the snapshot's captured current zone/today; each interval retains actual boundaries. Include days with no coverage only in the displayed export explanation, not by fabricating interval rows. Explain that a recorded zero and missing data differ and comparisons were not recalculated from the file.

CSV is one normalized table with header, in this exact order: `schema_version,row_type,exported_at_epoch_ms,export_zone_id,range_from_day,range_through_day,duration_unit,recorded_day,package_name,id,challenge_id,exercise,mode,repetitions,base_consumed_ms,earned_consumed_ms,awarded_ms,consumed_ms,active_duration_ms,recorded_at_epoch_ms,zone_id,start_epoch_ms,end_epoch_ms,selected_packages_json,coverage_status`. Emit one `metadata` row even for an empty result, then usage/combined/activity/coverage rows. Inapplicable fields are empty; unknown is not numeric zero. Use RFC-style quote doubling for commas, quotes and line breaks; CRLF row separators. Treat text cells beginning with tab/CR/LF or whose first non-whitespace character is `=`, `+`, `-`, or `@` as text by prefixing `'`; document this reversible CSV escape, while JSON preserves exact strings. Do not emit localized thousands separators or spreadsheet formulas.

Sort by row type in the order above, then day, package/id, start time for reproducible exports. File names: `scrollfit-history-<from>-<through>.csv` / `.json`; MIME `text/csv` / `application/json`.

## E3 — Failure, cancellation and acceptance

Picker cancellation makes no export and does not change history. Read/staging failures offer Retry without a success claim. Destination copy failure says the file may be incomplete; offer retry with a new destination and best-effort cleanup of this just-created document. Do not delete another document or assume a remote provider supports deletion/atomic rename. Success is reported only after stream close succeeds. A completed export does not mark usage observed or change counters/history. Serialize off the main thread, with cancellation checks between rows/chunks.

Acceptance: exported rows match visible snapshot/range; hidden/deleted rows and camera/raw award data are absent; large Long values round-trip exactly; special-character text/CSV formula cases are safe; partial/unknown/current-day coverage stays explicit; storage denial, full destination, cancellation, clear-during-picker/copy, and process death never report false success. Verify provider behavior, locale-independent files and accessibility on a device. No measured export demand or successful run is claimed by this spec.
