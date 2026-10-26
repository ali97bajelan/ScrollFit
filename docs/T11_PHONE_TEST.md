# T11 phone verification — pending

Intended phone: physical Android 14 / API 34 phone. No phone result is
recorded. Android build/SDK prerequisites and the actual APK path must be resolved
before these steps can run. Build with the README command; install the resulting
`app/build/outputs/apk/debug/app-debug.apk` only after BUILD SUCCESSFUL. This file
is an expected output, not a delivered APK.

1. Configure two safe targets with short allowances in production monitoring.
   Enable the production service; stop the independent debug spike. Confirm that
   app use, screen-off and camera time follow the T05 checklist.
2. From dashboard **کسب زمان برای یک برنامه**, confirm target selection comes
   first. Choose A, then each exercise. Read reps/reward/capacity before Start.
   Grant camera access. Choose front/rear directly; revisit to check persistence.
3. Complete each of push-ups, squats, jumping jacks and knee raises in the displayed
   camera position. For every exercise, record visible counts, actual repetitions,
   captured reward and final saved reward separately. Confirm A alone gets time.
   Return explicitly to A; spend its credit and check that blocking resumes.
4. Exhaust A. Check that its production blocker offers all four exercises with
   terms; directly open and complete each. Check that every award is saved once
   and only to A. Rapidly tap the exercise button twice; verify one Activity/flow
   remains, Back closes it without revealing a second challenge screen.
5. Repeat while monitoring is disabled: advance earning still uses saved selected
   targets and policy; no claim of enforcement should appear.
6. Perform one completed rep, hide required joints, then reappear. Check retained
   completed progress, positioning feedback, no phantom rep and successful fresh
   complete cycles. Cover/uncover camera quickly and sustain loss.
7. At partial progress and near the final rep, separately test Back/Cancel, Home,
   screen lock, another app, rotation (including 180°), lens change, retry and
   camera access revocation. Camera must stop; incomplete challenges must never
   acquire new credit from late callbacks. A reward already committed before the
   interruption should stay saved. Retry failed camera setup with a new challenge.
8. Background during initial loading, target/exercise offer loading and Start.
   Resume and check preparation recovers. A stale started challenge must not reuse
   old counts or complete in the background. Open another blocker exercise while
   an earlier screen exists; the old flow must be cancelled before replacement.
9. Force stop/kill at partial progress; relaunch. Confirm no resumable earning
   progress and no credit. Kill just after persisted success; credit survives.
   Revisit, rotate and navigate repeatedly after success; no duplicate credit.
10. Near the per-app award cap, confirm reduced reward is shown before Start.
    Change terms between displaying and starting (using a debugger/test fixture
    only if necessary); a changed offer requires another explicit tap. At the
    combined ceiling or zero award capacity, confirm reward refusal.
11. At both caps, open **فقط ورزش، بدون پاداش زمانی** from dashboard and blocker.
    Complete a target; confirm saved activity and no target wallet/award. Cancel a
    second partial trial; no partial reward or retroactive conversion is possible.
    T15 history presentation is pending; inspect local Room records for activity.
12. Cross actual local midnight during a challenge. It must end without new-day
    credit from the old identity. Existing awards expire per T04. Record the old
    and new dates, stored status and both day balances. Clock-abuse hardening is
    outside v1.
13. Use Android instrumentation to run concurrent completion/reopen and activity
    rollback tests. Inject storage failure during completion; no success should
    appear before a saved result, and retry must preserve the same identity.
14. Turn networking off before the first camera launch after installation. Confirm
    local inference, no model download, camera release and no saved recordings.
    If Android rejects overlay-to-Activity entry, preserve the blocker, record
    Logcat BAL messages and use dashboard earning; do not mark direct entry passed.

15. Switch between Persian, English and French. Check the new challenge terms,
    progress, success, refusal and error text, numeric formatting and screen
    direction. Persian should remain RTL; English and French should use LTR.

Record: date/time, build hash, Android version, camera, exercise, mode, target,
expected/observed reps, displayed/saved reward, activity duration, interruptions,
actual result, logs and manufacturer restrictions observed. Every item above is
**pending**; synthetic JVM tests do not establish camera accuracy or enforcement.
