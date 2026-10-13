# T06 camera and offline pose candidate

Implemented 2026-10-13 (Asia/Tehran). Scope: T06 and a generated commit
message. No git commit was created. T07–T16 remain outside this request.

Open the exercise catalog, choose one of the four exercise cards and tap
**باز کردن دوربین**. The screen requests camera access explicitly and
offers app settings after denial, rear/front selection, retry, close and
positioning/tracking feedback. The selected camera and exercise are saved
through the existing atomic DataStore updates. Storage failures are visible
and recoverable. A debug-only landmark overlay renders in memory.
No repetition counts or rewards are connected by T06.

## Frames and coordinates

`CameraPoseSession` binds only its own Preview and ImageAnalysis use cases
to the screen lifecycle. It requires RESUMED visibility and permission.
Pause, cancellation, lens changes, navigation and fatal errors invalidate
the generation before unbinding; model close is queued behind current
inference. Pending initialization cannot bind a stopped generation.
No global `unbindAll`, capture/recording use case or service camera is used.

One serial executor owns model creation, inference and close. CameraX
`STRATEGY_KEEP_ONLY_LATEST` bounds its frame queue. A generation gate
allows one inference at a time and rejects repeated/future/expired
monotonic samples. UI publication coalesces into one pending result and
one runnable. Result age is checked after inference and again on main-thread
delivery (250 ms). The screen reports tracking loss after a 750 ms gap,
and opening errors after five seconds without a first result. These
thresholds are conservative defaults, not measured Mi 11 Lite NE timings.

CameraX sensor timestamps may have an unknown time base; sample timestamps
use `SystemClock.elapsedRealtime()` at analyzer arrival. Latest-frame
backpressure limits queueing, but this does not prove sensor-to-display
latency. Slow native calls are not forcibly interrupted; camera is unbound
immediately and the model closes when the in-flight native call returns.

CameraX `ImageProxy.toBitmap()` handles supported pixel formats/strides;
the shared viewport crop is applied and pixels are rotated once using
`imageInfo.rotationDegrees`. Inference receives upright, unmirrored images.
Landmark indices preserve anatomical left/right; front preview/overlay
alone mirror. Both use cases request 4:3 and use a shared viewport after
the preview has layout. FIT_CENTER projection includes letterboxing. A
display listener restarts the owned session on rotation changes, including
180° changes without Activity recreation, to refresh the viewport. Pixel
alignment on hardware remains pending.

The output is the existing `PoseSample`: monotonic timestamp and 33
MediaPipe normalized x/y/z, visibility and presence values. z follows
MediaPipe's image-relative depth scale; it is not normalized to [0, 1].
Required x/y must be in frame, all values finite and confidence valid.
Presence and visibility must each be at least 0.5 for a required group.
Push-up positioning accepts either complete shoulder/elbow/hip side;
front-view exercises require their relevant full-body joints. Future
detectors still own their confidence and movement-cycle decisions.

Optional EMA smoothing uses alpha 0.35 for geometry while retaining current
confidence. Missing body/confidence and gaps over 500 ms clear continuity;
optional low-confidence landmarks are never interpolated through. Stale
samples cannot change the smoothing history. No counter consumes this
pipeline yet; T07–T11 must carry raw tracking/gap information into each
detector, and must not treat coalesced UI updates as a complete frame stream.

## Offline model and API decisions

The official Lite float16 version 1 task is physically bundled under
`platform/src/main/assets/pose_landmarker_lite.task` (5,777,746 bytes).
SHA-256: `59929e1d1ee95287735ddd833b19cf4ac46d29bc7afddbbf6753c459690d574a`.
The archive contains the pose and landmark TFLite models. `preBuild`
requires the pinned size/hash; initialization rechecks the packaged asset.
There is no runtime downloader or network fallback. Upstream provenance,
model-card license reference and the Apache 2.0 license travel with the asset.

