# Android Exercise Gate — implementation brief for Codex

You are implementing a working native Android application, not merely proposing an architecture. The owner wants to build it entirely with Codex and will install builds, grant permissions, and report results, but does not want to write code. Ask the owner questions in Persian when needed. This brief is in English to keep implementation requirements precise.

## Goal and scope

Build a personal-use, sideloaded Android app that limits daily foreground use of selected apps and awards additional access time after camera-verified exercise. Example: Instagram has a 30-minute daily allowance; after exhaustion, completing 2 push-ups awards 2 additional minutes for Instagram.

Version 1 MUST support four exercises: push-ups, squats, jumping jacks, and alternating standing knee raises. Count complete repetitions; professional form assessment, injury prevention assessment, identity verification, and robust anti-cheat are out of scope. Do not silently reduce the implementation to one exercise.

No accounts, backend, subscriptions, cloud inference, or remote video upload. Process camera frames on-device; do not record or persist video or images. Internet is unnecessary for normal operation after installation and model provisioning.

## Work autonomously with reviewable results

First inspect the existing repository and its instructions. Preserve unrelated changes. If starting fresh, initialize a small maintainable project. Verify current official Android and pose-detection documentation and compatible stable dependencies before committing to APIs; do not invent versions or permissions.

Ask only questions that materially block implementation. Use the defaults below for routine choices and record them in README. Ask the owner's phone model and Android version early if not known, but continue independent work. Communicate in Persian; keep code identifiers in English. Implement, build, and test rather than stopping after a plan. Report unavailable SDKs, network access, hardware tests, and unresolved limitations honestly. Never claim physical-device verification from emulator or unit tests alone.

## Suggested stack and boundaries

Use Kotlin, Jetpack Compose, CameraX, Room for durable state, and DataStore for preferences. Choose one maintained on-device pose detector, such as MediaPipe Pose Landmarker or ML Kit Pose Detection, after evaluating compatibility, model packaging, and offline operation. Do not call an LLM API to judge exercise frames.

Separate pose estimation, exercise counting, usage accounting, blocking, and UI behind testable interfaces. Use a small understandable structure; avoid unnecessary frameworks and backend services. Keep all four exercise detectors independently replaceable.

## User flow

1. Onboarding explains the app's purpose, local camera processing, required special access, and limitations. The user explicitly enables monitoring.
2. Show launchable apps and allow multi-selection. Exclude this app, system settings, launcher, permission controllers, phone/emergency functions, and other critical system surfaces. Query only what is needed; do not assume unrestricted installed-app visibility.
3. Configure a daily allowance per selected app, default 30 minutes. Show today's consumed time, base allowance remaining, earned time remaining, and monitoring health.
4. Once allowance is exhausted, attempts to use the selected app show a blocking surface with the target app, exercise choices, and available rewards. Returning to the target without credit re-blocks it.
5. The user explicitly opens the exercise screen and grants camera permission if required. Show live preview, positioning guidance, selected exercise, repetition progress, and cancel control.
6. On successful challenge completion, persist the award once, show awarded minutes, and offer return to the target app. Cancelling an incomplete challenge awards nothing.
7. When credit is consumed, block again. Switching to an unrestricted app must remain possible.

## Time accounting and reward rules

### Supported earning modes and global limits

- Support both just-in-time earning after a target app's allowance runs out and advance earning from the dashboard at any time. Before an advance challenge, choose its target app; credit is not transferable. Both paths use the same detectors, ledger, award caps, and expiry rules.
- Offer an optional zero-base-allowance mode for starting the day with no free time and earning access through exercise. Allow a base allowance of zero; do not enable this mode by default.
- Add an enabled, editable combined daily usage ceiling across all currently selected target apps. Proposed initial default: 60 minutes; explain and allow editing during onboarding. This is a product default, not a user-confirmed exact amount. It includes both base and earned time consumed. Exercise credit cannot bypass this ceiling. Count each foreground interval once in both its app budget and the combined usage total; changing apps cannot reset the total. Retain today's accounted usage if an app is removed/re-added, and do not reset counters on settings changes.
- Effective usable time for an app is the minimum of its remaining base-plus-earned budget and the remaining combined ceiling. Show which limit caused blocking. At the combined ceiling, disable earning further usable credit for today, but keep exercise-only mode available. Do not delete existing unused credit merely because the combined ceiling is reached; it still expires at midnight.
- Offer configurable short usage sessions, default 5 minutes, for selected apps, including when using banked credit. At a session boundary show Continue or Finish. Continue requires an explicit tap, uses only existing available budget, and does not require another exercise. If no budget remains, show the appropriate budget blocker instead. Finish returns to a safe screen and does not spend unused credit. Session pauses cannot consume usage time. Brief app switching must not erase accumulated foreground session time; reset on explicit Finish or the daily boundary. Document and persist session state so process restart does not silently bypass the prompt.
- Include exercise-only mode with no target app and no time reward. It remains available after reward or combined usage caps are reached. Save completed repetitions and active exercise duration, separately from earned-credit challenges. Cancellation may retain completed activity for history but never grants partial credit. Never convert previously recorded exercise-only activity into an award retroactively.
- Let the owner edit repetition targets and reward durations with positive validated values. Do not automatically increase exercise difficulty or present these values as a personalized training prescription.


