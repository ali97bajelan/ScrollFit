# U4 — Compact history and coverage-aware weekly review

Date: 2026-10-17. Status: implementation proposal. Covers **UX10, F04**. Depends on [U1](2026-10-17-ux-foundation-design.md) and the [shared contract](../../UX_REVIEW_IMPLEMENTATION_2026-10-17.md).

## Intent and boundaries

Help people reflect on recorded use and exercise without treating missing data as improvement. Preserve `OutcomeAggregator`/`CoverageAssessment` comparison rules. The current eager `ProductPage`/daily cards and full-history read are replaced for History only; other screens need no list rewrite.

## H1 — Useful first view

Show a compact seven-calendar-day overview ending today, followed by separate exercise/ exercise-only summary, comparison eligibility, then expandable daily detail. One day is expanded at a time; initial expansion is none. Bars represent recorded combined usage with a visible numeric/text equivalent, never earned credit. Distinguish complete-known-zero (a zero marker), complete-known-positive, partial/in-progress (hatched/annotated recorded amount), and unknown (unknown marker, no zero-height bar). A partial recorded amount is labeled as recorded usage, not a whole-day total. Today always says In progress.

If there are no visible usage, activity or coverage rows, lead with **Complete your first exercise** and **Set up selected apps**, not seven unknown cards and two failed comparisons. If history-visibility markers indicate deletion, say **No visible history** rather than claiming the person never exercised. Keep the actions available. A coverage-only day counts as evidence even with zero consumption. Removed apps use package-name fallback; app labels are decoration and cannot change scope identity.

## H2 — Eligibility remains strict

Daily comparison is yesterday versus the day before yesterday. Weekly comparison is the seven completed days before today versus the preceding seven completed days. Today is excluded from comparisons. Both periods need complete coverage, consistent accounting, identical selected-package scope and zone, and visible history. Keep existing reason priority for hidden history, changed zone/scope, insufficient coverage and inconsistent accounting.

Show eligible-day counts for the two-day and fourteen-day comparison sets, with a **Why can't these periods be compared?** explanation. Counts help explanation and do not authorize partial comparisons. Do not substitute seven eligible nonconsecutive dates or infer reduced use from missing observations. Durations/dates use U1 formatters and the snapshot's captured today/zone.

## H3 — Lazy composition and bounded repository reads

History uses one `LazyColumn` with stable date keys and expandable detail. Read an initial window from today minus 14 days through today inclusive (15 dates, enough for both comparison periods). Add explicit **Load older days**; each page contains at most 14 distinct recorded dates strictly before its cursor, newest first. Unknown synthetic dates appear only in the recent seven, not as infinite older rows. A page may have many records on one day; the guarantee is bounded dates, not a claimed memory cap. Preserve each day's detail values; never split a day's accounting across pages.

Add `DataRepository.visibleHistorySlice(fromDay: String, throughDay: String): VisibleHistory` and `olderVisibleHistory(beforeDay: String, limit: Int): HistoryPage`; `HistoryPage(history: VisibleHistory, nextBeforeDay: String?)`. Limit is 1..31; UI uses 14. The older query selects distinct visible dates with limit+1 lookahead, then fetches rows only for returned dates in a single Room transaction, including mode mappings and visibility metadata. Restrict hiddenUsageDays metadata to the requested window/date set while retaining the global coverageHiddenThroughDay marker. `nextBeforeDay` is the oldest returned date when more records exist, otherwise null. Strictly-exclusive cursors prevent duplicate dates. Preserve full `visibleHistory()` for existing callers. No Paging library is needed.

On refresh, midnight, time-zone/scope change, history clear, or new source revision: invalidate old page generation/cursors and reload. Late page completions from prior generations are ignored. Clearing history must not append old in-flight results. Read failures preserve clearly labeled previous data and offer Retry; initial failure shows no fabricated chart. Add DAO range/day-set queries instead of loading all history then slicing in UI. Keep transactions and visibility semantics in the repository.

## H4 — Weekly reflection, not a score

Use the same overview for a compact optional weekly review: recorded use with coverage, exercise-only achievements, comparison eligibility, and **Did these breaks help you feel more in control?** Choices: More / About the same / Less / Skip. This first version keeps an answer only in saved screen state for the current visit, sends nothing, and labels it as a private reflection. It offers **Adjust my plan** opening U3 and **Try an exercise** opening exercise-only. No streak, health claim, usage-reduction claim, or automatic reward/rule change follows an answer. A persisted journal or telemetry would require a separate data design.

## Acceptance and validation

- Unknown, partial, known zero, positive and current-day values remain distinct in visuals and accessibility text.
- First use and deleted-history empty states give direct actions; activity stays separate from credit and app use.
- Existing zone/scope/hidden/coverage/accounting comparison tests pass unchanged. Eligibility counts describe exact comparison windows.
- With 1,000 recorded dates, initial repository read is 15 calendar dates and each older read returns at most 14 distinct dates; list composition is lazy. Measure initial/older load times and composed item count on the same seeded device/build before claiming improvement.
- Clear-history/midnight/zone changes during a suspended page load cannot restore stale rows. No exportable/visible activity is recreated by presentation.
- French/Persian/RTL/200% font and TalkBack can distinguish every coverage state and activate day expansion, older loading and adjustment.
