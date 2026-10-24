# PG05 — Accessible, localized positioning feedback

Status: implemented; QA not run. Depends on PG02–PG04. Shared requirements: [task register](../../EXERCISE_POSITION_GUIDE_TASKS.md).

## Outcome / copy contract

Show one useful next step without noisy prompts or unsupported diagnoses. Use icons and words with color, never color alone. Hardware/permission/model errors replace guidance immediately and offer the existing recovery actions.

| Evidence | Proposed English copy | Rule |
|---|---|---|
| Initializing | Getting the camera ready… | Never show ready during loading |
| NO_RELIABLE_POSE | Cannot see you clearly. Try adjusting the lighting or phone position. | Does not assert actual light level or person count |
| SHOW_REQUIRED_PARTS | Keep [exercise-specific body parts] visible. | Push-up: a complete arm and hip on one side |
| MOVE_BACK | Move farther from the camera to keep the movement in view. | Confident edge/overflow evidence only |
| MOVE_CLOSER | Move a little closer to the camera. | Confident small body bounds only |
| CENTER_BODY | Move toward the center of the preview. | Never confusing mirrored left/right commands |
| Stable framing | In view. Start when you’re ready. | Position only; no form approval |
| Active tracking recovered | Tracking restored. Continue from [exercise start position]. | Do not announce Start during an active workout |
| Countdown canceled | Position changed. Get into view and start again. | No reward or success message |

Unknown active tracking loss uses neutral “Tracking paused. Keep the required body parts visible.” Replace the existing unconditional out-of-view claim in `workout_tracking_lost`. If framing is FRAMED but counter tracking is false, show the exercise starting-position recovery cue and keep readiness false; do not show the stable-framing message.

## Timing and speech ownership

Create `GuidePromptPolicy` in core. Non-error visible reason changes require 500 ms of stable evidence; error display and readiness invalidation are immediate. Stable ready display follows PG02's 1,000 ms gate. TalkBack announces each changed, settled message once via one polite status node; count updates do not become live regions.

Optional app positioning speech requires 2,000 ms of the same unresolved issue and a global minimum of 10,000 ms between actual positioning-prompt playbacks, reusing the current feedback timing intent. At playback, revalidate generation, stage and issue. Recovery/End/background clears queued prompts. No repetition of a resolved instruction. Countdown cues preempt positioning; saved completion preempts all; rep counts coalesce as today. There is one audio owner, never simultaneous independent guide/workout TTS engines.

`GuidePromptPolicy.reset()` clears issue dwell, recovery state and queued eligibility, but retains the last actual positioning-playback timestamp for that screen/audio owner's lifetime. Lens, run and stage changes must not bypass the 10,000 ms cooldown. Reuse one policy per audio owner; do not recreate it per run key. A newly created screen/audio owner starts a new cooldown scope.

With TalkBack touch exploration enabled, suppress app guide/rep/countdown speech and tones without changing the saved mute preference. Stop any playing app sound immediately when TalkBack takes ownership. Visual countdown/status remain; TalkBack announces countdown start and exercise start once rather than every digit. Mute disables all optional guide/countdown sounds; visuals and controls remain complete. Offline voice unavailable: use existing local tone/visual fallback; do not download voices or block Start.

## Files / interfaces

- `core/.../feedback/GuidePromptPolicy.kt`: `GuidePromptPolicy(clock: MonotonicClock)`; `update(issue: FramingIssue?, ready: Boolean, activeWorkout: Boolean): GuidePrompt?`, `played(prompt: GuidePrompt)`, `reset()`; `GuidePrompt(issue: FramingIssue?, recovered: Boolean)` is a semantic message, not localized text. The output owner validates run/stage before play and calls `played` only on actual output start.
- Extend `FeedbackMessage` in `ChallengeFeedback.kt` with `Guide(prompt: GuidePrompt)` and `Countdown(remaining: Int)`; preserve existing completion/repetition paths. Add `beginGuidance()` to establish an audio-only positioning/countdown scope, `guide(prompt: GuidePrompt)` and `countdown(remaining: Int)` to submit those messages, and `playbackStarted(token: Long)` for actual-start timing. Existing `begin()` enters workout mode and clears queued guidance; `progress` and `complete` are ignored in guidance mode. `stop()` ends either scope. GuideJourney starts guidance mode on Position and keeps it through Countdown, then calls existing workout begin only on activation. These audio modes never authorize persistence. Extend tests before changing this contract and update all exhaustive `when` statements in `ChallengeAudio.kt`.
- `app/.../guide/GuideStatus.kt` maps current hardware/framing/readiness/stage state to one resource and semantics node. Use the same resolver for the button's prerequisite explanation and status.
- Modify `WorkoutTrackingStatus.kt`, `ChallengeAudio.kt`, `WorkoutContent.kt`; use `FeedbackAccessibility`'s existing saved-preference versus effective-output ownership.
- New position-guide resources in Persian, English, French; update affected current guidance/status strings rather than leaving contradictory copies in trial/catalog/help.

## Acceptance

All messages have locale/placeholder parity and understandable Persian RTL/French expansion. Replay, Pause, step navigation, camera radio group, Help, Start and End have coherent labels/roles/focus. Help dismiss restores focus to Help. Reduced motion retains labeled stills and countdown digits without animation. Actual TalkBack and offline audio checks supplement synthetic policy and semantics tests. No new vibration setting, voice engine or user preference is required.
