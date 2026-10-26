# I07 implementation handoff

Status: **IMPLEMENTED — QA NOT RUN**  
Base revision: `1500806` (`feat(monitoring): implement I03-I05 review preparation`)  
Dirty source identity: `70fb5a9240be6db27193b39239a9c388b453ef4c9a34aaea01810e8173f0f4a2` (SHA-256 of the 26 changed/relevant added files, with handoff files and unrelated pre-existing untracked evidence excluded).

## Changed files

- `app/src/main/java/com/example/scrollfit/monitoring/MonitorDiagnostics.kt`
- `app/src/main/java/com/example/scrollfit/monitoring/MonitorRuntime.kt`
- `app/src/main/java/com/example/scrollfit/monitoring/MonitoringService.kt`
- `app/src/test/java/com/example/scrollfit/monitoring/MonitorDiagnosticsTest.kt`

## Implementation

Diagnostics are process-local and bounded: a 128-record sanitized ring, fixed per-phase failure counters, retry counts/results, queue high-water/drop counts, and count/total/max processing latency. Records contain only monotonic timestamps and closed phase/code/result enums. Writes are synchronized in memory and never suspend or write to storage.

Snapshots label their scope `Since process start`. They expose separate monotonic ages for the last successful known reconciliation and the last successfully processed selected-app observation. Wall-clock changes cannot make an age negative; a value from a prior process/boot has no restored record and is Unknown. Idle/non-target checks do not refresh the selected-app observation age. No support export or customer-facing diagnostics screen is added here.

## Fixtures written, not run

- `ringRetainsLast128SanitizedRecords`
- `snapshotSchemaDoesNotRetainPrivateEventPayloads`
- `observationAgeUsesMonotonicTimeAndBecomesUnknownAcrossReset`
- `queueDropsAndLatencyAggregateWithoutGrowingRecordHistory`
- `retriesAndResultsAreTypedBoundedEvents`

All are in `MonitorDiagnosticsTest.kt` and remain unrun.

## QA remaining

Scenarios: I07-01 through I07-03. Run the full app JVM suite and Android instrumentation when the QA phase starts:

- `./gradlew :app:testDebugUnitTest --console=plain`
- `./gradlew :app:assembleDebug :app:assembleRelease :app:lintDebug :app:assembleDebugAndroidTest :platform:assembleDebugAndroidTest --console=plain`
- `./gradlew :app:connectedDebugAndroidTest :platform:connectedDebugAndroidTest --console=plain`

Stress at least 10,000 records, confirm the retained schema and restart scope, and measure the same rapid-transition fixture planned for I09. No overhead or device-performance result is claimed.
