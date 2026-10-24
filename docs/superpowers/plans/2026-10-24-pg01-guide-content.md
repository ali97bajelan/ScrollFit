# PG01 Exercise Guide Content Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task; use superpowers:subagent-driven-development only if explicitly selected. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Provide complete local positioning and movement guides for all four exercises.

**Execution status:** Catalog, resource mappings and static illustrations are implemented. QA ran on 2026-10-24: the app build and source checks passed; the four exercise counter suites passed 114/114 tests using a temporary Gradle exclusion for `CoverageRecorderTest.kt`. The unfiltered core suite still fails to compile that test because its `kotlinx.coroutines` imports are missing from core test dependencies. Emulator QA passed 7/9 scenarios; PG01-02 fails because recovery copy is not rendered, and PG01-07 fails because playback and step-selection controls are absent. See [PG01 QA](../../qa/position-guide/PG01.md). Steps 1, 2, 4 and 5 remain incomplete.

**Architecture:** Android-free guide definitions describe viewpoints, landmark groups and demo poses. The app maps them to localized copy and local Compose/vector illustrations, with no camera dependency.

**Tech Stack:** Existing Kotlin/JUnit 4, Compose Material 3 and Android string resources.

**Spec:** [PG01](../specs/2026-10-24-pg01-guide-content-design.md); [shared requirements](../../EXERCISE_POSITION_GUIDE_TASKS.md).

## Global Constraints

- Keep pinned dependencies, minSdk 28, target/compileSdk 37 and JVM target 17.
- Keep camera processing offline; bundle illustrations; no recording, uploads or remote media.
- Support Persian RTL, English, French, localized numbers, light/dark, TalkBack, mute and reduced motion; targets >= 48 dp.
- Existing counter/reward rules remain authoritative. Practice/guides create no activity/credit.

## Review Focus

- Same-side knee raise is counted today; a left/right pair is two reps (step 1).
- Push-up joints must belong to one full side (step 1).
- New Exercise enum members cannot silently fall back to another guide (step 1).
- Demo art and copy must agree on count endpoints (steps 3–4).
- Permission-denied/offline/reduced-motion users can still understand the guide (step 4).

## Task: Content catalog and illustrations

**Files:**

- Create `core/src/main/kotlin/com/example/scrollfit/core/exercise/ExerciseGuide.kt`.
- Test `core/src/test/kotlin/com/example/scrollfit/core/exercise/ExerciseGuideTest.kt`.
- Create `app/src/main/java/com/example/scrollfit/guide/GuideResources.kt`, `GuideIllustration.kt`.
- Create `app/src/main/res/values/position_guide_strings.xml`, `values-en/position_guide_strings.xml`, `values-fr/position_guide_strings.xml`.
- Test `app/src/androidTest/java/com/example/scrollfit/guide/GuideContentTest.kt`.

**Interfaces:** Consumes `Exercise`, existing counter endpoints and `CameraChoice`. Produces `GuideViewpoint`, `GuidePose`, `ExerciseGuide` and `ExerciseGuides.forExercise(exercise)` exactly as PG01 defines; app resource mapping is exhaustive and separate from core.

- [ ] **1. Add catalog regressions.** Assert guide keys equal `Exercise.entries.toSet()`, side groups equal the two PG01 push-up sets, other groups match exact PG01 sets, and demo order starts/ends at the specified neutral pose. Add named cases `kneeRaiseGuideDescribesEachLiftAsOneRep` and `pushUpGuideAcceptsEitherCompleteSide`; copy assertions belong to app tests, group/pose assertions to core. Do not infer alternation enforcement.
- [ ] **2. Run red.** `./gradlew -p verification/core :core:test --tests '*ExerciseGuideTest' --console=plain`. Expect missing guide types/assertion failures, not an unrelated configuration failure. Record a blocked environment separately.
- [x] **3. Implement catalog, resource mapping and local art.** Implement the PG01 signatures; define all four viewpoints and keyframes. Add matching localized placement/start/count/coverage/recovery strings, English copy aligned with PG01. No network assets. Stills are always available and default paused; playback controls remain a PG03 UI concern.
- [ ] **4. Add UI content checks.** `GuideContentTest`: each exercise exposes its viewpoint, one-rep rule and steps; denied camera permission requires no request to view; static/reduced-motion variant still exposes all steps; knee raise copy does not say one pair equals one rep. Manually compare drawings with counter endpoints; XML equality cannot prove translation meaning or anatomy.
- [ ] **5. Verify.** App build, source checks and 114 exercise counter tests passed; the full core test task remains blocked by the missing coroutine test dependency. Emulator QA recorded 7 PASS and 2 FAIL; expose the recovery cue in the guide, qualify the demo controls under the planned PG03 scope, add the planned guide regression tests, then rerun [PG01 QA](../../qa/position-guide/PG01.md). The planned `GuideContentTest` does not exist yet.
- [x] **6. Review and checkpoint.** Review the diff and record implementation/QA status. Suggested commit: `feat: add offline exercise positioning guides`. No commit created; this request asked for a message, not a commit.

**Handoff:** PG02 consumes landmark groups; PG03 consumes demo/resource mapping. No camera or challenge behavior is implemented here.
