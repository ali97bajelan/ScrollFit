# Gradle SDK test matrix

## Intent

Run tests against the Android SDK contract in `app/build.gradle.kts` and remove reliance on a particular phone manufacturer. The current app values are `minSdk 28`, `targetSdk 35`, and `compileSdk 37`; there is no `maxSdk`. `compileSdk` controls compilation and is not an upper runtime limit.

## Proposed approach

Keep core and app JVM tests device independent. Run Android instrumentation at API 28, API 35, and the newest available runtime API. Use Gradle-managed or connected devices, and keep each device profile separately runnable.

If a requested emulator image or device is unavailable, record that profile as `NOT RUN` and continue the remaining Gradle profiles. Executed test failures remain failures; unavailable device coverage is never reported as passing. Do not add `maxSdk` or change app compatibility.

## Alternatives considered

- Test every API level from 28 through 37. This gives broader version coverage but requires ten emulator images and treats `compileSdk 37` as a runtime boundary even though Gradle does not set `maxSdk`.
- Test only the target API 35. This is cheaper but omits the app's declared minimum API 28.

The boundary-plus-newest-runtime matrix is proposed because it covers the declared minimum and target behavior while checking newer Android behavior without inventing a maximum supported runtime.

## Acceptance

- Test commands derive API values from the Gradle configuration and report the tested API for every instrumentation run.
- Core and app JVM suites can run when no emulator or phone is connected.
- An unavailable device profile does not prevent other test profiles from running, and is recorded as `NOT RUN`.
- The full matrix retains nonzero failure status when an executed test fails.
