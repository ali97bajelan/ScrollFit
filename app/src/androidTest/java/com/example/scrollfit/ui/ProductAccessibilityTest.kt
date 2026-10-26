package com.example.scrollfit.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.scrollfit.R
import com.example.scrollfit.core.data.CameraChoice
import com.example.scrollfit.core.exercise.Exercise
import com.example.scrollfit.core.pose.FramingIssue
import com.example.scrollfit.guide.ExerciseGuideContent
import com.example.scrollfit.guide.PositionContent
import com.example.scrollfit.guide.PositionUiState
import com.example.scrollfit.platform.pose.CameraPoseStatus
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProductAccessibilityTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun disabledStartExplainsTheCameraProblemAndRecoveryStaysReachable() {
        var retries = 0
        compose.setContent {
            CompositionLocalProvider(
                LocalDensity provides Density(1f, 2f),
                LocalLayoutDirection provides LayoutDirection.Rtl,
            ) {
                ScrollFitTheme {
                    Box(Modifier.requiredSize(640.dp, 360.dp).testTag("position_root")) {
                        PositionContent(
                            state = PositionUiState(
                                exercise = Exercise.SQUAT,
                                camera = CameraChoice.FRONT,
                                issue = FramingIssue.SHOW_REQUIRED_PARTS,
                                ready = false,
                                starting = false,
                                status = CameraPoseStatus.CAMERA_ERROR,
                                startAllowed = false,
                            ),
                            onStart = {}, onCameraChanged = {}, onHelp = {}, onClose = {},
                            preview = { modifier -> Box(modifier.testTag("position_preview")) },
                            onRequestPermission = {}, onOpenSettings = {}, onRetryCamera = { retries++ },
                        )
                    }
                }
            }
        }

        val expectedDescription = compose.activity.getString(R.string.position_start_disabled,
            compose.activity.getString(R.string.camera_error))
        val start = compose.onNodeWithText(compose.activity.getString(R.string.position_start))
        start.assertIsNotEnabled()
        assertEquals(expectedDescription, start.fetchSemanticsNode().config[SemanticsProperties.StateDescription])
        compose.onNodeWithText(compose.activity.getString(R.string.workout_help))
            .assertHasClickAction().assertHeightIsAtLeast(48.dp)
        val retry = compose.onNodeWithText(compose.activity.getString(R.string.camera_retry))
        retry.assertHasClickAction().assertHeightIsAtLeast(48.dp)
        retry.performSemanticsAction(SemanticsActions.OnClick) { it() }
        compose.runOnIdle { assertEquals(1, retries) }
    }

    @Test fun guidePlaybackControlsAreLabeledAndReachable() {
        compose.setContent {
            ScrollFitTheme {
                Box(Modifier.fillMaxSize()) {
                    ExerciseGuideContent(Exercise.JUMPING_JACK, onContinue = {}, onClose = {})
                }
            }
        }

        listOf(R.string.position_demo_previous, R.string.position_demo_play, R.string.position_demo_next)
            .forEach { resource ->
                compose.onNodeWithText(compose.activity.getString(resource))
                    .assertHasClickAction().assertHeightIsAtLeast(48.dp)
            }
        compose.onNodeWithText(compose.activity.getString(R.string.position_continue))
            .assertHasClickAction().assertHeightIsAtLeast(48.dp)
        compose.onNodeWithText(compose.activity.getString(R.string.position_demo_pause)).assertDoesNotExist()
    }
}
