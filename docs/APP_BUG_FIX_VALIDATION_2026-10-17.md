# App bug fix validation — 2026-10-17

## Status

Implementation for BUG-01 through BUG-03 is present in the working tree. The core logic and structural/resource checks pass. Android compilation, Compose instrumentation, TalkBack, camera/device checks, and seeded performance measurements were not available in this environment, so none of the three bugs is marked fully verified.

Environment: repository `master` at baseline `754b75cb9bc82969cd00bd39e9c03d7aba82e670`; Android project targets API 37/JVM 17. No Android device or `adb` executable was available. No runtime locale, theme, or font-scale result is claimed.

## Automated checks

| Check | Result | Evidence and limits |
| --- | --- | --- |
| `python3 verification/check_source.py` | PASS | XML/TOML, resource references, locale key/placeholder parity, and structural checks passed. This does not establish Android compilation or runtime behavior. The pre-existing owner changes in this script were preserved. |
| Core JVM suite via cached compiler: `JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 SCROLLFIT_KOTLIN_COMPILER_DIR=/tmp/scrollfit-kotlin-classpath python3 verification/run_core.py` | PASS — 305 tests | Compiled 62 Kotlin source/test files; 24 JUnit test classes passed, including outcome aggregation and feedback/announcement policy tests. Three existing unnecessary-`!!` compiler warnings remain. |
| `git diff --check` | PASS | No whitespace errors in tracked diff. |
| `./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug` | BLOCKED | Gradle could not start: the default wrapper cache is outside the writable workspace; a writable cached distribution was tried, but the JVM then failed with `Could not determine a usable wildcard IP for this machine`. No app build/test result is claimed. |
| `./gradlew :app:connectedDebugAndroidTest` | BLOCKED | Same Gradle startup limitation; no `adb` or Android device was available. |

## Bug acceptance status

| Bug | Implementation present | Verified here | Remaining evidence |
| --- | --- | --- | --- |
| BUG-01 — active workout controls and camera ownership | Shared workout content and camera viewport; bounded responsive layout; Help/End actions; stale callback invalidation; layout and fake-camera instrumentation tests added. The layout matrix includes both portrait font scales, a French resource-context case, and an overflowing recovery-controls scroll case that checks preview and End remain reachable. | Core-independent source/resource checks only. App tests were not compiled or run. | Run app unit/build/lint and connected layout/camera ownership tests. Check 320×480 and 640×320 dp at 100%/200% text, Persian/English/French, RTL, both challenge entry points, trial mode, camera exercises/lenses, permission/error recovery, and exit/background release on devices. |
| BUG-02 — progress semantics and stable feedback | Progress range/state description, single tracking live region, 1-second deduplicated announcement policy, immediate distinct errors, completion result live region, and touch-exploration audio policy. | Core policy tests pass in the 305-test suite. Compose semantics tests are added but uncompiled; no TalkBack/audio result is claimed. | Run app tests and Compose semantics tests. Verify TalkBack announcements, error/completion/save-failure behavior, mid-utterance/tone takeover, and unchanged saved preference on device. |
| BUG-03 — history scaling | Per-call date indexes and memoized outcomes; background snapshot projection; lazy keyed day list with shared app-name/activity lookups; regression and UI tests added. | Core aggregation regression suite passes. UI projection/list tests are not compiled or run. | Run app tests and lazy-list tests. Verify refresh/clear/day/zone races and perform the 1,095-day/21,900-activity before/after device measurement: five cold entries and five full scrolls per variant, with projection/first-content medians, peak memory, initially composed cards, and slow-frame fraction. |

## Workspace notes

The initial worktree contained unrelated modified and untracked files. They were left untouched, including the existing changes in `verification/check_source.py`, release artifacts, exported schemas, and other planning documents. A separate worktree could not be created because Git metadata is read-only in this environment. Changes remain uncommitted in the authorized workspace; the repository has no commit evidence for this implementation.
