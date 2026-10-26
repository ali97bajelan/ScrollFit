# PG06 — Device, usability and release qualification

Status: proposed / not executed. Depends on PG01–PG05. Shared requirements: [task register](../../EXERCISE_POSITION_GUIDE_TASKS.md).

## Outcome

Produce an honest evidence report that separates automated logic/layout verification, physical camera behavior and first-use comprehension. A bundled guide is not successful merely because its screen renders.

## Required coverage

- Full matrix: **4 exercises × 2 camera choices × 3 entry paths** (advance earning, blocker earning, exercise-only) = 24 happy-path journeys. Run both cameras only where hardware is present; an unavailable lens must show recovery, never count as camera PASS. Additionally check all four standalone trials for no saved activity/credit.
- For each exercise/lens combination: neutral start, correct complete movements, clipped joints, too close, too far, horizontal offset, brief occlusion, low-confidence image, return to starting pose. Jacks include overhead/open-frame coverage; push-ups include both visible anatomical sides; knee raises include same-side cycles and alternating sides.
- Layout/accessibility: Persian RTL/English/French; light/dark; portrait 320 dp width at 100% and 200% font; short landscape 640×360 dp at both scales; TalkBack on/off, mute, absent offline voice, static/reduced-motion demo. Visual inspection must check actual camera letterboxing/mirroring after 90/180/270-degree rotation.
- Reliability: deny permission, permanent denial, settings return, unavailable lens, model/inference failure, background during countdown/active exercise, process death, repeated Start, midnight, storage latency/failure and completion racing with exit. Inspect saved activity/award rows through test fixtures, never add production debug accounting controls.
- Intended device from README: physical Android 14 / API 34 phone, **if available**. Record actual model/API, preview dimensions, lens and app revision. Do not assume a phone is connected or inherited build blockers remain current.

## Proposed release targets

These are acceptance targets to validate, not existing results:

1. Zero practice/countdown rewards, duplicate awards, stale-run progress or false success after save failure in automated race tests and manual checks.
2. For each exercise/lens, three 10-rep controlled trials of complete movements: at least 9/10 detected in each trial and zero extra reps; ten partial/invalid movements and 30 seconds idle create zero reps. Record manual count and app count independently. Diagnose guide/framing failure separately from an existing counter limitation; do not retune counters silently within this package.
3. In five consenting first-time participant sessions, at least four can position and begin **each** exercise within 60 seconds of opening Learn, in at most two positioning attempts, without coaching. Each can explain the one-rep rule before starting. Participants may stop at any time; incomplete/stopped sessions are recorded, never silently removed from the denominator.
4. All essential actions readable/reachable in the required layout matrix; no color-only cues, overlapping speech or transform mismatch. Confirm contrast using UI fixtures against opaque status/overlay backing.
5. On the intended device, observe at least ten minutes of mixed positioning/exercise. Record time to first preview/readiness, actual inference/update cadence, visible freezes, thermal interruptions and camera release after leaving. Readiness must be achievable in a ten-second stationary, well-framed trial; cadence too low for the 250 ms gap rule is a release blocker requiring explicit threshold review and regression updates.

No measured result may be filled in until run. Failed recognition targets may require a separately scoped counter fix; they still block declaring this experience qualified.

## Deliverable / sign-off

Create `docs/qa/position-guide/QUALIFICATION_REPORT.md` during execution with environment, build/test commands and exit codes, scenario IDs/results, 24-row journey matrix, 8-row lens/exercise accuracy matrix, layout/accessibility coverage, anonymized aggregate usability results and unresolved defects. Evidence logs go in `docs/verification/position-guide/` (check existing ignore rules). Do not capture/store participant camera frames. Reference synthetic UI screenshots for layout if needed.

Record PASS, FAIL, BLOCKED or NOT RUN, tester/date and evidence for every row. Product readiness requires all P0 scenarios passing and any remaining deviations explicitly documented for review. Missing device/build access means BLOCKED, not waived acceptance. Update README/task register only with actual feature and verification state; do not rewrite historical evidence as a new result.
