# T09 jumping-jack counter

Implemented 2026-10-13 (Asia/Tehran). Scope: T09 and a generated commit
message. No git commit was created. T10–T16 remain outside this request.
Android compilation and the required intended-device trial remain pending.

## Detector policy

`JumpingJackCounter` implements the Android-free `ExerciseCounter` contract
with independent `JumpingJackConfig`, EMA smoothing and cycle state. Feed
raw upright, unmirrored poses in monotonic order on a serial caller. Both
shoulders, wrists, hips and ankles (11/12/15/16/23/24/27/28) must be finite,
inside the frame, and have presence and visibility of at least 0.5.
Missing required joints, degenerate torso/arm spans, inverted geometry,
crossed feet or inconsistent shoulder/hip ordering discard partial movement.

Each arm cue is the angle between shoulder→hip and shoulder→wrist: down is
near 0°, overhead near 180°. Foot separation is horizontal ankle distance
divided by the mean projected shoulder-to-hip length. Each foot must also
extend outward from its own hip to qualify as open, preventing one moving
foot from supplying the entire separation. Image aspect ratio corrects
both angles and lengths. Translation, mirroring and uniform image scaling
preserve these cues; no standing calibration or new dependency is needed.

Both arms and feet must reach the same endpoint simultaneously. Raw and
smoothed geometry must agree on that endpoint before dwell begins. This
prevents an EMA tail from combining earlier raised arms with later spread
feet when those movements never actually formed an open pose together.

Provisional defaults:

| Setting | Default |
| --- | --- |
| Closed arm angle, each arm | At most 30° |
| Open arm angle, each arm | At least 140° |
| Closed ankle separation / torso length | At most 0.65 |
| Open ankle separation / torso length | At least 1.6 |
| Open outward foot displacement / torso length, each foot | At least 0.35 |
| Geometry EMA alpha | 0.35 |
| Stable endpoint dwell | 100 ms |
| Minimum confirmed-phase to opposite-candidate time | 200 ms |
| Maximum confirmed-open to completed-closed time | 5,000 ms |
| Maximum consecutive sample gap | 500 ms |
| Required presence and visibility | 0.5 each |

Only stable **closed → open → closed** counts. Starting open, partial
arms/feet, one-arm/one-foot motion, incomplete returns, jitter, isolated
spikes and implausibly fast transitions cannot complete a repetition.
Open holds beyond five seconds expire. Loss, invalid samples, mismatched
timestamps and silent gaps over 500 ms clear smoothing and incomplete
movement while retaining completed repetitions within the running trial.
Negative, duplicate and out-of-order callback timestamps cannot mutate
state. `reset()` clears counts, smoothing and timestamp history.

These thresholds need device tuning. They count visible movement and do
not grade posture, prove a physical jump, or provide robust anti-cheat.
Perspective, camera tilt, occlusion and noisy inference can cause misses
or false positives. Keep the phone fixed in front, include overhead hands
and both ankles in frame, and begin with feet together and arms down.

## Camera trial

Open Exercises → Jumping jack (**پروانه**) → **باز کردن دوربین**. The
existing camera screen now supplies the independent counter and Persian
full-body front guidance, completed count and a temporary/no-award notice.
Every fresh raw inference reaches the detector on the existing serial
worker before preview smoothing and UI coalescing. Tracking loss keeps
completed counts; background/exit/rotation/lens/retry camera restarts reset
the trial. No frames, landmarks, awards or activity history are stored by
this addition. Durable challenge/award integration remains T11.

## Verification

Actual JVM command:

```bash
env JAVA_HOME=/tmp/scrollfit-toolchain/jdk-21.0.12.1+1 \
  GRADLE_USER_HOME=/tmp/scrollfit-gradle \
  bash /tmp/scrollfit-toolchain/gradle-9.6.0/bin/gradle \
  -p verification/core :core:test --offline --console=plain
```

The full suite passes **171 tests, zero failures/errors/skips**, including
30 new jumping-jack tests and all 141 existing checks. Cases cover full,
repeated and gradual cycles, short endpoint holds at 33 ms sampling,
starting open, partial arms/feet, asymmetric movement, incomplete returns,
asynchronous noise, jitter, smoothing, transition timing, loss/reappearance,
gaps/expiry, confidence for every required joint, malformed geometry,
timestamps, reset, transformations and configuration.
See [log](verification/T09-core-tests.log),
[totals](verification/T09-test-summary.txt) and
[jumping-jack XML](verification/T09-jumping-jack-tests.xml).

The initial sandbox command failed before configuration at Gradle's local
wildcard-IP/socket service. Outside-sandbox offline verification ran. No
test-first behavioral RED result is claimed. An isolated `/tmp` copy then
removed raw endpoint agreement and invalid-sample cycle clearing. Its 29
jumping-jack checks produced four expected failures: asynchronous arm/leg
noise, loss/count retention, open reappearance and mismatched timestamps.
This deliberate-fault probe did not alter workspace sources. See
[probe log](verification/T09-fault-probe.log) and
[probe XML](verification/T09-fault-probe.xml). A subsequent test adds
33 ms cadence coverage. Independent read-only review found no actionable
correctness issues in the detector, tests or camera routing.

The Android command uses the same runner/environment with
`:app:assembleDebug :app:assembleRelease :app:lintDebug
:platform:assembleDebugAndroidTest :platform:connectedDebugAndroidTest
--offline --console=plain`. Exit **1** at offline AGP 9.4.0 resolution
before compilation/lint/instrumentation. [Build log](verification/T09-build.log).
No fresh Google Maven reachability check, APK, native inference or phone
accuracy result is claimed. Structural resource checks do not establish
Android compilation. T09 is **implemented, not device-verified**; its
required recorded device trial is outstanding.

See [phone checklist](T09_PHONE_TEST.md) and
[generated commit message](T09_COMMIT_MESSAGE.txt). Verification artifacts
remain local in git-ignored `docs/verification`.
