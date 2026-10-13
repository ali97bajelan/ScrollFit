# T08 squat phone checks — all pending

Intended device: Xiaomi Mi 11 Lite NE, Android 14. Android compilation is
blocked and no installable APK is available. Complete the
[T06 camera checks](T06_PHONE_TEST.md) before these actual-device trials.
Synthetic sequences validate detector logic, not physical accuracy.

1. Open Exercises → Squat → **باز کردن دوربین**. Fix the phone in front
   or at a slight angle, with both shoulders, hips, knees and ankles in
   frame. Confirm Persian guidance, a zero count and the no-award notice.
   Stand still briefly, lower and stand again, then hold standing. Confirm
   one count per full cycle and no repeated count during endpoint holds.
   Record manually observed and displayed counts over several cycles.
2. Test front and slight-angle placement separately, with rear/front lenses
   and portrait/landscape. Front preview mirroring must preserve counts.
   Record camera height/distance/tilt, lighting and any threshold misses.
   Repeat with different comfortable movement depths; no professional
   posture or prescribed depth is required.
3. Start lowered, then stand: confirm zero until another full cycle.
   Try small partial dips, incomplete returns, endpoint jitter and moving
   only one leg. Confirm no extra counts. Return to standing and perform
   a fresh full cycle to verify recovery.
4. Hide a required joint or either leg during a partial squat, step out
   of frame, and reappear standing or lowered. Confirm useful tracking
   feedback, retained completed counts and no phantom repetition. Stand
   still to reacquire the reference and complete a new cycle.
5. Hold lowered beyond ten seconds and return. Confirm no count for the
   expired cycle and successful counting after a fresh standing baseline.
   Record excessive-speed misses separately; timing is provisional.
6. Background and immediately reopen, lock/unlock, rotate, change lenses
   and retry after an error. Camera restarts must clear the trial and its
   partial movement, including rapid pause/resume. Confirm camera shutdown
   on pause/exit and no queued late count after stopping.
7. Observe several minutes in good and poorer light. Record tracking gaps,
   count delays, false positives and misses. Completed counts must stay
   visible when a tracking-loss update hides stale geometry during the
   same running trial. Test fixed-body distance changes: uniform image
   scale changes alone should not add counts.
8. Run after installation in airplane mode. Confirm local counting and
   no budget, reward or activity-history changes. Closing/reopening and
   process restart start this temporary trial at zero. Durable challenge
   and reward integration remains T11.

Record APK version, device/OS, lens, orientation, placement, lighting,
manual versus displayed counts, loss/reappearance, calibration recovery,
lifecycle resets and reproducible failures. Device trial status:
**not run — Android build/SDK unavailable**. No physical result is claimed.
