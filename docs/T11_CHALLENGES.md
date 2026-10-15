# T11 — challenge lifecycle and awards

T11 connects the four independent counters to the existing budget engine. Open
**کسب زمان برای یک برنامه** from the dashboard, select a configured safe target,
then choose an exercise. The production blocker displays all four current offers;
an exercise button opens the same preparation flow with its target/exercise.
**فقط ورزش، بدون پاداش زمانی** is available from the dashboard and blocker at caps.
The debug T02 blocker remains an isolated experiment without production awards.

## Terms and progress

Preparation restores the last exercise and camera from DataStore. It displays
repetitions, actual offered reward in seconds, remaining award capacity and a
reduced-reward explanation. Camera access is requested before Start. Start uses a
fresh UUID and BudgetEngine's exact displayed offer: changed rules, date, capacity
or combined balance require redisplay and another explicit tap. The stored
challenge captures target, mode, exercise, date, repetition target and reward.

The camera routes the same four serial raw-inference counters used by the trials.
Its fresh timestamped cumulative progress feeds an Android-free ChallengeSession.
Completed reps survive tracking loss. Stale, future and duplicate timestamps are
ignored; a regressed cumulative counter invalidates the session. Fresh tracking
is required before a goal can complete. Preview trials still reset their counts
and have no wallet/activity effects.

Active duration sums adjacent tracked observations with gaps no greater than
750 ms, excluding initialization/loss/background. It is conservative observed
tracking time; it is not fitness or calorie measurement. The duration can
undercount during UI coalescing or sparse inference. Only the completed target's
repetitions are retained when the goal is reached.

## Persistence and lifecycle

Completion records activity, award identity, wallet and completed status in one
transaction. Exercise-only records completed status/activity with no target,
wallet or award. Activity identity derives from the challenge; duplicate callbacks
return the existing completion without inserting another activity. Ledger/status
identities survive history deletion, so replay does not recreate cleared history.
There is no schema migration or new dependency in T11.

Success is displayed only after the operation returns its persisted result. A
storage exception offers retry with the same identity; it never announces credit.
The success screen states the actual awarded seconds and offers an explicit
return to the selected target. Base and combined usage accounting remains T05;
credit cannot bypass the combined ceiling and remains specific to one app.

Cancel/back, background/pause, rotation, camera stop/retry/lens change, model or
camera failure and navigation synchronously invalidate unfinished completion.
Cancellation writes run in application scope so leaving Compose does not cancel
cleanup. Starting while storage is suspended uses a generation check; a stale
created challenge is cancelled without accepting its progress. Preparation reloads
after interrupted reads when the screen resumes. New blocker requests reuse the
Activity and invalidate the old flow before replacing its content.

The transaction checks eligibility before writes, after writes and after its final
suspended daily cleanup. Invalidated work rolls back; failed activity inserts also
roll back the wallet/ledger/status. Transaction completion is the durable boundary:
an award already committed before interruption remains valid even if its screen
has gone away. The existing startup recovery cancels all active rows before
returning any policy/repository facade; saved awards remain intact. Cancelled
partial activity is discarded. A one-second visible-screen date check and policy
checks cancel old-day challenges; no old challenge awards new-day credit.

## Blocker launch

The unexported ChallengeActivity is opened only from a visible overlay button,
using an explicit immutable one-shot PendingIntent. API 34 sender and API 35
creator options opt in; API 36+ uses ALLOW_IF_VISIBLE. CLEAR_TOP/SINGLE_TOP and
onNewIntent prevent stacked challenge Activities from rapid taps. The original
blocker stays until an Activity actually resumes, since Android can silently
reject a launch. The dashboard provides a fallback if device/OEM launch handling
does not permit entry. No automatic service-side Activity launch was added.

The choices follow the current official [Android activity-launch guidance](https://developer.android.com/guide/components/activities/secure-bal)
and [ActivityOptions API](https://developer.android.com/reference/android/app/ActivityOptions),
checked 2026-10-15. Actual overlay visibility eligibility and OEM behavior require
device checks; an API choice is not evidence that a phone accepted a launch.

## Verification

See the current [core output](verification/T11-core-tests.log),
[baseline output](verification/T11-baseline-tests.log),
[old-engine regression](verification/T11-activity-regression-red.log),
[Android attempt](verification/T11-build.log) and
[structural checks](verification/T11-source-checks.log).
These local evidence files are ignored by Git. Final totals and actual Android
outcome are recorded in README/TASK_STATUS after the commands finish.

The JVM store is a serialized rollback adapter, not SQLite. Room reopen/duplicate
activity and trigger-induced activity-write rollback checks have been added to
BudgetPersistenceTest; their execution depends on the Android build/SDK/device.
Read-only independent review covered the accounting, lifecycle and launch paths,
and the reported preparation-resume and Activity-stacking issues were addressed.
Review is not Android compilation or hardware verification.

T12 speech, T13 short-session prompts, T14 full UI/settings and T15 outcome screens
remain outside this request. No recording, remote upload or runtime model download
is introduced. See [the pending phone checklist](T11_PHONE_TEST.md).