- Allowances and rewards belong to each target app independently; no shared wallet in v1.
- Count foreground interactive use only. Exclude screen-off time, the blocker, exercise screens, and time in other apps. Explicitly document v1 handling of split-screen and picture-in-picture; never charge the same elapsed interval twice.
- Consume the daily base allowance first, then earned credit. Default rewards: 2 push-ups = 2 minutes; 10 squats = 2 minutes; 20 jumping jacks = 2 minutes; 20 alternating knee raises = 2 minutes. These are configurable product defaults, not claims of exercise equivalence or fitness advice.
- One knee raise, followed by return to the starting posture, counts as one repetition. A jumping jack is closed → open → closed. Other exercises require a full down-and-up cycle.
- The selected reward rule is captured at challenge start. Changes to settings cannot change an in-progress award.
- Allow repeated challenges within a configurable daily earned-time cap per target app, default 20 minutes. The cap limits total time awarded today, including consumed credit, rather than just the remaining balance. Display the reward and remaining award capacity before each challenge. Only completed challenges award credit. If less than the configured reward remains under the cap, display the reduced reward before starting. At zero capacity, disallow further reward challenges. Enforce the cap transactionally with duplicate-award prevention and reset capacity at local midnight. Lowering the cap does not remove existing credit; it stops further awards if today's awarded total has reached the new cap.
- Use persisted unique challenge IDs and a transactional award operation so duplicate callbacks, navigation, and process restart cannot award twice.
- Reset the base allowance at local calendar midnight and expire unused earned credit. Keep daily history. Handle date changes while the app is running and after restart; document that changing the device clock is not hardened against deliberate abuse in v1.
- Use monotonic time for elapsed durations and wall-clock dates for daily boundaries. Persist usage periodically and at transitions; do not grant a fresh allowance on process death or reboot. Document the bounded possible accounting loss after abrupt termination.
- Refresh/reconcile usage on resume. Do not award time based solely on an animation, timer, or button press.

## Android monitoring and blocking

Investigate and implement an appropriate consumer-device approach using Usage Access for usage information and, where necessary, a narrowly scoped AccessibilityService for foreground transitions and blocking. Usage Access alone is not an app-suspension mechanism. An ordinary sideloaded app cannot guarantee an unbreakable lock.

Verify current restrictions on overlays, background activity launches, foreground services, boot behavior, and accessibility windows. Choose a working blocking surface rather than assuming arbitrary background activity launches are allowed. Provide an explicit feasibility spike early: select one app, set a short limit, and verify the monitoring/blocking path on the owner's phone.

Do not read messages, inspect text in other apps, perform unrelated gestures, collect accessibility content, or prevent uninstall or revocation of permissions. Request only necessary capabilities. Avoid continuous high-frequency polling where events/reconciliation suffice. Use a foreground service only if needed and permitted, with an honest visible notification and appropriate declared type.

Show separate states for monitoring enabled, permission missing, service stopped, and healthy. If required access is revoked, report that limits cannot be enforced; do not show a false blocked state. Provide recovery guidance for manufacturer battery restrictions when observed. After reboot, restore configuration and resume monitoring only through Android-supported mechanisms; if user action is required, explain it.

This is a self-control aid. Force stop, permission removal, uninstall, and some manufacturer behavior can bypass it. Do not use device-owner enrollment, root, or hidden APIs for v1. Play Store publishing is out of scope; do not claim future policy compliance without a separate review.

## Camera exercise detection

- Camera runs only while the user-visible exercise screen is active. Release it when the screen closes or backgrounds. No always-on camera.
- Support available front/rear cameras and account for preview mirroring and rotation. Default to rear camera where convenient; let the user switch.
- Show exercise-specific placement instructions: side view with shoulder/elbow/hip visible for push-ups; full body from front or slight angle for squats; full body from front for jumping jacks and knee raises.
- Use pose landmark confidence checks, normalized geometry or joint angles, temporal smoothing, hysteresis, and per-exercise state machines. Tune thresholds with real-device feedback rather than claiming universal accuracy.
- Count a full movement cycle once. Reject tiny jitter and implausibly fast transitions. Do not require professionally correct posture or medically judge form.
- When required landmarks are missing or confidence is low, pause counting and show a useful message. Reset partial movement state after sustained tracking loss so reappearance cannot create phantom repetitions. Retain completed repetitions during the active challenge.
- Drop stale frames and prevent concurrent detector invocations; avoid queue growth and UI freezes. Render results on the correct thread.
- Persist only challenge metadata, repetitions, and award records. A debug landmark overlay may be offered without saving images.
- A cancelled or interrupted challenge cannot accidentally earn a reward. For v1, process death cancels an incomplete challenge; a completed persisted award survives restart.

