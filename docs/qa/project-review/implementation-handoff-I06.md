# I06 implementation handoff

Status: **IMPLEMENTED — QA NOT RUN**  
Base revision: `1500806` (`feat(monitoring): implement I03-I05 review preparation`)  
Dirty source identity: `70fb5a9240be6db27193b39239a9c388b453ef4c9a34aaea01810e8173f0f4a2` (SHA-256 of the 26 changed/relevant added files, with handoff files and unrelated pre-existing untracked evidence excluded).

## Changed files

- `app/src/main/java/com/example/scrollfit/monitoring/MonitoringService.kt`
- `app/src/main/java/com/example/scrollfit/monitoring/MonitorRuntime.kt`
- `app/src/main/java/com/example/scrollfit/monitoring/MonitorDependencies.kt`
- `app/src/main/java/com/example/scrollfit/monitoring/MonitorLifecycle.kt`
- `app/src/main/java/com/example/scrollfit/monitoring/MonitorFailureBoundary.kt`
- `app/src/main/java/com/example/scrollfit/monitoring/MonitorAccess.kt`
- `app/src/main/java/com/example/scrollfit/monitoring/MonitoringSetup.kt`
- `app/src/main/res/values/monitoring_strings.xml`
- `app/src/main/res/values-en/monitoring_strings.xml`
- `app/src/main/res/values-fr/monitoring_strings.xml`
- `app/src/test/java/com/example/scrollfit/monitoring/MonitorFailureBoundaryTest.kt`
- `app/src/androidTest/java/com/example/scrollfit/monitoring/MonitoringServiceLifecycleTest.kt`

## Implementation

Failures cross the runtime boundary as a coarse localized category plus typed phase/code. Exception messages and event payloads stay out of `MonitorStatus`. Initialization, preferences, metadata, reconciliation, queue processing, budget/coverage writes, overlays and navigation have guarded exception boundaries; coroutine cancellation still propagates.

Usage-listener and screen-receiver registration are tracked independently. A second-registration failure removes only the first successful registration. Cleanup attempts both unregistrations even if one throws. An unconfirmed overlay removal retains the ownership lease so another service cannot attach a second blocker.

## Fixtures written, not run

- JVM: `nonSecurityReconcileFailureIsTypedAndSanitized`, `secondRegistrationFailureCleansFirstSuccessfulRegistration`, `cleanupContinuesAfterUnregisterThrowsAndDoesNotRepeat`, `cancellationPropagatesFromAsynchronousBoundary`, and `removalFailureRetainsOwnershipLease` in `MonitorFailureBoundaryTest.kt`.
- Android runner: `typedStartupFailureCrossesTheServiceLifecycleWithoutItsExceptionText` in `MonitoringServiceLifecycleTest.kt`.
- Existing I05 lifecycle and service fixtures remain in place.

## QA remaining

Scenarios: I06-01 through I06-04. Run the full profiles; implementation tests were not executed:

- `./gradlew :app:testDebugUnitTest --console=plain`
- `./gradlew -p verification/core :core:test --console=plain`
- `./gradlew :app:assembleDebug :app:assembleRelease :app:lintDebug :app:assembleDebugAndroidTest :platform:assembleDebugAndroidTest --console=plain`
- `./gradlew :app:connectedDebugAndroidTest :platform:connectedDebugAndroidTest --console=plain`

Verify non-security reconciliation failure and retry, partial registration rollback, cancellation, safe-surface dismissal during suspended storage, and failed overlay removal on an Android runner/device. This handoff does not claim service/device acceptance.
