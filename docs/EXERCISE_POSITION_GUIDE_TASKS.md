# Exercise position guides — tasks, specifications, plans and QA

Created: 2026-10-24. Status: **PG01–PG05 implemented; QA not run. PG06 remains pending.** PG01 follows the proposed content and artwork requirements below; PG01–PG05 have not been qualified on devices or with assistive technology.

## Objective

Before each exercise, help the user understand **where to put the phone, where to stand or lie in its view, how to perform a complete counted repetition, and how to recover when tracking fails**. Keep guidance available during exercise without hiding progress or End.

Entry point: [project README](../README.md). This package supplements T06–T12 and the [U2 workout design](superpowers/specs/2026-10-17-ux-workout-design.md); it does not mark those tasks complete or assume all older plans were implemented.

## What exists today

Source inspected on 2026-10-24:

- `core/.../exercise/Exercise.kt` defines push-ups, squats, jumping jacks and alternating knee raises. The four counters own repetition validation; guide illustrations must match their complete cycles.
- `core/.../exercise/ExerciseGuide.kt` now defines one viewpoint, complete required landmark groups and static demonstration sequence per exercise. `app/.../guide/` provides localized resource mappings and bundled Compose illustrations.
- `app/.../challenge/ChallengeScreen.kt` shows preparation and starts a persisted challenge before opening the live camera. `ChallengeFlow.start()` owns terms and challenge identity. There is no dedicated positioning stage in these sources.
- `WorkoutContent.kt` already provides a bounded preview, count, one status message, Help, audio and End. Preserve its portrait/landscape and large-text behavior.
- `CameraViewport.kt` owns fresh counters by run key. Its point overlay is debug-only. `CameraPoseSession.kt` runs raw inference/counters on one worker; UI delivery can coalesce. Its `CameraPoseState.sample` is filtered and may be null when required joints disappear.
- `PreviewProjection` uses FIT_CENTER letterboxing and front-preview mirroring. Landmark indices remain anatomical and unmirrored. Never mirror the physical overlay because the UI is RTL.
- Localized text already exists in Persian (`values`), English and French, including exercise-specific `*_counter_guidance` strings. The current generic loss text can claim the body is out of view even when confidence is the actual problem; replace that claim with evidence-based guidance.
- README delivery/build claims are historical and differ from newer source/task notes. This documentation does not reverify builds, APKs or phones. Record fresh evidence in PG06.

Full source roots used below: `core/src/main/kotlin/com/example/scrollfit/core/`, `app/src/main/java/com/example/scrollfit/`, `platform/src/main/kotlin/com/example/scrollfit/platform/`.

## Proposed experience and alternatives

| Approach | Benefit | Cost / limitation | Decision |
|---|---|---|---|
| Static instruction cards only | Smallest change; usable without camera permission | Cannot help the user correct actual framing | Keep as an accessible fallback |
| Exercise demo + live framing + explicit start | Explains movement and helps users find the camera range | Needs reliable geometry, session isolation and device QA | **Recommended for this package** |
| Full posture coaching / 3D avatar | Could provide detailed movement feedback | Current detectors do not support professional form assessment; larger scope | Defer |

Journey: **Prepare terms → Learn → Position → Start → 3–2–1 → Exercise → Saved result**. Learn has an explicit Continue action and is always reopenable; no timed auto-dismiss. Users may go directly to Position after reviewing the guide, with Help remaining available. No tutorial-completion preference or auto-start is introduced.

- **Learn:** show one local two/three-keyframe demonstration, camera viewpoint, phone placement, visible body parts and the exact one-rep rule. Viewable before camera permission.
- **Position:** camera preview plus a light exercise-specific target outline; one actionable status; front/rear selector; Help; persistent exit. Start is enabled only after stable framing and valid terms/permission. Explain the disabled state nearby.
- **Countdown:** only after an explicit Start and successful challenge creation. Large localized 3–2–1, optional offline sound; no reps accepted. At the end, bind a fresh counter for the challenge. Recheck readiness and terms as specified in PG04.
- **Exercise:** retain the existing large count, status, Help, audio and End. Fade the target outline after starting; restore it on tracking trouble. Framing advice is advisory during movement: valid exercise endpoints can change the body silhouette substantially.

## Shared requirements (apply to every task)

