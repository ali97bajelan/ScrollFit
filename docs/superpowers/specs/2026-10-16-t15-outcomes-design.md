# T15 daily and weekly outcomes design

Status: written design approved by the owner on 2026-10-16; native implementation
was started at the owner's request. The owner prohibited emulator and Gradle
test runs, so implementation verification remains incomplete.
Date: 2026-10-16 (Asia/Tehran).

## Intent and agreed direction

Prepare the next task after T14 for implementation. T15 helps the owner see
selected-app usage and exercise activity as separate outcomes, without confusing
missing monitoring data with zero use. The owner approved a rolling view of the
last seven calendar days, coverage-gated comparisons, and keeping existing
history visible with unknown coverage.

The task definition is `exercise_gate_tasks.md`, T15, and the daily/weekly
history requirements in `exercise_gate_codex_brief.md`. T16 integrated delivery
and physical-device acceptance remain separate. This document proposes the
remaining technical and product decisions. The owner subsequently approved this
written design, including its coverage/checkpoint and comparison policies.

## Existing implementation

- `DailyUsage` stores base/earned consumption and awarded milliseconds per app
  and day. `CombinedUsage` stores the once-accounted daily combined total.
- `ExerciseActivity` stores repetitions and active duration for reward and
  exercise-only challenges. Challenge identity can identify the earning mode.
- `DurableRepository.visibleHistory()` reads visible usage, combined rows and
  activities transactionally. Removed apps retain historical rows.
- `ProductScreens.kt` renders a basic daily history. It has no weekly aggregation
  or durable coverage evidence.
- `MonitoringController` rejects uncertain target intervals and reports gaps.
  `MonitorRuntime.hasUnmonitoredGap` is an in-memory warning, not historical
  coverage evidence.
- Room is at version 2. Authentic v1/v2 schema exports and Android execution are
  pending because previous Android builds stopped before compilation.
- History deletion hides accounting rows and deletes activities while preserving
  budgets, sessions, usage cursors, challenge identities and award records.

## Approaches considered

1. **Durable intervals plus pure outcome aggregation — recommended.** Record
   confirmed monitoring intervals, retain the existing accounting records as
   authoritative totals, and calculate coverage and summaries in the JVM core.
   This supports honest gaps and testable eligibility with one additive Room
   migration. It needs careful lifecycle integration.
2. **Daily coverage flag.** Persist a daily healthy/unhealthy flag alongside
   summaries. Smaller initially, but cannot describe partial-day monitoring or
   distinguish a reconnect from a fully observed day. Rejected for ambiguity.
3. **Summaries with coverage always unknown.** Add a weekly UI without collecting
   new evidence. Useful as an intermediate slice, but cannot complete the agreed
   comparison behavior and would leave the central T15 requirement unfinished.

## User-facing behavior

The History destination gains a seven-day summary ending today, followed by
daily detail. Include all seven dates, even when a date has no stored records.
Keep older visible history reachable in the existing daily list. No new primary
navigation destination or charting dependency is needed.

Display these outcomes separately:

- Selected-app usage: recorded combined duration and per-app duration, including
  historical apps that are no longer selected or available.
- Exercise activity: repetitions grouped by the four exercises and total active
  duration. Include exercise-only activity. Do not add repetitions of different
  exercises into a single fitness score.
- Time awarded: sum visible per-app `awardedMs`; this is distinct from earned
  time consumed and from exercise-only activity.

Combined usage comes from `CombinedUsage`, not from adding combined and app
totals together. A missing combined row stays unknown unless complete coverage
proves there was no target usage and no visible app rows contradict it. A missing
per-app row can mean zero only for an app in the complete day's recorded scope.
Legacy or inconsistent records remain visible as recorded values with a data
qualification; they cannot support a comparison.

Each date shows a coverage label: complete, partial, or unknown. Today is marked
in progress and is never compared as a completed day. Seven-day totals are
labeled recorded totals when any day is incomplete. Show coverage alongside
them; do not replace unknown usage with a displayed zero or extrapolate it.
Activity-only days still show their activity with usage marked unknown.

