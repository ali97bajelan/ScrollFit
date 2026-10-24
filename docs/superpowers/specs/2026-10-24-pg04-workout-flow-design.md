# PG04 — Safe guide-to-workout journey

Status: proposed / not implemented. Depends on PG02–PG03. Shared requirements: [task register](../../EXERCISE_POSITION_GUIDE_TASKS.md).

## Outcome

Learn and positioning never create activity or credit. Users explicitly start a fresh attempt after understanding the movement and finding the camera range. Preparation, positioning, countdown and active exercise cannot share counter state.

## States and transitions

Create UI-only `GuideStage { PREPARE, LEARN, POSITION, STARTING, COUNTDOWN, EXERCISE, RESULT, INTERRUPTED }` in `app/.../challenge/GuideJourney.kt`. Keep `ChallengePhase` as the domain transaction state; do not add tutorial phases to the accounting model.

| From / event | Result / required side effects |
|---|---|
| Prepare → guide | Learn for selected exercise; no camera or challenge write |
| Learn → Continue | Position; ask camera permission in context; create a position-only run key |
| Position → camera/exercise change | New position run; reset readiness and stale geometry; no challenge |
| Position → Start | Require current ready=true, available immutable offer, permission and no pending work; lock choices; enter Starting; call existing engine start once with fresh UUID |
| Starting → OfferChanged/refused | No countdown; show new terms/refusal; require an explicit review and another Start after positioning |
| Starting → Started | Own the returned challenge and enter Countdown 3–2–1 for 3,000 ms using monotonic time; position run may continue for framing only; accept no challenge progress |
| Countdown → timer ends | Recheck current framing/permission/day; if valid, dispose position session, bind fresh active counter under challenge ID, enter Exercise and begin normal rep audio |
| Countdown → framing invalid, permission loss or End | Cancel the started row; stop countdown/audio; Position after confirmed cancellation, or Interrupted with Retry if cancellation fails; next Start uses a new ID |
| Exercise → count target | Existing completion/save flow; result only after durable commit |
| Exercise → tracking loss | Keep completed reps; show recovery cue and outline; existing raw counters clear incomplete cycles; no count fabricated by guide |
| Any unfinished stage → background/rotation/navigation/fatal camera error | Invalidate generation first, stop camera/audio, cancel any active challenge; no automatic resume of reps/countdown |

No automatic start when readiness turns green. Countdown can finish only if framing is still fresh; a brief invalid sample cancels rather than silently extending a started attempt. Explain this as “Position changed. Get into view and start again.” Do not restore an unfinished challenge from saveable state.

## Wiring and boundaries

- `GuideJourney` owns stage, guide generation and readiness. Proposed API: `showGuide(exercise: Exercise)`, `continueToPosition()`, `observePosition(runKey: String, state: CameraPoseState, nowMs: Long)`, `requestStart()`, `tick(nowMs: Long)`, `interrupt()`; UI reads `stage`, `positionRunKey`, `ready`, `countdownRemaining` (nullable Int). Inject engine-start/cancel delegates and monotonic clock for app unit tests; keep raw counters in the camera worker.
- Refactor `ChallengeFlow.start()` so successful engine creation does not immediately begin rep acceptance/audio. Add `activateStartedChallenge(id: String)` for the countdown-to-exercise handoff. `cameraStateChanged` requires both current challenge ID and Exercise stage.
- Position uses `position:<generation>`; active uses challenge UUID; help in Exercise retains that UUID. The adapter invalidates old callback tokens before disposing/rebinding. Never subtract trial counts from a cumulative counter; use a fresh counter.
- Track PG02 `framingResetSerial` per position run. A changed serial resets readiness in Position, including when the latest visible issue is FRAMED. Capture the serial on explicit Start; any subsequent change during Starting invalidates the start generation (cancel a late Started row), and any change during Countdown cancels that attempt. Check the captured serial again before activation. This preserves brief raw losses through UI coalescing; status-message debounce cannot suppress it.
- Integrate both main `ChallengeScreen` routes and `ChallengeActivity` blocker entry, and reuse content in standalone `CameraScreen` trials. Trial countdown creates no challenge; earned and exercise-only modes create their normal challenge only on explicit Start. Trial labels remain clear and no activity is persisted.
- Reuse current cancellation/commit behavior. A commit that wins an interruption race remains saved; guide navigation cannot undo or duplicate it. Uncertain cancellation/save state blocks another Start until resolved. Guide-stage state never authorizes an award.

## Acceptance / exclusions

Three practice reps followed by Start/Countdown produce zero challenge reps until the fresh active counter completes a full cycle. Repeated Start taps, slow storage and old callbacks cannot create multiple active attempts or rewards. Tests cover day rollover while Starting/Countdown, offer changes and cancellation failure. No budget calculation, reward rule or special-access permission requirement changes; exercise-only still requires camera access only.
