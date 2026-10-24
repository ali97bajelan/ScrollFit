# PG03 — Learn, Position and camera overlay UI

Status: proposed / not implemented. Depends on PG01–PG02. Shared requirements: [task register](../../EXERCISE_POSITION_GUIDE_TASKS.md).

## Outcome and layout

Create reusable guide UI rather than another exercise-specific camera screen. Reuse `WorkoutContent`'s bounded layout pattern and `CameraViewport` ownership. All text comes from PG01/PG05 resources.

**Learn:** title and view badge; placement drawing showing phone/body/viewing cone; labeled movement keyframes; concise starting/counting/coverage copy. Persistent Continue to position and Back/Close. Longer explanation can scroll; actions remain outside the scroll. Readable without camera access. This screen can be presented from preparation and in an on-demand help sheet.

**Position:** small exercise/view header; flexible camera image with target outline; one status with icon/text; front/rear radio choices; Help; Start and End/Close in a persistent action area. A disabled Start has visible text explaining the current prerequisite (permission, loading, framing, terms or saving). The target outline uses a standing figure for three front-view exercises and a horizontal side figure for push-ups. It is a suggested area, not a body shape the user must match.

**Exercise:** count and status remain dominant. Hide the positioning outline after start while tracking is good; restore a subtle outline when tracking is lost and hide it on recovery. Do not make overlay visibility a counting prerequisite. One line of guidance can wrap; reserve control space before preview. Never overlay text directly onto variable camera pixels without an opaque/scrim backing.

## Geometry

Create `GuideOverlay.kt`. Use the same imageWidth/imageHeight and FIT_CENTER image rectangle as `PreviewProjection`. Map guide and observed geometry through one shared helper; no separate hand-coded scale/crop transform. Missing dimensions means no overlay until geometry is valid. Letterbox bars never count as valid framing space.

Front preview is mirrored once; rear is not. A physical-space arrow/outline must not inherit RTL reversal. Use a generic “Move toward the center of the preview” cue instead of anatomical “left/right.” Push-up artwork can face either side; the cue accepts either complete side. Target geometry follows current image orientation/dimensions after rebind; stale geometry is cleared on switch/rotation.

## Proposed interfaces / paths

- `app/.../guide/ExerciseGuideContent.kt`: `ExerciseGuideContent(exercise: Exercise, onContinue: () -> Unit, onClose: () -> Unit, modifier: Modifier = Modifier)`.
- `app/.../guide/PositionContent.kt`: `PositionUiState(exercise: Exercise, camera: CameraChoice, issue: FramingIssue?, ready: Boolean, starting: Boolean, status: CameraPoseStatus)` and `PositionContent(state: PositionUiState, onStart: () -> Unit, onCameraChanged: (CameraChoice) -> Unit, onHelp: () -> Unit, onClose: () -> Unit, preview: @Composable (Modifier) -> Unit, modifier: Modifier = Modifier)`.
- `app/.../guide/GuideOverlay.kt`: `GuideOverlay(exercise: Exercise, imageWidth: Int, imageHeight: Int, mirrored: Boolean, visible: Boolean, modifier: Modifier = Modifier)`; normalized target outline uses PG02 safe area. It does not draw or persist raw poses in release.
- `core/.../pose/PoseProcessing.kt`: extend shared projection with `PreviewProjection.imageRect(...)` returning a core-owned rectangle value; keep existing `point(...)` callers compatible.
- `CameraViewport.kt`: add an optional composable overlay slot receiving current `CameraPoseState`; default remains empty and debug point overlay stays separate.
- `WorkoutContent.kt`: use its existing preview slot and help sheet; preserve one status semantics node and all existing tests.

## Acceptance

Learn, Position, permission/error and countdown variants meet 320 dp/200% font, short landscape (640×360 dp), Persian RTL, French expansion, light/dark and 48 dp action targets. UI fixtures cover four exercises/front-rear and asymmetric landmark projections. Overlay geometry agrees with preview on real devices as well as synthetic tests. Screen readers receive the textual equivalent; artwork with duplicate meaning is decorative. PG05 supplies audio and announcement behavior.

## Deliberate scope

Choose local Compose/vector drawings over raster generation or online video. No additional navigation framework, design-system replacement, production skeleton tracing, body alignment score or full-screen controls hidden behind gestures.