## Screens and local data

### Required v1 convenience features

- Offer all four exercises directly from the blocker, showing repetitions and reward. Persist and preselect the last chosen exercise and camera, while allowing an immediate change without visiting settings. Keep navigation to challenge start minimal.
- Provide optional on-device spoken repetition counting, enabled by default, and a completion announcement stating the actual persisted reward. Announce sustained tracking loss with a short positioning prompt; rate-limit warnings and avoid overlapping or stale speech. Include a mute toggle and visible equivalents. Stop speech on cancellation/backgrounding. Check for an offline Persian TTS voice; when unavailable, keep visual counting functional and offer short sound cues without requiring network access. Document this fallback.
- Persist the last exercise/camera, speech preference, and daily award cap. Show today's awarded total and remaining award capacity alongside remaining usable credit. At the cap, explain that no more time can be earned today and keep navigation to unrestricted apps available.
- Extend daily history and a weekly view with combined/per-app usage duration, completed repetitions by exercise, active exercise duration, and awarded minutes. Show usage and activity as separate outcomes; low usage can be a success even on a day without exercise. Compare only recorded periods with adequate coverage and label missing monitoring data; never treat missing data as zero use or claim reduced usage without a valid baseline. Use supportive neutral messages, without guilt, punishment, or loss of historical progress for missed days. Do not estimate calories or claim measured fitness improvements from repetition counts.

Acceptance checks must also cover remembered exercise/camera selection, muted and unavailable TTS, absence of stale speech, and the daily award cap across consumption, duplicate callbacks, restart, and midnight.

Deliver onboarding/permissions, dashboard, app selection and limits, blocker, exercise challenge, reward success, simple daily history, and settings. Use Persian UI with correct RTL layout, readable mixed numbers/text, accessible contrast, and clear status messages. Keep emergency/system navigation available.

Persist target apps, daily limits, reward rules, daily usage, challenge IDs/statuses, and an award ledger. Store time as integer seconds or milliseconds, with explicit units. Derive remaining time from consistent durable records rather than disconnected UI counters. Provide disable monitoring and delete local history controls.

## Definition of done

Build a debug APK and provide its actual path and installation steps. Provide README with setup/build instructions, permissions, selected dependencies, implemented behavior, known limitations, defaults, and a physical-device test checklist.

Automated checks should cover time-budget consumption, daily rollover, duplicate award prevention, interrupted challenges, and all four counting state machines using synthetic pose landmark sequences. These checks validate logic, not camera accuracy. Run the available build, lint, and relevant tests; report exact outcomes and blockers.

Physical-device acceptance checklist:
1. Two selected apps maintain independent limits and rewards.
2. A short allowance is consumed only during actual target foreground use; screen-off and exercise time are excluded.
3. Exhaustion blocks access; an incomplete/cancelled challenge never grants credit.
4. Each of the four exercises counts complete repetitions in its instructed camera position and awards credit exactly once.
5. Granted credit allows target use and re-blocks after exhaustion.
6. Navigation, process restart, reboot, and midnight do not duplicate awards or reset the day's budget incorrectly.
7. Missing pose landmarks pause counting; permission denial/revocation gives actionable status; camera stops on backgrounding.
8. The app runs exercises without a network connection and stores no camera recordings.
9. Advance earning and just-in-time earning use identical rules; zero-base mode starts with no free allowance.
10. App switching cannot bypass the combined ceiling; neither existing nor newly earned credit overrides it.
11. Short-session Continue consumes existing budget without requiring exercise; Finish preserves unused credit. Prompts and exercise screens consume no app time.
12. Exercise-only activity works at either cap and grants no credit. Reward settings remain user-controlled and history separates usage from activity.

## Implementation order

Start with a buildable skeleton and the monitoring/blocking feasibility spike. Next implement durable budgets and idempotent awards, then the camera pipeline and all four detectors, then connect the end-to-end flow and finish the UI. Validate incrementally and give the owner installable builds when hardware feedback is needed. A milestone build may contain partial functionality if clearly labeled, but the requested v1 includes all four exercises.

Begin now. Ask about phone model/Android version if needed, state any genuinely blocking questions together, and continue all work that does not depend on their answers.
