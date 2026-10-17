# U2 — Understandable and recoverable workout design

Date: 2026-10-17. Status: implementation proposal. Covers **UX01, UX02, UX04, UX05, UX12, F01**.

Depends on [U1](2026-10-17-ux-foundation-design.md) presentation contracts and the [package contract](../../UX_REVIEW_IMPLEMENTATION_2026-10-17.md). Goal: prepare, position, exercise, and resolve the result without ambiguous rewards or navigation traps. Exercise stays optional; recognition thresholds and reward policy are preserved.

## Existing integration points

Keep `ChallengeFlow` as the main-thread owner and `ChallengeSession` as the only consumer of rewarded detector progress. Keep CameraX/MediaPipe in `CameraPoseSession`; extract its visual preview and callbacks from the instruction-heavy `CameraScreen`. Main navigation and `ChallengeActivity` both render the same flow. Changes to camera ownership must preserve disposal, callback invalidation, and rear/front mirroring. Preparation/practice never creates a persisted challenge.

## W1 — Explicit states and camera coach

Add `WorkoutStage { PREPARE, POSITION, PRACTICE, EXERCISE, SAVING, RESULT, RECOVERY }`. These are presentation stages, separate from existing `ChallengePhase`. State transitions:

| From | User/system event | To / effect |
| --- | --- | --- |
| Prepare | Permission connected, Continue | Position; no challenge identity |
| Position | Optional practice | Practice; isolated counter, no activity/award writes |
| Position/Practice | Review terms | Prepare with current offer; no automatic start |
| Prepare after positioning | Explicit Start, current terms accepted | Engine start; new identity; Exercise |
| Exercise | Detector reaches required count | Saving; stop camera progress delivery |
| Exercise | End, pause, disposal, camera failure, day change | Recovery; invalidate eligibility synchronously |
| Saving | Committed result / unresolved interruption | Result / Recovery |
| Recovery | Verified not saved, Start again | Prepare; refresh offer; require explicit Start |
| Result/Recovery | Done | Caller closes route/activity |

Prepare shows exercise choice, mode, terms, camera choice, privacy details, and positioning guidance. Position shows responsive preview, framing hints, one tracking state, camera choice, Retry, Back, and optional practice. For squats/jacks/knee raises use existing front-facing guidance; push-ups retain side-view guidance and alternative landmark support. Do not claim correct form or medical suitability.

Define pure `TrackingReadiness.update(timestampMs: Long, nowMs: Long, tracking: Boolean): Boolean` and `reset()`. Ready requires 1,000 ms of continuous valid tracking samples with each adjacent gap at most 250 ms. A sample must be nonnegative, no later than nowMs, and at most 250 ms old; invalid, stale, repeated/out-of-order, camera/exercise change, or a larger gap resets readiness. Readiness is coaching, not proof that future reps will be detected. Start remains possible after a clear positioning warning; a user must not be permanently blocked by an unvalidated readiness heuristic. Practice is optional and ends at three detected reps or Done, with a visible **Practice — no time earned or activity saved** label. It uses a new counter instance on entry and cannot pass progress into `ChallengeSession`.

Starting Exercise always creates a fresh camera/counting run. Reset both counter and readiness; the positioning/practice count is discarded. Keep camera choice fixed during Exercise; changing it requires ending and returning to Prepare. Keep-screen-awake applies only while the exercise stage is active and the lifecycle is resumed; clear it on all other stages, pause, and disposal. This does not change background cancellation policy.

## W2 — Recovery and persisted-result truth

Use `InterruptionReason { USER_ENDED, BACKGROUNDED, CAMERA, PERMISSION, DAY_CHANGED, REPLACED, PROCESS_RESTART }`. Say why the run stopped and that unfinished progress is not saved. Do not use discarded reps to award time or restore a detector count.

Add `BudgetEngine.savedCompletion(id: String): CompletionResult?`: one read transaction, returning an existing `Awarded(award)`, `ExerciseOnlyCompleted` for a completed exercise-only challenge, or null. This method never creates activity, changes counters, or grants an award. Award identities survive history deletion; recovering a result must not recreate deleted activity. Saved exercise-only status can be shown without resurrecting deleted activity details.

In `ChallengeFlow`, track the started ID separately from the detector session; save only that ID through `rememberSaveable` for restoration. Do not save pose/progress. On interruption, synchronously invalidate current generation and completion eligibility, stop audio/camera delivery, then wait for any in-flight completion job to settle. Cancel the still-active challenge and query `savedCompletion`. Recheck generation/identity before publishing. Startup repository recovery already cancels unfinished challenges; still look up completed results by restored ID.

