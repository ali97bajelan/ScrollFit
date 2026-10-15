# T13 short usage sessions

Scope: the next ordered task after T12. T14–T16 remain outside this request.
Preserve existing workspace changes and the index; prepare commit text without
creating a commit.

The existing per-app, per-local-day `SessionState` and global DataStore
`sessionBoundaryMs` implement an editable five-minute default for selected apps.
Each app accumulates its own foreground time across switching, screen pauses,
process restart, monitoring toggles and remove/re-add. Midnight selects a fresh
daily row. Continue and Finish explicitly reset only the displayed session.

Spending updates the wallet, combined total, boot cursor and session atomically,
clipping to both budget and session time. A pending prompt stays pending across
boundary edits. Budget exhaustion takes priority, including an exact collision.
Prompt time never spends base or earned credit. Continue checks current available
budget and resets the window without exercise or awards. Finish requests Home
before resetting, keeping the overlay and window if the request is rejected.
Actual navigation completion remains a device acceptance check.

Actions carry the displayed session plus the day's cumulative app usage so a
late or duplicate tap cannot reset a later window. Resets advance the boot cursor
to discard older queued intervals; the monitor reconciles afresh after a reset.
The existing Room schema suffices; no migration or dependency change is needed.

Implementation/verification checklist:

- [x] Tests for clipping, pending persistence, switches/restart, earned credit,
  Continue/Finish, stale actions, settings edits, budget collisions and midnight.
- [x] Atomic engine policy and monitor decisions/tick scheduling.
- [x] Serialized service actions, overlay controls and localized boundary editor.
- [x] Room reopen/rollback checks prepared; full JVM suite and Android attempt.
- [x] Review, README/status, phone checklist and generated commit message.

Official references checked 2026-10-16: [Room transactions](https://developer.android.com/reference/androidx/room/RoomDatabaseKt)
roll back on exceptions/cancellation; the existing [accessibility overlay type](https://developer.android.com/reference/android/view/WindowManager.LayoutParams#TYPE_ACCESSIBILITY_OVERLAY)
is supported for a connected accessibility service. These API references do not
establish intended-device enforcement or OEM behavior.

## Verification results

- Direct Kotlin 2.4.10 / JUnit 4.13.2 / Temurin Java 21 compilation and execution
  of all actual core sources/tests: **267 tests passed**, zero failures.
  Command: `python3 /tmp/scrollfit-toolchain/run-core.py current`; exit 0.
  [Log](verification/T13-core-tests.log). This is 23 new session tests plus
  244 existing checks. The temporary runner/toolchain are outside the repository;
  use the isolated Gradle command below for the project-native equivalent.
- The initial seven session regressions failed on the pre-T13 sources:
  **251 run, 7 failures**. [Actual red log](verification/T13-session-red.log).
  Once spending became clipped, the old session-overflow fixture correctly
  stopped overflowing. Its rollback test now injects a failing session write,
  still proving rollback of wallet/combined/cursor writes; the Room fixture uses
  a real SQLite abort trigger. No Android execution is claimed for that fixture.
- Project-native final command:
  `env JAVA_HOME=/tmp/scrollfit-toolchain/jdk-21.0.12.1+1 GRADLE_USER_HOME=/tmp/scrollfit-gradle ./gradlew -p verification/core :core:test --rerun-tasks --offline --console=plain`.
  Exit **0**, `BUILD SUCCESSFUL in 6m 26s`; fresh JUnit XML confirms
  **267 tests, zero failures/errors/skips** (23 session + 244 existing).
  [Gradle log](verification/T13-gradle-core-tests.log),
  [totals](verification/T13-test-summary.txt), [session XML](verification/T13-session-tests.xml).
  The sandbox first refused
  Gradle's local lock socket; outside-sandbox baseline execution exited 0.
- Android command with the same JAVA_HOME/GRADLE_USER_HOME:
  `./gradlew :app:assembleDebug :app:assembleRelease :app:lintDebug :platform:assembleDebugAndroidTest --offline --console=plain`.
  Exit **1**, `BUILD FAILED in 9s`: offline AGP 9.4.0 plugin resolution failed
  before Android compilation, lint, KSP or instrumentation assembly.
  [Build log](verification/T13-build.log). No APK, fresh Google Maven
  reachability result or physical-device result is claimed.
- XML/TOML, main/debug/release resource references, private challenge Activity,
  restricted accessibility capabilities and whitespace passed.
  [Source checks](verification/T13-source-checks.log),
  [translation/placeholder checks](verification/T13-localization-checks.log).
  Persian, English and French each contain all six new session strings.
- Independent read-only source review found no Critical/Important issues.
  The reviewer did not independently execute the tests. Overlay event ordering,
  successful Home navigation, Android API/resource compilation and actual Room
  durability remain unverified; they require the Android environment/device.
- Two new file-backed Room tests cover prompt/Continue across reopen and reset
  rollback; the existing spending rollback check now injects a session-write
  failure. DataStore's reopening test covers session preference persistence and
  now rejects zero boundary edits. All these Android tests are prepared, unrun.

T13 is **implemented, not device-verified**. Monitoring uses the existing
one-second checkpoints, shortened to the remaining session/budget interval.
Abrupt termination can still lose approximately the last checkpoint plus
scheduler/storage delay; unknown downtime is not backfilled. The debug-only T02
spike retains its separate timer and does not exercise this session policy.
See [pending phone checks](T13_PHONE_TEST.md) and
[generated commit message](T13_COMMIT_MESSAGE.txt). No git commit was created.

