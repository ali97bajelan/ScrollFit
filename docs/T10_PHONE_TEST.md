# T10 knee-raise phone checks — all pending

Intended device: physical Android 14 phone. No installable APK or
device trial is available. Complete [T06 camera checks](T06_PHONE_TEST.md)
before these trials. Synthetic tests establish detector logic, not physical
accuracy or native inference performance.

1. Open Exercises → knee raise → **باز کردن دوربین**. Fix the phone in
   front with shoulders, hips, both knees and ankles visible. Confirm
   Persian guidance, zero count, tracking feedback and temporary/no-award
   notice. Start standing; lift the left knee, return and stand briefly;
   then lift the right knee and return. Each full return should count once.
2. Record manually observed versus displayed counts for alternating raises
   and several complete same-side cycles at comfortable speeds. Test rear
   and front lenses, portrait and landscape. Record placement, lighting,
   count delay, false positives and missed repetitions. Mirroring must
   preserve counting.
3. Hold each knee raised for two or three seconds: count should remain
   unchanged until return, and standing holds should not add more counts.
   Starting with a raised knee and returning should not count; begin a fresh
   full cycle after standing. Hold raised beyond five seconds and return:
   the expired cycle should not count; a fresh cycle should recover.
4. Test small/partial lifts, incomplete lowering, noisy movement near lift
   and return endpoints, and moving the other leg before a stable return.
   Confirm no extra count. This is a movement check, not form grading.
5. Hide each required joint, step out of frame during a lift, and reappear
   standing or raised. Confirm tracking feedback and retention of completed
   counts with no phantom repetition. Stand briefly and try a fresh cycle.
6. Background/reopen, lock/unlock, exit, rotate, change lenses and retry
   after an error. Confirm camera release on pause/exit, zero trial count
   after restart and no queued count after stopping.
7. Run for several minutes in good and poorer light. Change distance while
   keeping the same posture: uniform body scale changes alone should not
   count. Record camera tilt/perspective and body-proportion limitations.
8. Run after installation in airplane mode. Confirm local counting and no
   budget/reward/activity-history change. Process restart starts at zero;
   durable challenge lifecycle and awards remain T11.

## Trial record

| Field | Observed result |
| --- | --- |
| APK/version, device/OS, date | Pending |
| Lens/orientation, placement, lighting | Pending |
| Manual/displayed left/right complete counts and delay | Pending |
| Sustained holds, partial/noisy motion and side switches | Pending |
| Loss/reappearance and recovery | Pending |
| Lifecycle reset, camera release and offline behavior | Pending |
| Reproducible failures and threshold adjustments | Pending |

Status: **not run — Android build/SDK unavailable**. Do not mark T10
device-verified until actual observed results are recorded.
