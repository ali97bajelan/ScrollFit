# P3 — On-demand intentional break (F05)

Date: 2026-10-17. Status: discovery-gated Stage C proposal. Entry: U3 acceptance and opt-in observation that a non-camera pause solves a real inconvenience. Read the [package contract](../../UX_REVIEW_IMPLEMENTATION_2026-10-17.md).

## Intent and smallest version

Offer a voluntary short pause when movement/camera use is inconvenient. A 30,000 ms suggested pause has **Continue** and **Finish** available immediately. There is no forced wait, breath/health prescription, detected exercise, earned credit, or automatic session reset. Automatic session-boundary prompts retain their existing token-protected behavior.

## I1 — State and entry

Add a Home **Take a break** action and let the blocker use the same private break activity. An optional target package comes only from a visible app/blocker action and is revalidated against selected eligible apps. Without a target, Continue returns to ScrollFit Home. With a target, Continue attempts to open it; failure keeps the break/result screen with Retry and Home. Use a fresh visit identity, monotonic elapsed clock, states `PAUSING`, `READY`, `FINISHED`; the UI says **Continue whenever you choose**. At 30 seconds only the suggested-pause state changes; no navigation, sound, permission or award occurs automatically.

## I2 — Accounting and session boundaries

This flow never calls `BudgetEngine.resolveSession`, creates a challenge/activity/award, or resets `SessionState`. Opening a selected app on Continue is an ordinary launch; existing budgets and pending session prompts can block it again. Explain that Continue does not add time or bypass a daily limit. Automatic prompt Continue/Finish continue using `SessionAccess` equality and monitor queue checks; do not reuse stale prompt tokens for this shortcut.

While ScrollFit's own activity is visible, existing monitoring rules do not count it as selected-app usage. An on-demand pause does not erase already observed usage. Finish takes the person to the launcher and closes the break activity; if launcher resolution fails, offer ScrollFit Home. Keep Stop monitoring accessible through the existing blocker/main flow.

## I3 — Lifecycle, privacy, and success

No camera, microphone, notification, exact alarm, or background timer permission. Pause hides/stops UI updates; resuming the same live process may compute elapsed time from the stored monotonic start. On process recreation, restart the optional suggestion at 30 seconds with **Start a fresh pause**; do not fabricate a completed break. Reboot/clock changes cannot generate a completion or credit. Keep-screen-awake is not needed for this passive suggestion.

Keep visible state, large action targets, and accessible remaining-time text; do not announce every second. Distinguish deliberate Finish from a timer reaching zero, app abandonment, and exercise-only completion. First version records chosen finishes only through opt-in pilot observation/local diagnostics, with no raw-app history event pipeline. Evaluate reported usefulness before wider rollout.

Acceptance: Continue/Finish work at time zero; finish leaves selected app without resetting wallet/session; continue can encounter the original blocker; invalid/removed targets never launch arbitrary packages; no accounting writes under any lifecycle path; muted/TalkBack users have equivalent control.
