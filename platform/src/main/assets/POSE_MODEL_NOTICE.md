# Bundled pose model

Asset: `pose_landmarker_lite.task`, MediaPipe Pose Landmarker Lite, float16,
version 1. Downloaded 2026-10-13 from the versioned official URL:

https://storage.googleapis.com/mediapipe-models/pose_landmarker/pose_landmarker_lite/float16/1/pose_landmarker_lite.task

Size: 5,777,746 bytes. SHA-256:
`59929e1d1ee95287735ddd833b19cf4ac46d29bc7afddbbf6753c459690d574a`.

The task archive contains `pose_detector.tflite` and
`pose_landmarks_detector.tflite`. It is bundled in the APK; no first-launch
or runtime download is implemented. Build preparation and initialization
both check integrity. Updating the model requires reviewing and changing
the pinned hash and byte count deliberately.

Upstream attribution: Google MediaPipe / BlazePose GHUM.
Official model and model-card links:

- https://developers.google.com/edge/mediapipe/solutions/vision/pose_landmarker
- https://storage.googleapis.com/mediapipe-assets/Model%20Card%20BlazePose%20GHUM%203D.pdf
- https://github.com/google-ai-edge/mediapipe/blob/master/LICENSE

The official BlazePose GHUM model card identifies its license as Apache
License 2.0 (page 2). MediaPipe source is also Apache 2.0. The upstream
license is included as `POSE_MODEL_LICENSE.txt`; the task archive itself
contains no separate license file. The upstream developer documentation
has its own content license and Google terms:
https://developers.google.com/terms/site-policies.