No streak penalties, guilt, calories, inferred fitness gains or automatic
difficulty changes. Suggested neutral messages include “Activity recorded” and
“Not enough comparable monitoring data yet.” A complete low-usage day does not
need exercise activity to be a useful result.

## Coverage meaning and collection

Coverage means the app has durable evidence that the monitoring path was able
to observe the configured target scope during an interval. It is not proof of
unbreakable enforcement or a measurement of all device use.

Persist intervals containing: unique recording-session ID, boot timeline ID,
start/end monotonic milliseconds, start/end epoch milliseconds, zone ID, and
the sorted set of selected target package names. Use an explicit history
visibility flag. Store scope members in a child table rather than a delimiter-
encoded package string. Coverage contains no other-app content or raw events.

A core coverage recorder has its own previous-sample anchor. It accepts only
successful serialized monitoring observations while enabled, usage access is
present, the service is connected, foreground reconciliation is known, and the
production monitor holds ownership. Screen-off, ScrollFit screens, blockers and
unrestricted apps may be covered without being chargeable target usage.
An observation whose `MonitorDecision` reports an issue or unmonitored duration
cannot confirm the preceding interval, even if the service currently looks
healthy. In particular, the existing five-second target-accounting gap rule
still takes precedence over coverage's longer idle confirmation window.

Two consecutive eligible observations confirm the interval between them only
when they share a recording session, boot, zone and target scope; elapsed time
increases; and wall-clock drift is within 2,000 ms. The maximum confirmation gap
is 90,000 ms. Larger gaps are unknown, not backfilled. Permission loss, unknown
foreground, ownership loss, queue/overlay/storage failure, clock/zone changes,
disablement and process/service restart break the anchor. A failed interval
write does not advance its persisted end.

Existing target ticks remain unchanged. While monitoring is eligible, schedule
one coverage checkpoint after 60,000 ms without a processed observation. Send
it through the existing serialized monitor command path, obtain current access,
interactive and foreground reconciliation state, and reset the idle timer after
each successfully processed observation. This is a bounded health checkpoint,
not continuous usage-event polling. Do not start a foreground service, request
a new permission or keep the device awake. Android may delay the checkpoint;
delays beyond 90,000 ms create a visible gap.

Merge adjacent intervals with the same session and scope transactionally to
avoid creating a row per checkpoint. Retry or duplicate samples must not extend
an interval twice. Never bridge sessions after process restart, even on the same
boot. Coverage writes must not alter the spending cursor or charge any duration;
the budget engine remains the only production spending authority. Commit
coverage only after the corresponding observation succeeds; missing coverage
is acceptable when accounting succeeded and the later coverage write failed.

At a scope change, break continuity conservatively. Observations arriving while
target settings change cannot certify a stable scope: carry the selected-target
snapshot used to process each observation and invalidate the coverage anchor
on configuration reload. An empty selected scope never qualifies for comparison.

## Aggregation and comparisons

Keep interval union, calendar splitting, coverage classification and outcome
aggregation in pure Kotlin, independent of Compose and Room.

Use ISO local dates, integer milliseconds, and actual zone-aware day boundaries.
Split accepted intervals at local midnight. A day may have 23, 24 or 25 hours;
do not assume 86,400,000 ms. Merge overlapping spans before measuring coverage.
A zone change within a reporting day makes that day ineligible for comparison;
recorded totals remain visible. Historical day identifiers are never reassigned
when the current zone changes.

A complete day is a finished calendar day whose visible, reliable interval union
covers its entire calendar duration under one nonempty selected-target scope
and one zone, with no hidden accounting or inconsistent totals. No percentage
threshold is used. The implementation may therefore show partial coverage often
on devices that suspend monitoring; honesty takes priority over producing a
comparison. Existing days without intervals have unknown coverage.

Compare yesterday with the preceding calendar day only when both are complete
and share the exact target scope and zone. Show absolute recorded usage change
in duration, with neutral “less”, “more” or “same” wording. Do not calculate a
percentage against zero. Otherwise show the eligibility explanation.

Compare the most recent seven completed calendar days with the immediately
preceding seven completed days only when all fourteen days meet the same
completeness, scope and zone requirements. This comparison is distinct from the
main seven-day display, which includes today. No matching-day substitution,
extrapolation, causal reduction claim or comparison to a user's current limits.

