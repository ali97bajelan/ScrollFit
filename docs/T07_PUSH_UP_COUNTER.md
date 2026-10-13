# T07 push-up counter

Implemented 2026-10-13 (Asia/Tehran). Scope: T07 and a generated commit
message. No git commit was created. T08–T16 remain outside this request.
T06 device output and physical tuning remain pending.

## Detector behavior

`PushUpCounter` implements the existing Android-free `ExerciseCounter`
interface. Feed it raw, upright, unmirrored samples in monotonic order
on a serial caller, before UI coalescing. It owns its own angle smoothing
and cycle state; other exercises remain independently replaceable.

It requires one complete anatomical side: shoulder, elbow, wrist and hip
(left 11/13/15/23 or right 12/14/16/24). Required joints must be finite,
in frame, with both presence and visibility at least 0.5. Degenerate
arm/torso segments are rejected. At acquisition the most confident complete
side is chosen and held through the cycle. Changing sides discards the
incomplete movement; joints from opposite sides cannot form one arm.

The shoulder–elbow–wrist projected angle uses the upright image width/height
ratio to correct normalized x/y coordinates. `PoseSample.imageAspectRatio`
defaults to 1 for existing square synthetic callers; the MediaPipe adapter
supplies the actual upright bitmap ratio. Depth is not used to grade form.

Provisional `PushUpConfig` defaults:

| Setting | Default |
| --- | --- |
| Up angle | At least 155° |
| Down angle | At most 105° |
| Angle EMA alpha | 0.35 |
| Stable phase dwell | 100 ms |
| Minimum time from confirmed phase to opposite phase candidate | 250 ms |
| Maximum time from confirmed down to completed up | 10,000 ms |
| Maximum consecutive sample gap | 500 ms |
| Required presence and visibility | 0.5 each |

Only a stable **up → down → up** cycle increments the count. Starting down,
holding either endpoint, incomplete returns and movement within the
hysteresis band do not earn repetitions. Too-fast transitions and expired
down phases discard the cycle and require a fresh up baseline.

Any observed missing/low-confidence body immediately discards the partial
cycle and smoothing history, while retaining completed repetitions. This
conservative behavior also covers brief loss; a confident reappearance
cannot finish an old movement. A gap over 500 ms has the same effect even
without a missing-body callback. Duplicate/out-of-order timestamps leave
cycle history untouched and report unavailable tracking; mismatched sample
timestamps are rejected and discard the partial cycle. `reset()` clears
both completed repetitions and movement history for a new trial/session.

These are configurable detector defaults for device tuning, not universal
accuracy claims, professional form grading or robust anti-cheat. Partial
movement may be undercounted, particularly with occlusion or slow inference.

## Camera trial integration

Open Exercises → Push-up → **باز کردن دوربین**. The Persian screen explains
fixed side placement with shoulder, elbow, wrist and hip visible, starting
up and returning up. It displays completed push-ups and existing positioning
and tracking-loss feedback. This is a temporary camera trial; no challenge,
history activity or award is created by T07. T11 owns durable integration.

The optional session counter receives each fresh raw inference on the
camera worker before smoothed debug-preview delivery and UI coalescing.
Results over 250 ms old invalidate the partial movement. Missing/delayed
pose geometry never removes cumulative repetitions from the delivered
progress snapshot. Main-thread stale delivery hides geometry and marks
tracking unavailable while preserving the count already computed from
fresh worker samples.

Stopping/restarting the camera resets this preview trial, including
backgrounding, retry, lens changes, exit and display rotation/rebinding.
The worker resets in queue order before a resumed inference; the main-thread
pause/initialization callback clears displayed counts independently of
Compose recomposition. Tracking loss within a running trial retains counts.
No landmark/frame persistence, recording, upload or new dependency was added.

## Verification and limitations

Actual JVM command:

```bash
env JAVA_HOME=/tmp/scrollfit-toolchain/jdk-21.0.12.1+1 \
  GRADLE_USER_HOME=/tmp/scrollfit-gradle \
  bash /tmp/scrollfit-toolchain/gradle-9.6.0/bin/gradle \
  -p verification/core :core:test --offline --console=plain
```

Exit 0: **110 tests, zero failures/errors/skips**. The 21 new push-up tests
cover complete/repeated cycles, either side, portrait/landscape aspect
ratios, starting down, partial motion, jitter/spikes, fast transitions,
missing/low-confidence tracking and reappearance, silent frame gaps, cycle
expiry, side changes/mixed sides, required joint confidence, malformed and
degenerate samples, stale/mismatched timestamps, angle smoothing, reset
and invalid configuration. Existing 89 JVM tests also pass.

The initial sandbox invocation failed before configuration because Gradle
could not initialize its wildcard-IP/socket service. Outside-sandbox local
verification ran successfully; this initial failure is not a failing
detector test. Logs and actual JUnit XML are retained in the git-ignored
`docs/verification/T07-*` evidence directory.

An isolated `/tmp` copy deliberately retained incomplete movement through
missing-body samples. Its 21 push-up tests produced three expected failures
(loss/reappearance, retained completed counts and mismatched timestamps),
confirming those checks detect the broken behavior. The real workspace
sources were unchanged by the probe. XML/TOML, per-source-set resource
references, the integer count format and whitespace checks also pass;
these are source checks rather than Android compilation.

An independent read-only review identified two integration issues: a rapid
background/resume could retain the preview trial despite its reset notice;
and coalesced tracking-loss delivery could omit the latest completed count.
Both are corrected in source and confirmed by review. These Android paths
still require compilation and device execution; no integration pass is
claimed from source inspection.

The actual Android command uses the same runner/environment with
`:app:assembleDebug :app:assembleRelease :app:lintDebug
:platform:assembleDebugAndroidTest :platform:connectedDebugAndroidTest
--offline --console=plain`. It exits 1 at AGP 9.4.0 plugin resolution,
before compilation, lint or instrumentation. This offline attempt does not
recheck Google Maven reachability. No APK, SDK/device inference, phone
trial or measured accuracy result is available. T07 is **implemented**,
with verification on the intended device pending.

See [phone checks](T07_PHONE_TEST.md) and [generated commit message](T07_COMMIT_MESSAGE.txt).