MediaPipe CPU VIDEO mode provides synchronous ordered frame processing
with explicit image lifetime on the background worker. It retains model
tracking across frames; it does not save a video. Segmentation is disabled
and only one body is requested. Each bitmap/MPImage/ImageProxy is released
after use; frames and landmarks are not logged, persisted or uploaded.
The application source requests CAMERA and optional camera hardware,
with no INTERNET, audio, media-storage or foreground-camera permission.
Merged-manifest verification remains blocked with Android compilation.

Official sources checked 2026-10-13:

- [MediaPipe Android setup, synchronous VIDEO inference and normalized results](https://ai.google.dev/edge/mediapipe/solutions/vision/pose_landmarker/android).
- [CameraX latest-frame backpressure and lifecycle analysis](https://developer.android.com/media/camera/camerax/analyze).
- [ImageProxy bitmap conversion](https://developer.android.com/reference/androidx/camera/core/ImageProxy).
- [Shared viewport/crop behavior](https://developer.android.com/reference/androidx/camera/core/ViewPort).
- [Rotation without Activity recreation](https://developer.android.com/media/camera/camerax/orientation-rotation).
- [Lifecycle Compose 2.10.0 release](https://developer.android.com/jetpack/androidx/releases/lifecycle#2.10.0), added explicitly for the screen lifecycle owner.
- [BlazePose GHUM model card and Apache 2.0 attribution](https://storage.googleapis.com/mediapipe-assets/Model%20Card%20BlazePose%20GHUM%203D.pdf).

## Verification and remaining gate

The previous session's `/tmp` JDK/cache were absent. Restored the official
Temurin 21.0.12.1+1 and Gradle 9.6.0 in `/tmp/scrollfit-toolchain`; the
Gradle ZIP matches the wrapper's pinned SHA-256. Dependencies for the
isolated core build were fetched from Maven Central. No SDK, adb,
emulator or attached-device verification is available.

Actual final core command (temporary Gradle runner used because its
distribution had just been restored outside the wrapper cache):

```bash
env JAVA_HOME=/tmp/scrollfit-toolchain/jdk-21.0.12.1+1 \
  GRADLE_USER_HOME=/tmp/scrollfit-gradle \
  bash /tmp/scrollfit-toolchain/gradle-9.6.0/bin/gradle \
  -p verification/core :core:test --offline --console=plain
```

Exit 0; **89 tests, zero failures/errors/skips**, including 18 new pose
checks and the 71 existing checks. Missing-interface test-first runs were
retained. An isolated temporary fault probe checks that the two final
side-view/freshness regressions detect deliberately broken behavior;
its result is recorded in the evidence summary. Android instrumentation
for packaged model integrity, native blank-image inference and 90° rotation
is prepared but **not run**.

The Android command used the same environment/runner with
`:app:assembleDebug :app:assembleRelease :app:lintDebug
:platform:assembleDebugAndroidTest :platform:connectedDebugAndroidTest`.
The first offline attempt stopped at missing Foojay in the restored cache.
The online attempt resolved that plugin but exited 1 at AGP 9.4.0
resolution, before Android compilation. The final offline attempt exits 1
at the same AGP gate. A direct current official Google Maven AGP POM request
returned HTTP 404. This is observed artifact access failure, not proof
that the documented release does not exist. No APK was produced.

XML/TOML, resource references, packaged model hash/archive and whitespace
source checks pass. These do not establish Android compilation, native
inference, accuracy or lifecycle behavior on a phone.

The independent read-only review identified stale main-thread pose delivery
and rotation changes without Activity recreation. Both are addressed in
source; delayed delivery has a core regression test, while actual display
rotation/rebinding requires Android/device execution. Review otherwise
found no concrete initialization/inference cleanup defect. Native and
hardware behavior were explicitly left unverified.

Evidence is retained locally under `docs/verification/T06-*` (as with
earlier tasks, this directory is git-ignored). See [phone checks](T06_PHONE_TEST.md)
and [commit message](T06_COMMIT_MESSAGE.txt). T06 is **implemented**,
with its device completion gate pending.
