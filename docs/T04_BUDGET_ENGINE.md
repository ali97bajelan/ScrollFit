# T04 budget and reward engine

Implement Task 4 only; production monitoring (T05), detectors and UI integration
(T11) remain separate. Existing uncommitted work stays in this checkout, and no
git commit is requested.

The Android-free `BudgetEngine` uses serialized `DurableStore` transactions.
Budget reads derive base remaining, earned remaining, awarded capacity and
combined remaining from durable daily records. Spending clips to usable budget,
consumes base first, and updates app, combined and session records together.
Settings never clear counters. Zero-base suppresses free time without deleting
previous base consumption. Credit and award capacity belong to one app/date.

Reward offers capture exercise, mode, repetitions, reward and date before start.
Start rechecks the displayed offer atomically; a changed offer must be displayed
again. Both earning modes share policy. Completion checks persisted terms, current
app selection/cap and combined ceiling before inserting one immutable award.
Cap reductions never silently reduce an already displayed reward; an unavailable
reward is refused. Exercise-only completion grants no time.

The application receives a delegated `DataRepository` facade without raw usage,
challenge-start or award writes. It uses `budgetEngine(timelineId)` for policy,
after process-recovery cancellation finishes. Raw T03 methods remain storage
primitives for contract fixtures, outside the application-facing facade.

Every engine operation cancels active challenges from other dates. Budget/offer
reads repeat for the current date if storage I/O crosses midnight, and spending,
start and completion check the date again after their decision reads. Old-day
challenges cannot award on the new day, even after the clock returns to their
original date. Recorded activity can retain the original challenge date.
Daily rows are retained; returning to an earlier date reuses its counters.
Clock manipulation is not hardened against deliberate abuse.

Room v2 adds a cursor per monotonic timeline (boot). The cursor advances even
when no budget is spent, rejecting duplicate/overlapping callbacks across app
switches and process restarts. The monitor supplies a stable boot identity,
chronological, non-overlapping, eligible foreground intervals and their local
date. It must split/flush intervals at midnight and exclude screen-off, blocker,
camera and unrestricted time. Old-date intervals are rejected rather than
charged to a fresh day. A reboot uses a new timeline but preserves daily counters.
Intervals ending in the future are rejected. T05 owns event reconciliation,
boot identity, rollover splitting and the accounting-loss bound; this engine
does not establish Android enforcement reliability.

DataStore and Room have no shared transaction. One immutable preference snapshot
is read inside each serialized budget operation; a concurrent preference edit
applies to the next operation. Room target-cap changes serialize with awards.
The Room v1→v2 migration only creates the cursor table; it preserves all existing
records. Generated schema/migration instrumentation remain pending Android tools.

Implementation checklist:

- [x] Add policy result types and durable cursor contract with rollback adapter.
- [x] Add spending/offer/start/completion engine and meaningful JVM cases.
- [x] Add Room cursor storage, preserving migration and application factory.
- [x] Run JVM suite, Android build/lint/instrumentation attempt and source checks.
- [x] Review changes; update README/status/evidence and T04 commit message.


Verification (2026-10-12, Asia/Tehran):

- Final command: `env JAVA_HOME=/tmp/scrollfit-toolchain/jdk-21.0.12.1+1 GRADLE_USER_HOME=/tmp/scrollfit-gradle ./gradlew -p verification/core :core:test --offline --console=plain`.
  Exit **0**, `BUILD SUCCESSFUL in 1m 9s`; **49 tests**, zero failures/errors/skips.
  Includes 27 new policy cases, plus 22 existing cases. [Log](verification/T04-core-tests.log),
  [budget/reward XML](verification/T04-TEST-com.example.scrollfit.core.budget.BudgetEngineTest.xml),
  [totals](verification/T04-test-summary.txt).
- Four rollover regression tests were observed failing before their fixes:
  [decision-write regressions](verification/T04-midnight-regression-red.log) and
  [review-read regressions](verification/T04-review-regression-red.log). All pass now.
- Read-only independent review found stale dates during budget/offer reads and
  the raw production repository policy bypass; both were fixed. Late rollback
  and concurrent-callback tests were also added. The JVM adapter serializes with
  a semaphore; this does not establish Room/SQLite concurrency behavior.
- Android attempt: `env JAVA_HOME=/tmp/scrollfit-toolchain/jdk-21.0.12.1+1 GRADLE_USER_HOME=/tmp/scrollfit-gradle ./gradlew :app:assembleDebug :app:lintDebug :platform:assembleDebugAndroidTest :platform:connectedDebugAndroidTest --offline --console=plain`.
  Exit **1**, `BUILD FAILED in 2s`, unavailable offline AGP 9.4.0 before Android
  compilation/KSP/lint/tests. [Log](verification/T04-build.log). No APK.
- Six new Room tests are prepared, **unrun**. The v1-shape migration fixture needs
  confirmation with authentic generated v1/v2 schema exports and MigrationTestHelper
  once Android builds work. Eight prior Room/DataStore checks are also pending.
- [Source checks](verification/T04-source-checks.log) validate XML/TOML, adapter and
  application API structure, and execute the migration SQL on SQLite with retained
  representative data. They do not substitute for Android compilation or Room
  schema validation. [Final whitespace check](verification/T04-whitespace.log).
- Android enforcement, boot identity generation, event ordering, midnight interval
  splitting, session prompts and camera-to-activity integration remain later-task
  work. Existing credit cannot bypass the ceiling; exercise-only never awards time.

T04 is **implemented with JVM verification**; Android persistence verification is
pending. Commit message: [T04_COMMIT_MESSAGE.txt](T04_COMMIT_MESSAGE.txt).
