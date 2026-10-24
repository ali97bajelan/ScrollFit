# PG05 Accessible Guide Feedback Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task; use superpowers:subagent-driven-development only if explicitly selected. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Deliver clear localized positioning prompts with one accessible speech/status owner.

**Architecture:** A pure timing policy chooses semantic guide prompts; the existing app audio output localizes and plays them. A shared UI resolver provides the visible reason, Start prerequisite and single status semantics node.

**Tech Stack:** Existing Kotlin/JUnit 4, Compose semantics, offline TextToSpeech/ToneGenerator and Android resources.

**Spec:** [PG05](../specs/2026-10-24-pg05-accessible-feedback-design.md); [shared requirements](../../EXERCISE_POSITION_GUIDE_TASKS.md).

## Global Constraints

- Keep pinned dependencies, minSdk 28, target/compileSdk 37 and JVM target 17.
- Persian RTL/English/French, mute, TalkBack and static/reduced-motion content remain complete.
- Non-error reason display settles for 500 ms; readiness uses 1,000 ms; audio issue dwell 2,000 ms and actual-playback cooldown 10,000 ms.
- No remote voices/assets; no claims of correct form, measured lighting or distance.

## Review Focus

- TalkBack starts while app prompt/countdown sound is playing (steps 1, 3–4).
- A queued issue resolves before playback/cooldown and must never speak (step 1).
- Valid framing with false counter tracking must not say ready (step 4).
- Translations/RTL can invert left/right or change the rep unit (step 4).
- Completion/reps/position/countdown must not compete across two audio engines (steps 1, 3).

## Task: Prompt policy, resources and accessibility

**Files:**

- Create `core/src/main/kotlin/com/example/scrollfit/core/feedback/GuidePromptPolicy.kt` and `core/src/test/kotlin/com/example/scrollfit/core/feedback/GuidePromptPolicyTest.kt`.
- Modify core `feedback/ChallengeFeedback.kt` and corresponding `ChallengeFeedbackTest.kt`, `FeedbackAccessibilityTest.kt`.
- Create `app/src/main/java/com/example/scrollfit/guide/GuideStatus.kt`.
- Modify `app/src/main/java/com/example/scrollfit/feedback/ChallengeAudio.kt`, `camera/WorkoutTrackingStatus.kt`, `challenge/WorkoutContent.kt` and PG03 guide views.
- Update three `position_guide_strings.xml` bundles plus affected existing `challenge_strings.xml` and exercise guidance resources in all locales.
- Create `app/src/androidTest/java/com/example/scrollfit/guide/GuideAccessibilityTest.kt`.

**Interfaces:** Consumes PG02 `FramingIssue`, PG04 stage/run ID and existing `FeedbackAccessibility`. Produces PG05 `GuidePromptPolicy`/`GuidePrompt`; extend `FeedbackMessage` with `Guide(prompt: GuidePrompt)` and `Countdown(remaining: Int)`. Add `ChallengeFeedback.beginGuidance()`, `guide(prompt: GuidePrompt)`, `countdown(remaining: Int)`, `playbackStarted(token: Long)` as specified in PG05. Validate current generation/stage/issue again when output drains. Keep one `FeedbackOutput`, respecting existing count coalescing and committed completion messages.

- [ ] **1. Add policy and ownership tests.** Unresolved issue at 1,999 ms → no prompt, 2,000 → eligible. Actual playback at 2,500 → next positioning playback no earlier than 12,500, even after reset/lens/run change in the same audio owner. Resolved/replaced queued issue, stopped run or stale generation → no playback. Guidance mode permits guide/countdown audio but rejects progress/completion; workout begin clears guidance and permits normal counts. Saved completion in workout mode preempts queued output, countdown preempts position, and count coalescing still works. Turning TalkBack on immediately stops output without changing saved speechEnabled; turning mute on also clears pending output.
- [ ] **2. Run red.** `./gradlew -p verification/core :core:test --tests '*GuidePromptPolicyTest' --tests '*ChallengeFeedbackTest' --tests '*FeedbackAccessibilityTest' --console=plain`. Expect missing prompt APIs or targeted assertion failures.
- [ ] **3. Implement policy/output arbitration.** Use monotonic timing; keep one GuidePromptPolicy per screen/audio owner. Reset dwell/queue on run changes while retaining its actual-playback cooldown timestamp. Call `played` on actual playback start via the token-checked `playbackStarted` callback (TTS `onStart`; immediate callback after successful tone start). Wire guidance mode on Position/Countdown, workout mode at PG04 activation and stop on interruption. Add new localized message mappings and fallback tones in existing audio output; review all sealed-message exhaustive branches. Suppress app sound under TalkBack. Share one visual reason resolver between status and disabled Start. Hardware errors render immediately; immediate readiness invalidation is independent of 500 ms reason settling.
- [ ] **4. Add semantic/localization fixtures.** Assert one polite status node, no rep live region, descriptive disabled Start, full-row camera radio semantics, Help focus restoration, no duplicate art narration, and static demo controls. Assert FRAMED + tracking=false shows a starting-position cue, not “Start when ready.” Parse locale XML and compare keys/format placeholders; manually review Persian/French meaning for each exercise and rep unit. Re-run existing workout layout/accessibility tests.
- [ ] **5. Verify.** Full core suite, `python3 verification/check_source.py`, `./gradlew :app:assembleDebug :app:lintDebug :app:connectedDebugAndroidTest --console=plain`. Execute [PG05 QA](../../qa/position-guide/PG05.md) on actual TalkBack and airplane-mode audio; semantics tests alone do not prove spoken behavior or contrast.
- [ ] **6. Review/checkpoint.** Record locale/accessibility/audio evidence. Suggested commit `feat: add accessible offline exercise positioning prompts` under session authorization.

**Handoff:** PG06 records actual comprehension, audio/visual consistency and full device qualification.