1. Use existing Kotlin, Compose Material 3, CameraX and MediaPipe. Keep pinned dependencies, minSdk 28, target/compileSdk 37 and JVM target 17. No library/toolchain upgrades are part of this feature.
2. All four exercises have a complete guide. Reuse existing counters and reward policy. A guide, practice movement or readiness result never grants credit or writes activity/history.
3. Keep camera processing offline. No frame recording, upload, screenshot telemetry, raw landmark persistence or runtime media/model download. Guide artwork is bundled vector/Compose content.
4. Support Persian RTL, English, French, localized numbers, light/dark themes, TalkBack, mute and reduced motion. Minimum interactive target: 48 dp. Text contrast target: 4.5:1 for normal text, 3:1 for large text; essential overlay strokes/icons: 3:1 against an opaque backing.
5. At 320 dp available width and 200% font, Start/End and recovery actions remain reachable. Reserve controls first and reduce the preview; scroll instructions inside their sheet. In short landscape use side-by-side layout. Apply system/IME insets at the host once.
6. Readiness means **the required body parts are visible for tracking**, never correct/safe form. No body score, medical claim, estimated distance in meters, or inferred lighting diagnosis. If the image is uncertain, say so and offer lighting/placement tips as suggestions.
7. Fit guides to the actual camera image rectangle, excluding letterbox bars. Front preview mirrors geometry once; rear does not. RTL changes text/layout direction, never camera coordinates or anatomical sides.
8. Keep explicit Start, immutable offers, fresh challenge identities, existing interruption semantics and exactly-once awards. Background, rotation/recreation, navigation, camera switch and fatal camera/model errors invalidate unfinished attempts. Completed awards remain valid.
9. Help remains available in active exercise. Opening an in-app help sheet keeps the run; background/system navigation interrupts it. Guide/camera state is ephemeral; never restore unfinished repetitions after process death.
10. All thresholds below are proposed configurable defaults, not measured recognition quality. PG06 must record calibration results before release. A blocked/unrun check stays **BLOCKED/NOT RUN**, never PASS.

## Ordered task register

| ID | Deliverable | Depends on | Priority | Spec | Plan | QA | Status |
|---|---|---|---|---|---|---|---|
| PG01 | Four exercise guide definitions and movement illustrations | — | P0 | [Spec](superpowers/specs/2026-10-24-pg01-guide-content-design.md) | [Plan](superpowers/plans/2026-10-24-pg01-guide-content.md) | [Scenarios](qa/position-guide/PG01.md) | IMPLEMENTED — QA NOT RUN |
| PG02 | Deterministic framing evidence and stable readiness | PG01 | P0 | [Spec](superpowers/specs/2026-10-24-pg02-framing-design.md) | [Plan](superpowers/plans/2026-10-24-pg02-framing.md) | [Scenarios](qa/position-guide/PG02.md) | IMPLEMENTED — QA NOT RUN |
| PG03 | Learn screen, Position screen and correctly aligned overlay | PG01, PG02 | P0 | [Spec](superpowers/specs/2026-10-24-pg03-guide-ui-design.md) | [Plan](superpowers/plans/2026-10-24-pg03-guide-ui.md) | [Scenarios](qa/position-guide/PG03.md) | IMPLEMENTED — QA NOT RUN |
| PG04 | Explicit start, countdown and isolated camera/challenge runs | PG02, PG03 | P0 | [Spec](superpowers/specs/2026-10-24-pg04-workout-flow-design.md) | [Plan](superpowers/plans/2026-10-24-pg04-workout-flow.md) | [Scenarios](qa/position-guide/PG04.md) | IMPLEMENTED — QA NOT RUN |
| PG05 | Localized accessible prompts and offline audio arbitration | PG02–PG04 | P0 | [Spec](superpowers/specs/2026-10-24-pg05-accessible-feedback-design.md) | [Plan](superpowers/plans/2026-10-24-pg05-accessible-feedback.md) | [Scenarios](qa/position-guide/PG05.md) | IMPLEMENTED — QA NOT RUN |
| PG06 | Device qualification, usability checks and evidence report | PG01–PG05 | P0 release gate | [Spec](superpowers/specs/2026-10-24-pg06-qualification-design.md) | [Plan](superpowers/plans/2026-10-24-pg06-qualification.md) | [Scenarios](qa/position-guide/PG06.md) | TODO |

## Acceptance / traceability

| User need / failure mode | Owner | Required evidence |
|---|---|---|
| Know where to put phone and body for every exercise | PG01, PG03 | Four content fixtures + front/rear phone walkthrough |
| Know exactly what movement counts once | PG01 | Demo endpoints matched to four existing counter tests |
| Correct framing without contradictory prompts | PG02, PG05 | Boundary/time tests + physical correction trials |
| Align guide across mirroring, rotation, aspect ratio, RTL | PG02, PG03 | Projection fixtures + front/rear visual device checks |
| Never count practice/countdown or accept stale camera events | PG04 | Fake-camera/repository race tests + persisted-row inspection |
| Guide usable with large text, mute, TalkBack, reduced motion | PG03, PG05 | Compose semantics/layout + actual assistive-technology checks |
| Errors, permissions, interruption, midnight, saving | PG04 | Flow/transaction tests + device scenarios |
| Prove actual usability and camera behavior | PG06 | Evidence matrix; unresolved blockers prevent release claim |

## QA execution rules

Every task's QA document contains reproducible scenario IDs, conditions, actions and expected outcomes. All scenarios begin **NOT RUN**. During implementation record result, commit, device/API, locale/camera, actual versus expected, issue link and evidence path for each ID. Use synthetic landmarks/fakes for automated tests; use consenting manual participants for physical trials. Do not retain camera images for evidence; record UI-only fixtures, test reports and aggregate observations.

PG01 adds the guide catalog, localized copy and local vector illustrations without changing counters, budgets or dependencies. Read the task spec, plan and QA together before continuing with PG02. Source changes since this review require reconciling the affected file/interface descriptions.

Suggested PG01 commit message: `feat: add offline exercise positioning guides`.
