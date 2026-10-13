# T07 push-up phone checks — all pending

Intended device: Xiaomi Mi 11 Lite NE, Android 14. No installable APK is
available while Android compilation is blocked. First complete the
[T06 camera checks](T06_PHONE_TEST.md), then run these trials on an actual
built APK. Synthetic sequences validate detector logic, not phone accuracy.

1. Open Exercises → Push-up → **باز کردن دوربین**. Put the phone in a fixed
   side view, with the same side's shoulder, elbow, wrist and hip visible.
   Confirm Persian placement guidance and a zero trial count. Start up,
   perform a complete down-and-up movement, then hold up. Confirm one count
   and no repeated count while holding. Repeat several complete cycles and
   record observed versus displayed counts.
2. Start the trial down and return up; confirm zero. Try small/partial
   movements and endpoint jitter, then a full cycle. Record misses or extra
   counts. Test either visible side separately, rear/front lenses and
   portrait/landscape placement. Front preview mirroring must not change
   anatomical side handling.
3. Hide the wrist, elbow or hip, leave the frame, or reduce visibility
   during a partial movement. Confirm useful tracking feedback, no new
   count when returning up, and retained completed counts. Re-establish
   up, then perform a fresh full cycle. Switch which side is visible during
   a partial movement and confirm the partial cycle is discarded.
4. Hold down beyond ten seconds, return up, then perform a fresh full
   cycle. Confirm the long incomplete cycle adds nothing and fresh cycles
   still work. Note that timing thresholds are provisional and need
   tuning from observed results.
5. Background and immediately reopen the app, lock/unlock, change lenses,
   rotate the display and use Retry after an error. Each camera restart
   must reset the temporary trial to zero and discard partial movement,
   including rapid pause/resume with no intervening rendered frame.
   Confirm the camera indicator stops on pause/exit.
6. Observe several minutes with good light and then poorer light. Record
   tracking gaps, delays and undercounts. If a tracking-loss update replaces
   a pending count update, completed repetitions must remain visible within
   that running trial. A count computed on the camera worker must not
   vanish merely because its pose overlay became stale.
7. Run in airplane mode after installation. Confirm counting works
   locally and budgets/awards/history do not change. Close/reopen and
   restart the process: this temporary trial starts at zero. Camera-to-award
   challenges remain T11 work.

Record APK version, device/OS, lens, orientation, visible side, lighting,
manual full-cycle count versus displayed count, false positives, missed
movements, tracking-loss/reappearance results, lifecycle reset behavior
and reproducible failures. All results are **pending**, not recorded passes.
