# T06 phone checks — all pending

Intended device: physical Android 14 phone. No APK is currently
available because Android compilation is blocked. Run these checks after
the documented build succeeds and record actual outcomes separately from
JVM/source checks. This milestone does not count exercise repetitions or
grant time.

1. Install the built debug APK. Before the **first** camera launch, enable
   airplane mode and turn Wi-Fi off. Open Exercises, choose a card and tap
   **باز کردن دوربین**. Grant camera permission. Confirm preview and visible
   body/tracking feedback work with no model download or network access.
2. Deny permission, then deny permanently. Confirm no camera indicator,
   a usable grant/settings action and successful recovery after enabling
   CAMERA in app settings. Repeat permission revocation and Android's global
   camera privacy toggle while the screen is open.
3. Try rear and front lenses. Confirm the front preview is mirrored and the
   rear preview is not. In a debug build enable the unsaved point overlay.
   Check shoulders, elbows, hips, knees and ankles align with the visible
   body in portrait, landscape and after a 180° rotation that keeps the
   Activity alive. Try split-screen resizing; record any alignment drift.
4. For push-ups, show either side with shoulder/elbow/hip visible. For other
   cards show a full body from the instructed front/slight angle. Hide body,
   leave the frame, occlude relevant joints and reduce light. Confirm useful
   searching/lost feedback and no frozen green tracking overlay. This is
   positioning feedback, not professional form grading.
5. Press Home, lock the screen, navigate Back, and tap Close. Confirm the
   camera privacy indicator stops each time; return and confirm preview
   resumes only on this visible screen. Repeat rapid open/cancel and lens
   switching during initialization. Confirm no old overlay/error returns.
6. Open another camera app or exercise the camera privacy switch. Confirm
   an actionable error and working Retry/lens/Close controls. Reopen several
   times and check no resource-exhaustion error accumulates.
7. Restart the process and reopen the catalog. Confirm remembered camera
   choice, and no preview outside the selected camera screen. Confirm
   budgets/awards/history were not modified by this camera test.
8. Run a five-minute preview and inspect responsiveness, tracking gaps,
   CPU/battery and the privacy indicator after close. Record whether the
   conservative 250 ms stale-result threshold causes frequent tracking loss;
   thresholds need observed device tuning, not a universal accuracy claim.
9. Inspect app storage/permissions and, where tooling is available, network
   traffic. Confirm no saved recordings/images, unexpected frame logging,
   audio request or frame uploads. Asset/native instrumentation must also
   pass separately from this visual inspection.

Record installed APK version, device/OS, camera lens, display orientation,
airplane-mode first-launch result, denial/revocation outcomes, lifecycle
indicator behavior, overlay alignment and failures with reproducible steps.
