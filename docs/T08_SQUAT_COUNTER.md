# T08 squat counter

Implemented 2026-10-13 (Asia/Tehran). Scope: T08 and a generated commit
message. No git commit was created. T09–T16 remain outside this request.
Android compilation and the intended-device trial remain pending.

## Detector behavior

`SquatCounter` implements the existing Android-free `ExerciseCounter`
interface with its own `SquatConfig`, smoothing, calibration and cycle state.
Feed raw upright, unmirrored poses in monotonic order on a serial caller.
Both shoulders, hips, knees and ankles (11/12/23/24/25/26/27/28) must be
finite, in frame and have presence and visibility of at least 0.5.
Missing either leg, degenerate torso/leg segments or inverted geometry
pauses tracking and discards incomplete movement.

For each side, vertical hip-to-ankle height is divided by the projected
shoulder-to-hip length. Torso length and hip–knee–ankle angles account for
the upright image aspect ratio. Normalization makes image translation,
mirroring and uniform scale changes leave the movement cue unchanged.
Both sides must lower relative to their own stable standing references;
one leg moving alone cannot qualify as the lowered endpoint.

Front-view knee flexion can be hidden in the image projection, so lowering
uses hip height rather than requiring a small projected knee angle.
Knee angles confirm extended legs for standing acquisition and return.
The reference uses a bounded median of raw heights during stable initial
standing, requiring at least three observations, then remains fixed until tracking loss or reset. This avoids
calibrating from the smoothing tail or permanently raising the reference
after one noisy height sample.

Provisional defaults:

| Setting | Default |
| --- | --- |
| Lowered hip height, each side | At most 75% of its standing reference |
| Returned hip height, each side | At least 90% of its standing reference |
| Initial hip height / torso length | At least 1.4 on both sides |
| Standing knee angle | At least 155° on both sides |
| Height and angle EMA alpha | 0.35 |
| Stable endpoint dwell | 120 ms |
| Minimum confirmed-phase to opposite-candidate time | 250 ms |
| Maximum confirmed-lowered to completed-standing time | 10,000 ms |
| Maximum consecutive sample gap | 500 ms |
| Required presence and visibility | 0.5 each |

Only stable **standing → lowered → standing** counts. Starting lowered,
partial lowering, incomplete return, jitter, isolated dips, too-fast
transitions and expired lowered holds do not earn repetitions. Observed
loss immediately clears calibration, smoothing and partial movement;
completed repetitions survive within the running trial. Silent gaps over
500 ms do the same. Duplicate, negative and out-of-order timestamps do
not mutate state. Mismatched sample/callback timestamps discard the
partial cycle. `reset()` clears the complete trial, including counts.

These are movement thresholds for device tuning, not posture/depth grading,
universal accuracy claims or robust anti-cheat. Camera tilt, torso leaning,
occlusion and perspective may cause misses or false positives. The phone
must stay fixed, and the user should start by standing still with the full
body visible. Both legs must remain visible even at a slight angle.

## Camera trial

Open Exercises → Squat → **باز کردن دوربین**. Persian guidance explains
fixed front/slight-angle full-body placement, standing acquisition,
lowering and returning to standing. The screen displays completed squats
and existing positioning/tracking feedback. A visible notice explains
that this temporary trial awards no time.

The existing session feeds every fresh raw inference to the counter on
its serial camera worker before debug-preview smoothing and UI coalescing.
Counting progress survives a delayed tracking-loss geometry update.
Backgrounding, exit, rotation/rebinding, lens changes, retry and camera
restart reset this temporary trial. No frame/landmark storage, upload,
new dependency, activity history or award integration was added. T11 owns
durable challenge and reward integration.

## Verification

Actual JVM command:

```bash
env JAVA_HOME=/tmp/scrollfit-toolchain/jdk-21.0.12.1+1 \
  GRADLE_USER_HOME=/tmp/scrollfit-gradle \
  bash /tmp/scrollfit-toolchain/gradle-9.6.0/bin/gradle \
  -p verification/core :core:test --offline --console=plain
```

Exit **0**: **141 tests, zero failures/errors/skips** (31 new squat checks
and 110 existing JVM checks). Squat
cases cover complete and repeated cycles, front/slight-angle geometry,
aspect ratio, mirroring/scale/translation, gradual movement, individual
leg references and body proportions, partial cycles, bent-knee return,
jitter/smoothing, implausible timing, gaps/expiry, loss/reappearance,
all required joints, malformed geometry, timestamps, reset and configuration.
See the actual [test log](verification/T08-core-tests.log),
[totals](verification/T08-test-summary.txt) and
[squat JUnit XML](verification/T08-squat-tests.xml).

The initial sandbox invocation stopped before configuration because
Gradle could not initialize its wildcard-IP/socket service. Offline local
verification outside that restriction ran. The test-first invocation then
failed compilation because the new counter/config did not yet exist;
this is not a behavioral regression result.

Independent review found a standing-height spike could poison a maximum-
based calibration. Two focused regression tests actually failed before
the bounded-median/frozen-reference fix; the red log and XML are retained
under `docs/verification/T08-calibration-regression-red.*`. The final
suite verifies those same cases alongside all existing detectors. A separate
sparse-frame regression failed before acquisition required at least three
observations; its red log/XML are retained under
`docs/verification/T08-sparse-calibration-red.*`.

The Android command uses the same runner/environment with
`:app:assembleDebug :app:assembleRelease :app:lintDebug
:platform:assembleDebugAndroidTest :platform:connectedDebugAndroidTest
--offline --console=plain`. It exits **1** at offline AGP 9.4.0 resolution,
before compilation, lint or instrumentation. This attempt does not recheck
Google Maven reachability. No APK, SDK/device inference, physical trial
or measured accuracy result is available. The required device trial is
**pending**; T08 is implemented, with device verification outstanding.

See [phone checks](T08_PHONE_TEST.md) and
[generated commit message](T08_COMMIT_MESSAGE.txt). Verification artifacts
are retained locally in git-ignored `docs/verification`.
