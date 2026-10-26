# I09 implementation handoff — metadata latency and safety snapshot

Status: **IMPLEMENTED — QA NOT RUN**. No latency, ANR, Android or device claim is made.

## Source identity

| Field | Value |
| --- | --- |
| Base revision | `a52035464db665e6acb7c586c831fbb5d8d541d7` |
| Dirty source identity | `85c35166e7063c1f4e6f3663e97d6fe680f335a2b7fa1f2e5ed9e4c5cb13461b` — sorted source/test paths and exact bytes; method and path list are in [CURRENT_VERIFICATION.md](CURRENT_VERIFICATION.md) |
| Current implementation commands | No Gradle, build, lint, emulator, instrumentation or device commands run |

Changed files: `app/src/main/java/com/example/scrollfit/monitoring/{TargetApps.kt,SafetySnapshot.kt,MonitorCommandQueue.kt,MonitorDependencies.kt,MonitoringService.kt,MonitorDiagnostics.kt,MonitorFailureBoundary.kt}`; `core/src/main/kotlin/com/example/scrollfit/core/usage/MonitoringController.kt`; `app/src/test/java/com/example/scrollfit/monitoring/{SafetySnapshotTest.kt,MonitorFailureBoundaryTest.kt}`; `app/src/androidTest/java/com/example/scrollfit/monitoring/MonitorLatencyTest.kt`; `core/src/test/kotlin/com/example/scrollfit/core/usage/MonitoringControllerTest.kt`.

## Implementation

Added immutable `SafetySnapshot` values containing eligible/protected package sets, readiness and a monotonic revision. `SafetySnapshotState` fails closed while a refresh is pending and rejects results from superseded revisions. The service callback reads the event package only, synchronously pauses/dismisses presentation, invalidates the foreground generation, and then starts suspendable package/role metadata refresh on the IO dispatcher. Published data is reconciled only for the current revision; the controller discards its prior interval when safety revision changes, so recovery begins with a fresh observation and does not backfill.

Package add/remove/change/replacement, input-method and configuration receivers request a fresh snapshot. Dynamic home/dialer roles are re-read on foreground window transitions, including return from Settings. A direct role-holder listener is not used because Android marks that listener hidden/system-only and requires `OBSERVE_ROLE_HOLDERS` ([AOSP RoleManager source](https://android.googlesource.com/platform/frameworks/base/%2B/2fffbcb7dfafdb61e2f0265e8265f66985c63147/core/java/android/app/role/RoleManager.java)); QA must verify the actual platform/OEM transition sequence. The queue remains fixed at 64 entries, pending-depth accounting is race-safe for fast consumers, and samples older than 5,000 ms or from the future are rejected.

## Fixtures written, not run

- `SafetySnapshotTest.slowMetadataDoesNotDelayDismissal`
- `SafetySnapshotTest.roleChangeWithholdsEligibilityUntilFreshSnapshotPublishes`
- `SafetySnapshotTest.staleSafetyRefreshIgnored`
- `MonitorLatencyTest.overflowAndStaleSamplesPauseWithoutSpend` — fills exactly 64 slots, rejects command 65, and rejects the 5,001 ms old sample.
- `MonitoringControllerTest.staleSamplesPauseWithoutSpendUntilFreshInterval` — confirms the stale interval yields no spend and a later fresh interval resumes safely.
- `MonitorLatencyTest.sampleAt5001MsIsRejectedAndBoundaryAt5000MsRemainsTrusted`
- `MonitorLatencyTest.timingHarnessSeparatesCallbackMetadataQueueAndPersistenceStages` — QA-only 20-sample stage fixture reports sample count, median, p95 and max using controlled 20 ms metadata/queue/persistence delays. It has not been run, and no before/after performance result exists.
- `MonitorFailureBoundaryTest.secondRegistrationFailureCleansFirstSuccessfulRegistration` and `cleanupContinuesAfterUnregisterThrowsAndDoesNotRepeat` cover the added receiver registrations/cleanup.

## QA remaining

Scenarios: I09-01 through I09-04. Run the full CORE, APP and Android instrumentation profiles from the [current verification matrix](CURRENT_VERIFICATION.md), then repeat the same rapid-switch workload against baseline and changed source on the same device/API. Record median, p95, max, sample count and evidence for callback, metadata, queue and persistence. Confirm safe dismissal before releasing a slow metadata latch, command 65 rejection, the 5,001 ms stale boundary, and no spend during uncertain/stale intervals. Intended-device qualification remains Xiaomi Mi 11 Lite NE / Android 14 API 34; no current result exists.
