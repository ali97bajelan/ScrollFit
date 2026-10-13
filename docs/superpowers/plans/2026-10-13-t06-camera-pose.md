# T06 camera and offline pose implementation plan

> **For agentic workers:** Use superpowers:executing-plans to implement this plan inline. The owner requested the next task and a commit message; preserve the current checkout and do not create a commit.

**Goal:** Provide a user-visible, offline camera/pose candidate for T06, with honest build and device verification status.

**Architecture:** CameraX Preview and ImageAnalysis feed one serial worker with latest-frame backpressure. MediaPipe CPU VIDEO mode processes upright, unmirrored images synchronously; the preview alone mirrors the front camera. Core confidence, smoothing, frame admission and projection helpers remain Android-free. Lifecycle generations reject callbacks from stopped sessions.

**Tech stack:** Existing Kotlin, Compose, CameraX and MediaPipe dependencies; bundled Lite float16 model version 1.

**Spec:** `exercise_gate_codex_brief.md`, camera section; `exercise_gate_tasks.md`, T06.

## Global constraints

- Implement T06 only; detectors T07–T10 and awards T11 remain separate.
- Persian RTL UI; English delivery documentation.
- No recording, uploads, runtime downloads or INTERNET permission.
- Preserve unrelated working-tree changes and the user's index.
- Physical preview, accuracy, OEM lifecycle behavior and airplane-mode launch require actual device evidence.

## Review focus

- Stop/background/switch during model initialization or inference must reject stale callbacks and release owned use cases.
- Unavailable/denied/revoked camera and corrupt/missing model must have visible recovery actions.
- Front-camera mirroring and rotation must preserve anatomical landmark identity.
- Low confidence, malformed samples and tracking gaps must reset smoothing without phantom pose continuity.
- Slow inference/UI must have bounded queues and stale-frame rejection.

### Task 1: Testable pose processing

**Files:** create `core/src/main/kotlin/com/example/scrollfit/core/pose/PoseProcessing.kt` and `core/src/test/kotlin/com/example/scrollfit/core/pose/PoseProcessingTest.kt`.

**Interfaces:** consumes existing Landmark/PoseSample; produces PoseFilter.accept(sample, timestampMs), PoseSessionGate.start/stop/admit/isCurrent and PreviewProjection.point.

- [x] Write tests for confidence, malformed coordinates, missing landmarks, stale timestamps, smoothing reset, session invalidation and mirrored FIT_CENTER projection.
- [x] Run isolated core tests before implementation; record the actual result.
- [x] Implement required-landmark confidence checks at 0.5, smoothing alpha 0.35, gap reset at 500 ms, and single-in-flight admission with 250 ms frame freshness.
- [x] Run the full core suite and retain evidence.

### Task 2: Offline model and Android camera pipeline

**Files:** platform pose package, `platform/src/main/assets/pose_landmarker_lite.task`, platform Gradle model validation, Android instrumentation smoke test and model provenance.

**Interfaces:** consumes core pose helpers; produces CameraPoseSession with lifecycle ownership and CameraPoseState; MediaPipePoseEstimator implements PoseEstimator<Bitmap>.

- [x] Download the official versioned asset, record byte count/SHA-256 and upstream licensing links.
- [x] Validate the checksum before packaging and before initializing inference; use only the packaged asset.
- [x] Implement owned Preview/ImageAnalysis binding, one serial executor, rotation, unmirrored inference, single-in-flight admission and coalesced main-thread results.
- [x] Stop on pause, cancel, switch, permission failure and fatal pipeline error; serialize model close behind current inference.
- [x] Prepare Android instrumentation for asset validation, native blank-frame inference and rotation; attempt build/lint/tests and report unavailable checks.

### Task 3: Reachable Persian camera screen and delivery

**Files:** app camera screen/overlay/resources/navigation/manifest/dependencies, README, TASK_STATUS, T06 documentation and commit message.

**Interfaces:** consumes CameraPoseSession/state and persisted camera/lastExercise preferences.

- [x] Add explicit camera preview buttons from the exercise catalog; permission request/settings, front/rear, retry, cancel and debug-only unsaved landmark overlay.
- [x] Restore preferences with visible storage-error recovery; save selections atomically.
- [x] Verify source/resource checks and core suite; request an independent final review.
- [x] Update implementation/evidence/device checklist and generate `docs/T06_COMMIT_MESSAGE.txt` without committing.

## Execution notes

The prior temporary JDK/cache are absent. Baseline invocation stopped at invalid JAVA_HOME before tests. Restore tools before claiming a test pass. The existing Android dependency/SDK blocker may persist. Work in the current checkout as requested by the IDE task; no branch/index mutation is needed.


## Execution record

- Source deliverables implemented in the existing checkout; no index or commit mutation.
- Core tests: 89 passed, zero failures/errors/skips. Pose suite: 18 checks.
- Side-view and stale-main-delivery regressions: two expected assertion failures in a deliberately broken temporary copy; both pass in the genuine suite.
- Model downloaded, bundled and independently checked against pinned hash and byte count; Apache 2.0 provenance/license included.
- Android build/lint/instrumentation attempted online and offline; blocked before compilation at AGP 9.4.0 resolution. Three native/rotation smoke tests are prepared and unrun.
- Independent final review found stale UI publication and rotation changes without recreation; both addressed in source. Actual rotation rebinding, native inference, preview alignment, performance and offline first launch remain device gates.
- Future counters need an explicit worker-side sample/gap feed; the coalesced UI stream is positioning feedback only. No counting or reward behavior is claimed.
- Delivery documentation and generated T06 commit message saved. Full evidence remains under git-ignored docs/verification/T06-*.
