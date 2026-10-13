# T10 alternating standing knee-raise counter

Scope: the independent detector and existing camera trial, per the owner's
T10 request. Challenge lifecycle, durable activity and awards remain T11.
No git commit is created.

## Detector policy

`AlternatingKneeRaiseCounter` implements `ExerciseCounter` using confident,
in-frame bilateral shoulders, hips, knees and ankles (MediaPipe indices
11/12, 23/24, 25/26 and 27/28). It measures each knee's vertical distance
below its hip divided by that side's aspect-corrected torso length. A knee
near hip height establishes a lift; the opposite leg must remain standing.
Aspect-corrected hip–knee–ankle angles identify the standing leg and return.
No standing calibration, world-coordinate assumption or new dependency is
required. Translation, horizontal mirroring and uniform scale preserve the
cues. The input remains the upright, unmirrored image-space pose.

One **standing → left OR right knee lifted → standing** cycle counts one
repetition. A lift alone does not count. A stable return requires both knees
down with standing leg angles. Alternating raises count independently; a
complete same-side cycle also counts, because alternation is guidance rather
than a mandatory gate. Switching to the other raised knee without a stable
standing return, or raising both knees, discards incomplete movement.

Raw and independently EMA-smoothed geometry must agree on the endpoint
before dwell begins. Partial movement, threshold jitter, isolated spikes,
unstable starting posture and implausibly fast transitions cannot complete
a repetition. An expired raised phase requires standing and a fresh lift.
Null/invalid samples, confidence loss, mismatched sample timestamps and
sample gaps clear smoothing and incomplete motion while retaining completed
trial counts. Negative, duplicate and out-of-order callback timestamps
cannot mutate state. `reset()` clears counts, state and timestamp history.

| Provisional setting | Default |
| --- | --- |
| Raised knee vertical drop / torso length | At most 0.35 |
| Standing knee vertical drop / torso length | At least 0.8 |
| Standing hip–knee–ankle angle | At least 155° |
| Geometry EMA alpha | 0.35 |
| Stable endpoint dwell | 100 ms |
| Minimum confirmed-phase to opposite-candidate time | 200 ms |
| Maximum confirmed-lift to completed-return time | 5,000 ms |
| Maximum consecutive sample gap | 500 ms |
| Required presence and visibility | 0.5 each |

Keep the phone fixed in front with shoulders, hips, both knees and ankles
visible. These thresholds need physical-device tuning. Perspective, body
proportions, camera tilt, occlusion and inference noise can cause misses or
false positives. The detector counts visible movement; it does not grade
professional form, verify identity or provide robust anti-cheat.

## Camera trial

Open Exercises → **بالا آوردن متناوب زانو** → **باز کردن دوربین**. The existing
camera screen supplies the detector, Persian full-body front guidance,
completed count and temporary/no-award notice. The existing searching and
tracking-loss messages provide confidence feedback. Every fresh raw pose
reaches the counter on the serial camera worker before preview smoothing
and UI coalescing. Loss preserves completed counts; background, exit,
rotation, lens changes and camera retry reset the restarted trial.

No camera media, landmarks, activity history or rewards are stored by T10.
The existing camera/model ownership and permissions are retained.

## Verification

Actual JVM command:

```bash
env JAVA_HOME=/tmp/scrollfit-toolchain/jdk-21.0.12.1+1 \
  GRADLE_USER_HOME=/tmp/scrollfit-gradle \
  bash /tmp/scrollfit-toolchain/gradle-9.6.0/bin/gradle \
  -p verification/core :core:test --offline --console=plain
```

The actual core sources compile and **203 tests pass, zero
failures/errors/skips**: 32 new knee-raise tests and 171 existing checks.
Coverage includes each side, alternating/same-side cycles, sustained holds,
starting raised, partial lifts/returns, side switches, bilateral motion,
supporting-leg position, gradual cycles, jitter, EMA endpoint agreement,
33 ms cadence, timing/expiry, loss/reappearance, gaps, each joint's confidence,
invalid geometry/samples, timestamps, reset, configurable thresholds and
aspect/mirror/translation/scale transformations. See [test log](verification/T10-core-tests.log),
[totals](verification/T10-test-summary.txt) and [new JUnit XML](verification/T10-knee-raise-tests.xml).

The initial sandbox run stopped before configuration because Gradle could
not initialize its local wildcard-IP/socket service. Outside-sandbox offline
execution succeeded. No test-first behavioral RED result is claimed.
An isolated temporary copy deliberately disables invalid-sample clearing,
raw/EMA endpoint agreement and side-switch/hold expiry checks. Eight of
the 32 tests fail as expected, including the strengthened EMA return
regression. See [probe log](verification/T10-fault-probe.log)
and [probe XML](verification/T10-fault-probe.xml). Workspace sources are
unchanged by this probe. Independent read-only review found no actionable
issues in detector, tests or camera routing.

The Android command uses the same runner/environment with
`:app:assembleDebug :app:assembleRelease :app:lintDebug
:platform:assembleDebugAndroidTest :platform:connectedDebugAndroidTest
--offline --console=plain`. Exit **1** at offline AGP 9.4.0 resolution,
before compilation, lint or instrumentation. [Actual build log](verification/T10-build.log).
No fresh Google Maven reachability check, APK, native inference or phone
accuracy result is claimed. Source resource checks do not establish Android
compilation. T10 is **implemented, not device-verified**; the required
recorded phone trial remains outstanding.

See [pending phone checklist](T10_PHONE_TEST.md) and
[generated commit message](T10_COMMIT_MESSAGE.txt). Evidence files remain
local in git-ignored `docs/verification`.
