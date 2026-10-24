# PG02 — Framing evidence and readiness

Status: proposed / not implemented. Depends on PG01. Shared constraints: [task register](../../EXERCISE_POSITION_GUIDE_TASKS.md).

## Outcome

Positioning reports one evidence-based instruction and enables Start only after stable, fresh framing. It must not mistake a filtered-away sample for proof that a user is standing too far away.

## Data and ownership

Create `core/.../pose/FramingEvaluator.kt` and `PositionReadiness.kt`. `FramingObservation(timestampMs: Long, issue: FramingIssue)` is an immutable summary, not stored. `FramingIssue { NO_RELIABLE_POSE, SHOW_REQUIRED_PARTS, MOVE_BACK, MOVE_CLOSER, CENTER_BODY, FRAMED }`.

`FramingEvaluator.evaluate(exercise: Exercise, sample: PoseSample?, timestampMs: Long): FramingObservation` consumes **raw upright unmirrored** poses on the existing inference worker before `PoseFilter` removes invalid samples. It uses PG01 groups. It never calls a counter or stores landmarks. Add `framing: FramingObservation? = null` to `CameraPoseState`; publish the summary alongside existing progress. `CameraPoseStatus` retains precedence for hardware/permission/model errors; stale delivery clears framing as well as sample.

Because main-thread delivery coalesces, also add `framingResetSerial: Long = 0` to `CameraPoseState`. Create worker-owned `FramingContinuityTracker.observe(observation: FramingObservation?, trackingAvailable: Boolean, nowMs: Long): Long` and `reset()`. Per owned camera session, its serial increases for every non-FRAMED/missing/invalid observation, false tracking, invalid/nonincreasing timestamp, age outside 0–250 ms or sample gap >250 ms. Use the current combined filter/counter tracking result; observe every raw inference, never only published UI frames. Carry the latest serial on every published state. No serial reuse within a session; new sessions have distinct run keys. UI readiness resets whenever the serial changes, even if the latest coalesced issue is FRAMED. Countdown cancels on a changed serial regardless of apparent recovery. Thus an invalid observation followed by valid observations before the UI drains cannot disappear. Raw counters remain independent of this tracker.

When main delivery marks a same-session result stale and clears its framing/sample, retain its `framingResetSerial`; clearing stale geometry must not hide a continuity break. Generation-rejected results remain ignored in full. Tracker updates run after filter/counter availability is known, while raw geometric evaluation still runs before filtering.

Reject samples with mismatched/negative timestamps, wrong landmark count, nonfinite coordinates/aspect or invalid confidence ranges. A required joint is confident when visibility and presence are both >= 0.5. For push-ups choose the complete confident side with the highest minimum confidence (left on ties); never mix sides. Optional low-confidence joints must not disqualify a complete required group. Invalid required joints or no complete confident group produce SHOW_REQUIRED_PARTS; null/malformed pose produces NO_RELIABLE_POSE.

## Provisional deterministic framing rules

For a complete confident required group, calculate its raw normalized x/y bounding box. No aspect correction is needed for fractional image occupancy; pixel projection remains PG03's responsibility. Apply the following priority:

1. Any required joint outside `[0,1]`, or bounding-box edge outside `[0.05,0.95]`: MOVE_BACK. This includes confident joints inferred outside the frame.
2. Push-up: bounding-box width < 0.35; standing exercises: bounding-box height < 0.45: MOVE_CLOSER.
3. Bounding-box center x outside `[0.35,0.65]`: CENTER_BODY.
4. Otherwise FRAMED. Exact boundary values are inclusive; equality at 0.05/0.95, 0.35/0.65 and minimum size passes that condition.

These are framing aids, not a distance or view-angle estimator. A SIDE badge is instructional; do not claim to detect the correct body orientation. A jack's closed start cannot prove overhead reach will fit: Learn and Position explicitly say to leave room for raised hands; no mandatory physical warm-up is introduced. Normal movement during Exercise does not need to match a static outline.

## Stable readiness

`PositionReadiness.update(observation: FramingObservation?, trackingAvailable: Boolean, nowMs: Long): Boolean`; `isReady(nowMs: Long): Boolean`; `reset()`.

Require FRAMED **and** existing counter/filter tracking on unique increasing timestamps spanning 1,000 ms. Age must be 0–250 ms, and consecutive sample gaps <= 250 ms. Invalid, future, duplicate, regressing, missing or non-FRAMED observations reset the window immediately. A 100 ms Position/countdown watchdog calls `isReady(nowMs)` to expire a last sample older than 250 ms and clear the window; it never replays an observation or submits synthetic missing samples between real callbacks. Readiness cannot stay latched when callbacks stop. Reset on camera/exercise/run changes, pause, errors and disposal. UI status smoothing never delays disabling Start.

At 0/250/500/750/1000 ms with current timestamps, readiness becomes true only at 1000. At 251 ms sample age or gap it is false. Readiness only gates start; existing counters continue to own movement validity during exercise.

## Acceptance / limitations

Synthetic edge/time tests are deterministic, including invalid-then-FRAMED updates coalesced before main delivery. Existing raw counter input, serial ordering, one-in-flight inference and generation cancellation remain intact. No confidence result is called “bad lighting”; show “Cannot see you clearly. Try adjusting the lighting or phone position.” for uncertain input. Thresholds may be changed only with updated fixtures and recorded PG06 calibration; synthetic PASS is not physical accuracy evidence.
