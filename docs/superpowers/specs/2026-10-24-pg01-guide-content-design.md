# PG01 — Exercise guide content and local illustrations

Status: implemented; QA run on 2026-10-24 with 7 PASS and 2 FAIL. The remaining failures are documented in [PG01 QA](../../qa/position-guide/PG01.md). Parent requirements and task register: [position-guide tasks](../../EXERCISE_POSITION_GUIDE_TASKS.md).

## Outcome

A user can explain phone placement, required camera coverage, starting position and one counted repetition for any of the four exercises without granting camera access. Instructions describe the current recognizers and do not assess form.

## Exercise content contract

| Exercise | Phone / view | Body coverage | Demo and one repetition | Recovery cue |
|---|---|---|---|---|
| Push-up | Place phone securely beside the movement, aimed at the side; show phone outside the user's movement area | Shoulder, elbow, wrist and hip from **one complete anatomical side**. Full-body illustration is helpful but ankles/head are not recognition prerequisites | Top with arms extended → lower → return to top = 1. Use 3 static keyframes | Return to the top; keep one shoulder-to-wrist chain and its hip visible |
| Squat | Secure phone in front or at a slight angle; aim to include standing and lowered positions | Both shoulders, hips, knees and ankles; head/feet in demo for understandable framing | Stand and pause → lower → stand again = 1. Counter calibrates from standing | Stand upright briefly with both legs visible before trying again |
| Jumping jack | Secure phone in front; leave room above and to both sides for open position | Both shoulders, wrists, hips and ankles through the movement, including wrists overhead | Arms down/feet together → both arms up/feet apart → closed = 1. Three keyframes show both limbs moving together | Return to arms down and feet together; leave space for both raised hands |
| Alternating knee raise | Secure phone in front; include the standing body and lifted knee | Both shoulders, hips, knees and ankles | Stand → one knee up → foot down and stand = 1. Show left then right as two reps | Put both feet down and pause before the next lift; alternating sides is guidance, not enforced |

The counter code is the source of truth for numerical thresholds. Do not expose angle targets or ask a user to chase a detector threshold. Same-side complete knee raises currently count; do not imply a left/right pair counts as one. Push-ups accept either full side, never a mixture of joints from different sides.

## Presentation

Each guide has an exercise title, viewpoint badge, phone-placement drawing, movement keyframes, short starting cue, one-rep sentence, coverage sentence and recovery sentence. Use inclusive neutral figures without body scoring. Distinguish a side-view push-up from standing front-view exercises. Show the phone's viewing cone and the body inside it in the placement drawing; this is instructional artwork, not a measured camera field of view.

Movement keyframes are always available as labeled stills. A Play demo action may cycle the same stills at 800 ms per keyframe, with a Pause action; default is paused and reduced motion keeps it static. No autoplay, flashing, remote video or new media dependency. Users can browse steps with buttons.

## Proposed interfaces / files

- Create `core/.../exercise/ExerciseGuide.kt`: `GuideViewpoint { SIDE, FRONT_OR_SLIGHT_ANGLE, FRONT }`; `GuidePose { PUSH_UP_TOP, PUSH_UP_LOWERED, STANDING, SQUAT_LOWERED, JACK_CLOSED, JACK_OPEN, KNEE_LEFT_UP, KNEE_RIGHT_UP }`.
- `ExerciseGuide(exercise: Exercise, viewpoint: GuideViewpoint, requiredLandmarkGroups: List<Set<Int>>, demonstration: List<GuidePose>)`; `ExerciseGuides.forExercise(exercise: Exercise): ExerciseGuide`.
- Required groups: push-up `{11,13,15,23}` OR `{12,14,16,24}`; squat/knee raise `{11,12,23,24,25,26,27,28}`; jack `{11,12,15,16,23,24,27,28}`. These match `CameraViewport.requiredFor` today.
- Create `app/.../guide/GuideResources.kt` for exhaustive enum-to-string/art mapping and `GuideIllustration.kt` for local Compose/vector artwork. Strings in three new `res/values*/position_guide_strings.xml` bundles. Core contains no Android resource IDs.
- Reuse this catalog from the camera adapter in PG02; avoid a second divergent landmark list.

## Acceptance

Every `Exercise.entries` member has exactly one guide; demonstrations start/end at the appropriate neutral position; localized copy describes the same count rule. Both anatomical push-up sides are supported. Guides load offline with camera permission denied. Existing counter tests remain unchanged and passing.

## Out of scope / choices

No new exercise, injury-specific adaptation, distance estimate or professional form instruction. The local still-frame approach is chosen over downloaded video to preserve offline support and reduced-motion usability. Artwork composition is left to the builder within the required viewpoints, labeled endpoints and accessibility constraints.
