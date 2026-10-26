# I12 implementation handoff — history scaling

Status: **IMPLEMENTED — QA NOT RUN**. The harness is prepared; no performance measurement or optimization is claimed.

## Source identity

| Field | Value |
| --- | --- |
| Base revision | `4c7bf34a3df6921a3eaa157e35bb3b7ff9e1549f` |
| Dirty harness fingerprint | `12cd25ff4198094b192df44ece68e4ebcfdc8f838c9ac612d4e85c229cb2bcb9` — SHA-256 over the three I12 Kotlin fixture/harness file checksums |
| Changed production source | None; query behavior remains unchanged pending measurement |
| Synthetic fixture | `platform/src/androidTest/kotlin/com/example/scrollfit/platform/data/HistoryScaleSeed.kt` |
| Instrumentation harnesses | `HistoryRangeTest.kt`, `HistoryPerformanceTest.kt` |
| Test status | Written, **NOT RUN** |
| QA scenarios | [I12-01–I12-03](2026-10-26-scenarios.md#i12--history-scale), all NOT RUN |

## Prepared measurement coverage

`HistoryScaleSeed` seeds a 7-day small baseline plus 365- and 1,095-day histories across 20 synthetic packages. It includes an unselected removed-app row, a hidden deletion-cutoff date, complete and partial coverage, sparse package scope, unknown-coverage dates, and UTC/Asia-Tehran zone differences. The fixture returns its expected visible-history snapshot so repository output can be compared for exact visibility and equivalence.

`HistoryRangeTest.largeHistoryBaselineRecordsLoadAggregationAndRoomQueryMetrics` captures ten post-warm-up runs for each history size. It records p95 repository load and aggregation time, Room SELECT count, coverage-target SELECT count, and sampled peak process PSS, and asserts the full visible-history result against the fixture. Proposed performance thresholds are reported as evidence and are not pass/fail assertions.

The UI harness runs matching 7-, 365-, and 1,095-day × 20-package datasets using `sevenDayBaselineCapturesHistoryProjectionAndListMetrics`, `oneYearHistoryCapturesHistoryProjectionAndListMetrics`, and `threeYearHistoryCapturesHistoryProjectionAndListMetrics`. It measures projection p95, initial content setup through idle, scroll-to-oldest latency, initially composed day-card count, Android frame-duration p95/max, dropped frame reports, and sampled peak process PSS from projection through rendering/scrolling. It checks that the oldest date remains reachable and initial composition stays lazy.

## Current source findings

- `ProductData` calls the full-range `visibleHistory()` repository API whenever the history snapshot is refreshed.
- `RoomStore.visibleCoverage()` loads every visible coverage row, then calls `coverageTargets(id)` once for each row. The harness counts those target-set reads.
- `OutcomeAggregator` indexes all supplied history in memory and builds `olderDays` for every recorded date. The history screen uses `LazyColumn`, but data loading and aggregation still cover the full history.
- `HistoryHost` discards preparation results when the snapshot identity or revision becomes stale; preserve this behavior when any bounded reads are introduced.

The plan requires profiling before query changes and reserves an optimization for a return from QA with a measured trigger. No baseline timing, query count, peak-memory, or frame-stall evidence exists yet, so repository and UI behavior remain unchanged.

Known measurement risks: use the intended phone and record OS/build, charging/thermal state, and competing foreground load. Android frame callbacks and 10 ms process-PSS sampling are observational and may miss brief peaks; preserve raw test logs with the run and interpret values across repeated controlled runs. The I01/stable-outcomes QA prerequisite also remains unaccepted.

## QA handoff

Run the task-filtered instrumentation fixtures on the intended device, then retain complete result XML and logs:

- `./gradlew :platform:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.example.scrollfit.platform.data.HistoryRangeTest --console=plain`
- `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.example.scrollfit.ui.HistoryPerformanceTest --console=plain`
- `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.example.scrollfit.ui.HistoryLazyListTest --console=plain` — includes stale projection rejection coverage for I12-03.

Record exact environment and repetitions, p95 history load/aggregate time, Room query counts, peak memory, and visible frame stalls. Compare results with the proposed 1-second p95/stalled-interaction/per-row-query trigger. If any optimization is warranted, return to Implementation, then rerun bounded-window visibility/comparison equivalence, query-count, clear/midnight/zone-staleness, and performance scenarios before acceptance. Keep the required scenario set and any broader device gates unfiltered; targeted fixture commands alone do not establish task acceptance.
