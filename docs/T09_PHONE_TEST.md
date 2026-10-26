# T09 jumping-jack phone checks — all pending

Intended device: physical Android 14 phone. No installable APK or
device trial is available. Complete the [T06 camera checks](T06_PHONE_TEST.md)
before these trials. Synthetic sequences establish detector logic, not
physical accuracy or native inference performance.

1. Open Exercises → **پروانه** → **باز کردن دوربین**. Fix the phone in
   front with both overhead hands, shoulders, hips and ankles in frame.
   Confirm Persian guidance, zero count and the temporary/no-award notice.
   Hold feet together and arms down briefly, open both arms and feet,
   then close both. Confirm one count and no repeated counts on holds.
2. Record manually observed versus displayed counts over several cycles
   at comfortable speeds. Test rear/front lenses and portrait/landscape.
   Front preview mirroring must preserve counting. Record camera distance,
   height, tilt, lighting, count delay and any threshold misses.
3. Start open, then close: confirm zero until a fresh full cycle. Test
   arms only, feet only, one raised arm, one outward foot, small arm/foot
   movements and incomplete return. Confirm no extra counts, then recover
   with a full cycle. Do not treat these checks as professional form grading.
4. Try noisy motion around endpoints and raise arms with feet together,
   then lower arms while spreading feet. If those never form a simultaneous
   open endpoint, no count should appear. Record false positives and misses.
5. Hide each required joint during open movement, step out of frame, and
   reappear open or closed. Confirm tracking feedback, retained completed
   counts and no phantom repetition. Close briefly and complete a new cycle.
6. Hold open beyond five seconds, then close: confirm no expired-cycle
   count and recovery on a fresh cycle. Record excessively fast movement
   separately; timing and geometric thresholds remain provisional.
7. Background and immediately reopen, lock/unlock, exit, rotate, change
   lenses and retry after an error. Confirm camera shutdown on pause/exit,
   a zero trial after restart, and no queued late count after stopping.
8. Run for several minutes in good and poorer light. Check count retention
   when tracking feedback hides stale geometry. Change body distance while
   holding the same posture: uniform scale changes alone must not count.
9. Run after installation in airplane mode. Confirm local counting and no
   budget/reward/activity-history changes. Process restart starts this trial
   at zero. Durable challenges and awards remain T11.

## Trial record

| Field | Observed result |
| --- | --- |
| APK/version, device/OS, date | Pending |
| Lens/orientation, placement, lighting | Pending |
| Manual/displayed complete counts and delay | Pending |
| Partial/noisy/asymmetric motion | Pending |
| Loss/reappearance and recovery | Pending |
| Lifecycle reset/camera release/offline behavior | Pending |
| Reproducible failures and threshold adjustments | Pending |

Status: **not run — Android build/SDK unavailable**. Do not mark T09
device-verified until actual observed results are recorded.
