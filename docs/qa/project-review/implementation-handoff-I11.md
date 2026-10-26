# I11 implementation handoff — accessibility and physical controls

Status: **IMPLEMENTED — QA NOT RUN**. No accessibility or physical-device acceptance is claimed.

## Source identity

| Field | Value |
| --- | --- |
| Base revision | `a52035464db665e6acb7c586c831fbb5d8d541d7` |
| Dirty source identity | `85c35166e7063c1f4e6f3663e97d6fe680f335a2b7fa1f2e5ed9e4c5cb13461b` — source/test fingerprint; see [CURRENT_VERIFICATION.md](CURRENT_VERIFICATION.md) |
| Matrix | [I11-accessibility-matrix.md](I11-accessibility-matrix.md), 828 explicit condition rows, all NOT RUN |
| Android/physical-device commands | None run during this implementation |

Changed files: `app/src/androidTest/java/com/example/scrollfit/challenge/{WorkoutAccessibilityTest.kt,WorkoutLayoutTest.kt}`, `app/src/androidTest/java/com/example/scrollfit/monitoring/BlockerAccessibilityTest.kt`, `app/src/androidTest/java/com/example/scrollfit/ui/ProductAccessibilityTest.kt`, `core/src/test/kotlin/com/example/scrollfit/core/pose/PoseProcessingTest.kt`, and [I11-accessibility-matrix.md](I11-accessibility-matrix.md).

## Fixtures and source review

Prepared explicit rows for 13 screens × Persian RTL / English LTR / French LTR × light/dark × 320×480 portrait / 640×360 landscape × 100%/200% text; added TalkBack rows at 200% and mute, unavailable offline voice, and reduced-motion branches on Learn, Position, Countdown, Workout and Help. The matrix includes 48 dp target checks, contrast thresholds, directionality/focus order and camera-transform assertions, and supports exact I04 evidence reuse.

Fixtures written, not run:

- `WorkoutAccessibilityTest.helpAndEndRemainLabeledActionsWithMinimumTouchHeight`
- `WorkoutLayoutTest.specifiedShortLandscapeAtTwoHundredPercentKeepsActionsReachable`
- `WorkoutLayoutTest.specifiedShortLandscapeInRtlKeepsActionsReachable`
- `ProductAccessibilityTest.disabledStartExplainsTheCameraProblemAndRecoveryStaysReachable`
- `ProductAccessibilityTest.guidePlaybackControlsAreLabeledAndReachable`
- `BlockerAccessibilityTest.blockerButtonsExposeLabelsAndAtLeast48DpTargetsInReadingOrder`
- `PoseProcessingTest.uprightGuideProjectionUsesImageAspectAndMirrorsOnlyTheFrontPreview`

No production layout or localized resource defect was confirmed from source inspection alone, so no unrelated product redesign or behavior change was made. The fixtures strengthen checks for disabled/recovery descriptions, action reachability, the native blocker hierarchy, guide transform independence from RTL, and minimum control bounds. The matrix still requires actual TalkBack, mute/no-voice/reduced-motion and physical camera checks.

## QA remaining

Scenarios: I11-01 through I11-04 and every row of the linked matrix. Run Compose/native-blocker instrumentation, then manually qualify TalkBack order, both themes, all locales, 48 dp targets, 4.5:1 normal / 3:1 large text / 3:1 essential overlay contrast, mute and unavailable voice visual equivalents, reduced motion, and front/rear camera transforms on the intended device. Record each matrix row separately; I04 may reuse only exact matching conditions and source/artifact identity.