Recovery states are `CHECKING`, `SAVED`, `NOT_SAVED`, and `READ_FAILED`. While Checking, disable restart. Saved routes to Result with the committed amount. Not saved offers **Start again**, **Choose another exercise**, and **Done**. Read failed says **Could not confirm whether this result saved**, with **Check again** and Done; do not offer restart until the ambiguity is resolved. Done is always reachable, even while checking. Restart clears the old session, result, tracking, count, and busy state only after verification; refresh target availability/current offer and show current terms before a second Start. Every actual start uses a new UUID. Late callbacks from the previous ID/generation must be ignored.

## W3 — Honest reward disclosure and distinct exercise-only language

Extend `RewardOffer` with `usableBeforeMs: Long` and `usableAfterMs: Long` (zero for exercise-only). Compute both in the same engine transaction as the offer. `usableBeforeMs = budget.usableMs`; `usableAfterMs = min(saturating(baseRemaining + earnedRemaining + rewardMs), combinedRemainingMs)`. Incremental usable time is `usableAfterMs - usableBeforeMs`, never negative. Keep award clipping to the daily reward cap, not to shared remaining time. Existing equality-based start validation must include the new fields; any changed quote requires redisplay and another explicit Start.

For earning modes, Prepare and the blocker disclose **Credit awarded on completion**, **Usable after completion**, **Shared daily time remaining**, and **Daily reward cap remaining**. Explain **Earned time expires at local midnight** and, if the increment is zero, **This credit adds no usable time now because your wallet already covers the shared remainder**. With wallet 0, reward 120,000 ms and shared remainder 30,000 ms, show credit 2 min and usable after 30 sec. With wallet 40,000/shared 30,000, show usable before/after 30 sec and zero increment. Never promise more access by raising the shared limit. Successful completion uses `Award.amountMs`, then refreshes current usability separately; a stale projection is not a committed result.

Exercise-only Prepare says **Complete [count] [exercise]. Save this activity.** Start label is **Start exercise**. No zero-reward, cap, or wallet metrics appear in this mode or its catalog cards. Earning label is **Earn time** with explanatory text nearby. Keep fuller descriptions outside cramped button labels.

## W4 — Exercise viewport and accessible transitions

During Exercise render one large count with progress-range semantics, a flexible preview filling remaining height, one essential tracking message, a full-row mute switch, and persistent **End**. Put no instructional scroll above End. At 320 dp available width and 200% font, reduce/collapse preview height before allowing controls to be pushed offscreen. Provide an on-demand instructions sheet from Prepare/Position and a concise help action during Exercise; opening a system/background surface still interrupts.

Target, exercise, and camera choices use `selectableGroup` and radio rows. Audio uses a full-row toggle with a single semantics node. Count exposes `ProgressBarRangeInfo(current, 0f..required.toFloat())` plus localized state description. Do not mark every rep/frame as a live-region announcement. Tracking loss/recovery uses deduplicated polite announcements after a 1,000 ms stable status; errors and completion announce once immediately. With TalkBack touch exploration enabled, suppress the app's rep/position/completion voice and tones while preserving the saved speech preference, visible text, and mute control. TalkBack owns these announcements; without TalkBack, retain existing offline speech/tone fallback. Test switching accessibility service state mid-run; stop any ongoing app utterance/tone when TalkBack takes over.

## W5 — Direct recovery and empty-state actions

Missing targets: **Choose apps** and **Exercise without rewards**. Award-cap/shared-ceiling refusal: explain reason, offer exercise-only with the selected exercise preserved, and Done. Unavailable target: Choose apps; never silently substitute another app. Camera permission denied: Retry permission or app settings before Start. Camera/model failure: recovery then Retry/position again; no direct restart of the rewarded counter. Launching a rewarded app after success can fail; keep the saved result visible, show Retry opening app and Done. Both activities support the same actions; the blocker activity may open the main setup route explicitly.

## Acceptance and evidence

- One count, visible feedback and End without instruction scrolling; keyboard does not obscure recovery actions.
- Practice cannot insert a challenge/activity/award or trigger completion audio. Three practice reps followed by Start begins at zero with a new counter.
- Pause/end/rotation/new intent/midnight invalidate unfinished runs; restart requires fresh terms and a fresh ID. A commit racing with interruption is shown once; storage uncertainty blocks another attempt until checked.
- The 120,000/30,000 example and zero incremental-use case are disclosed before effort and in the blocker; reward policy tests continue to pass.
- Exercise-only requires camera access only and produces no earned-time claims.
- TalkBack identifies choices, count, End, loss and completion without overlapping app speech. Muted users retain visible state.

Automate pure state, transaction, race, and semantic tests. Phone acceptance must cover all four exercises, both cameras/entry points, mute/TalkBack, physical framing, backgrounding, force-stop, rotation, midnight, and save failure. Do not infer recognition quality from readiness tests.
