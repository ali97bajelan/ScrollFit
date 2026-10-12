# T03 durable data and preferences

Scope is T03 only. T02 enforcement remains unverified on the intended device. T04 spending/eligibility and T11 detector/lifecycle integration are separate tasks; this milestone has no new user-facing budget controls.

## Storage and ownership

`ScrollFitApplication.persistence` owns one lazy `ExerciseGatePersistence`. Its suspend `repository()` cancels challenges left ACTIVE by an earlier process before returning the repository. Completed awards survive this recovery. Callers must obtain the repository through this entry point rather than constructing their own app instance. Database reads/writes run through suspend Room APIs, never on the main thread with `allowMainThreadQueries`.

Room v1, file `exercise-gate.db`, contains:

| Table | Identity and purpose |
| --- | --- |
| `targets` | Package primary key; selected flag, base allowance, per-app daily award cap. |
| `daily_usage` | Package + ISO local date; base spent, earned spent, total awarded, history visibility. |
| `combined_usage` | ISO date; cumulative usage across apps, including apps removed later. |
| `challenges` | Unique ID; target or exercise-only, exercise/mode, immutable repetition/reward/start-date terms, terminal status. |
| `award_ledger` | Challenge ID primary key; immutable target/day/amount and award timestamp. |
| `exercise_activity` | Unique activity ID; optional challenge, exercise, repetitions, active duration, wall timestamp. No camera media. |
| `sessions` | Package + date; accumulated foreground duration and pending-prompt flag. |

Foreign keys use no deletion cascades. Target removal flips `selected=false`; re-addition updates configuration without replacing dependent records. Target caps live in Room alongside configuration so a cap edit and an award decision serialize in the same database. They are preferences, with one source of truth rather than a duplicate DataStore copy.

DataStore file `exercise-gate-preferences.preferences_pb` stores all four reward rules, combined ceiling, zero-base mode, speech, camera, last exercise and session boundary. Defaults are the documented configurable product proposals. Transforms run against the latest stored preferences inside one atomic edit. Invalid values and storage/corruption errors propagate; no silent replacement/reset is configured. Enum names and preference keys form the persisted format and must remain stable or be explicitly migrated.

Budget/award/session durations use signed 64-bit integer **milliseconds**. Existing `RewardRule.rewardSeconds` remains explicitly **seconds**, with validated overflow-safe conversion to milliseconds when a challenge is prepared. Epoch timestamps are milliseconds; dates are local ISO `yyyy-MM-dd`. `WallClock` and `MonotonicClock` are injectable through `ExerciseGatePersistence`. Monotonic checkpoints are not replayed across reboot; future T04/T05 accounting must use monotonic deltas and persist accumulated counters.

## Atomic operations and caller boundaries

- `recordUsage` accepts a policy-approved interval once and updates app, combined and session counters in one transaction. Positive deltas and integer overflow are validated; earned spending cannot exceed recorded awards. T04 must supply base-first spending, interval deduplication, day splitting and ceiling decisions; this storage API is not a foreground monitor.
- `awardCompletedChallenge` reads the immutable challenge, checks its active/start-day/completion and current app cap, inserts the unique ledger identity, increments awarded total and marks completion in one transaction. A retry returns the already persisted award without incrementing again. An old-day incomplete challenge cancels; exercise-only activity cannot award time. T04 must also implement combined-ceiling eligibility and advertised reduced rewards before challenge creation. T11 must provide actual verified progress.
- `startChallenge` uses an aborting insert so an existing ID cannot replace its reward snapshot. No caller-provided timestamp or UI completion animation grants an award by itself.
- Process recovery cancels incomplete challenges, leaving ledger/wallet values intact. Session duration and pending prompt survive restart; T13 owns explicit Continue/Finish transitions.
- `clearHistory` deletes exercise activity and hides existing app/combined history. It retains accounting totals (including past dates), session state, challenge terms/statuses and ledger identities. Today cannot gain budget or new award capacity from history deletion. Retaining past counters also avoids a reset if the clock moves back to an earlier recorded date. Hidden daily records remain hidden while receiving later updates; a new date has new visible records. This control clears visible history, not every enforcement record on disk. No deletion UI is introduced until T14.