## Persistence, deletion and errors

Add coverage interval and scope-member tables through an explicit Room 2→3
migration; retain and register 1→2→3. No historical intervals are synthesized.
Migration preserves accounting, sessions, awards, cursors and history visibility.
Do not fabricate schema JSON or use destructive migration.

Extend the transactional history read with visible coverage and historical
challenge mode metadata needed for activity grouping. Do not read hidden award
ledger records back into visible history. History deletion also hides coverage
atomically, preserving enforcement and idempotency records. Break the active
coverage anchor on deletion. Further same-day writes remain hidden for the
affected day, even if monitoring reconnects or starts a new interval; later days
can appear. Persist a history-deletion cutoff so an in-flight or restarted
recorder cannot recreate same-day visible coverage. A deleted day's rows cannot
qualify for comparison or imply zero use.

Reads that cross midnight or a zone change retry rather than publishing a mixed
snapshot. Storage errors show the existing retry state, not an empty week.
Refresh after resume, activity completion, settings/deletion and date changes.

## Architecture boundaries

- Core data contracts: coverage models, history read and transactional storage
  operations, including deletion visibility.
- Core coverage recorder: accepts serialized observations and stable scope;
  emits/persists confirmed intervals without owning spending.
- Core outcomes: immutable daily/week summaries and explicit comparison
  eligibility/reasons, calculated from one history snapshot.
- Platform Room adapter: interval/scope/cutoff persistence, merging, queries and
  versioned migration. Existing repository remains the UI entry point.
- Monitoring service: source observations, ownership, lifecycle invalidation and
  the idle checkpoint. Failure handling stays on its existing serialized path.
- Compose history: render summaries and qualification text; preserve existing
  refresh/error/navigation conventions. Extract history into its own focused
  file rather than expanding the shared `ProductScreens.kt`.

## Global constraints

- T15 only; T16 integrated delivery and physical-device acceptance stay separate.
- Preserve the shared checkout, index and unrelated existing changes; no commits,
  staging, publication or installation during this planning task.
- Reuse Kotlin/JVM, Compose Material 3, Room and DataStore; add no dependency,
  permission, foreground service, wake lock, analytics or network requirement.
- Support Persian RTL, English and French with locale-aware durations, accessible
  headings/controls, mixed-number direction and non-color-only coverage labels.
- Integer milliseconds for durations; ISO local dates for stored day identity.
- Missing or hidden monitoring data must never imply zero usage or improvement.
- Keep enforcement counters, award identities and captured challenge terms intact.

## Acceptance and verification

Core tests must cover empty/legacy history, all seven dates, removed apps,
exercise-only activity, zero-usage complete days, unknown missing rows, separate
awarded/consumed time, and exactly-once combined totals. Pin comparison gating
for one incomplete day, changed targets, zero baseline, today, hidden history,
inconsistent rows, clock changes and DST boundaries.

Coverage sequence tests must cover overlaps/duplicates, maximum-gap boundaries,
restart/reboot, access loss, disabled monitoring, foreground uncertainty, screen-
off/own UI, ownership/storage/queue failures, concurrent setting changes,
midnight and deletion while recording. Verify that coverage cannot spend usage
or move the budget cursor. Assert heartbeat scheduling without real sleeps.

Prepare Room tests for migration, reopen, merged intervals, rollback and deletion
cutoff, including a late in-flight write after clear-history. Export authentic
schemas and run migration validation when Android configuration works; otherwise
record the exact unrun checks and build output.

Run the actual core suite and source/resource checks after implementation.
Attempt debug/release builds, lint and instrumentation assembly. Pending Android
execution must remain distinct from JVM/source passes. Add a T15 phone checklist
covering coverage loss/recovery, daily/weekly views, deletion/restart, all three
locales, RTL, TalkBack and large font. Do not claim camera or device accuracy from
synthetic tests.

## Handoff

Written-design review is complete. The implementation plan will name exact
files, interfaces, test assertions, verification commands and the execution
order. Product implementation starts only after the plan is reviewed and an
execution method is selected.