## Migration behavior

This is the first production database; there is no previous Room schema to migrate. The T02 SharedPreferences 30-second experiment remains separate and is never imported as a daily allowance or award. Room exports v1 into `platform/schemas` via KSP. **The schema JSON is not yet generated**, because Android configuration fails before KSP executes; do not invent it. The first successful build must retain the generated v1 JSON in version control.

There is no destructive migration or downgrade fallback. An unsupported version fails visibly at repository access, retaining the file rather than resetting budgets. Every future schema version must include an explicit data-preserving migration and tests using the generated old/new schemas. Keep award identities, current and retained day counters, configuration and session state through migrations. Changing stable enum names or preference keys also requires an explicit preferences/database conversion.

## Verification and next commands

Executed JVM command (outside sandbox because Gradle needs a local socket):

```bash
env JAVA_HOME=/tmp/scrollfit-toolchain/jdk-21.0.12.1+1 \
  GRADLE_USER_HOME=/tmp/scrollfit-gradle \
  ./gradlew -p verification/core :core:test --offline --console=plain --stacktrace
```

Result: **22 JVM tests passed**: 12 new repository/model tests and 10 existing spike tests, no failures/errors/skips. The memory transaction adapter tests core contracts and rollback behavior; it does **not** prove Room/SQLite durability or DataStore persistence. See [log](verification/T03-core-tests.log) and [repository JUnit XML](verification/T03-repository-tests.xml).

Actual Android attempt:

```bash
env JAVA_HOME=/tmp/scrollfit-toolchain/jdk-21.0.12.1+1 \
  GRADLE_USER_HOME=/tmp/scrollfit-gradle \
  ./gradlew :app:assembleDebug :app:lintDebug \
    :platform:assembleDebugAndroidTest :platform:connectedDebugAndroidTest \
    --offline --console=plain
```

Exit **1**, `BUILD FAILED in 3s`: AGP 9.4.0 could not resolve from the offline cache during root-project configuration. Compilation, KSP/schema export, Android lint and instrumentation did not execute. No APK was produced. Earlier SDK/Google Maven download blockers remain documented; this offline attempt does not establish current server reachability. One automatic permission-review attempt timed out before execution; the authorized retry ran and produced [this build log](verification/T03-build.log).

Pending tests: **7 Room tests**, covering file reopen, 24 simultaneous completion callbacks, transactional rollback, settings/re-addition/history retention, duplicate challenge identity, startup cancellation and unsupported-version failure without deletion; **1 DataStore test**, covering all preferences, concurrent edits, invalid values and file reopen. On a configured SDK/device with dependencies reachable:

```bash
./gradlew :app:assembleDebug :app:lintDebug :core:test \
  :platform:assembleDebugAndroidTest :platform:connectedDebugAndroidTest --console=plain
```

Inspect and retain generated v1 schema, fix any KSP/Android compilation failures, and require the eight instrumentation tests to pass before changing T03 to verified. No intended-device or emulator persistence result is claimed.

Official references checked on 2026-10-12: [Room](https://developer.android.com/training/data-storage/room), [data-preserving migrations](https://developer.android.com/training/data-storage/room/migrating-db-versions), [Room database tests](https://developer.android.com/training/data-storage/room/testing-db), [atomic DataStore edits](https://developer.android.com/topic/libraries/architecture/datastore), [KSP 2.3.12 release](https://github.com/google/ksp/releases/tag/2.3.12), [AndroidX Test 1.7.0](https://developer.android.com/jetpack/androidx/releases/test). Compatibility of the Android artifacts is pending actual resolution/compilation.
