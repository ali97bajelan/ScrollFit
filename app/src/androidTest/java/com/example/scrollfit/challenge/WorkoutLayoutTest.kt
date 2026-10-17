package com.example.scrollfit.challenge

import android.content.res.Configuration
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material3.Text
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.scrollfit.R
import com.example.scrollfit.feedback.AudioStatus
import com.example.scrollfit.platform.pose.CameraPoseStatus
import com.example.scrollfit.ui.ScrollFitTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale

@RunWith(AndroidJUnit4::class)
class WorkoutLayoutTest {
    @get:Rule val compose = createComposeRule()

    @Test fun portraitAtTwoHundredPercentKeepsPreviewAndActionsOnScreen() {
        assertWorkoutFits(width = 320, height = 480, fontScale = 2f, direction = LayoutDirection.Ltr)
    }

    @Test fun portraitAtDefaultTextKeepsPreviewAndActionsOnScreen() {
        assertWorkoutFits(width = 320, height = 480, fontScale = 1f, direction = LayoutDirection.Ltr)
    }

    @Test fun shortLandscapeAtDefaultTextKeepsPreviewAndActionsOnScreen() {
        assertWorkoutFits(width = 640, height = 320, fontScale = 1f, direction = LayoutDirection.Ltr)
    }

    @Test fun shortLandscapeAtTwoHundredPercentKeepsPreviewAndActionsOnScreen() {
        assertWorkoutFits(width = 640, height = 320, fontScale = 2f, direction = LayoutDirection.Ltr)
    }

    @Test fun shortLandscapeTrialKeepsPreviewAndCameraCloseAtLargeText() {
        assertWorkoutFits(width = 640, height = 320, fontScale = 2f,
            direction = LayoutDirection.Ltr, trial = true)
    }

    @Test fun portraitTrialKeepsPreviewAndCameraCloseAtDefaultText() {
        assertWorkoutFits(width = 320, height = 480, fontScale = 1f,
            direction = LayoutDirection.Ltr, trial = true)
    }

    @Test fun portraitTrialKeepsPreviewAndCameraCloseAtLargeText() {
        assertWorkoutFits(width = 320, height = 480, fontScale = 2f,
            direction = LayoutDirection.Ltr, trial = true)
    }

    @Test fun shortLandscapeTrialKeepsPreviewAndCameraCloseAtDefaultText() {
        assertWorkoutFits(width = 640, height = 320, fontScale = 1f,
            direction = LayoutDirection.Ltr, trial = true)
    }

    @Test fun shortLandscapeTrialFitsFrenchLengthDisclosureAtLargeText() {
        assertWorkoutFits(width = 640, height = 320, fontScale = 2f,
            direction = LayoutDirection.Ltr, trial = true, frenchLengthLabels = true)
    }

    @Test fun recoveryControlsScrollWithoutHidingPreviewOrEnd() {
        assertWorkoutFits(width = 640, height = 320, fontScale = 2f,
            direction = LayoutDirection.Ltr, trial = true, recoveryControls = true)
    }

    @Test fun rtlPortraitKeepsFullActionTargetsAtLargeText() {
        assertWorkoutFits(width = 320, height = 480, fontScale = 2f, direction = LayoutDirection.Rtl)
    }

    private fun assertWorkoutFits(width: Int, height: Int, fontScale: Float, direction: LayoutDirection,
        trial: Boolean = false, frenchLengthLabels: Boolean = false, recoveryControls: Boolean = false) {
        var endClicks = 0
        compose.setContent {
            val context = LocalContext.current
            val configuration = LocalConfiguration.current
            val localizedConfiguration = remember(configuration, frenchLengthLabels) {
                Configuration(configuration).apply {
                    if (frenchLengthLabels) setLocale(Locale.FRENCH)
                }
            }
            val localizedContext = remember(context, localizedConfiguration, frenchLengthLabels) {
                if (frenchLengthLabels) context.createConfigurationContext(localizedConfiguration) else context
            }
            CompositionLocalProvider(
                LocalContext provides localizedContext,
                LocalConfiguration provides localizedConfiguration,
                LocalDensity provides Density(density = 1f, fontScale = fontScale),
                LocalLayoutDirection provides direction,
            ) {
                ScrollFitTheme {
                    Box(Modifier.testTag("workout_root").requiredSize(width.dp, height.dp)) {
                        WorkoutContent(
                            state = WorkoutUiState("run", 3, if (trial) null else 10,
                                if (trial) R.string.push_up_repetitions else R.string.challenge_progress,
                                CameraPoseStatus.TRACKING),
                            speechEnabled = true, speechLoaded = true, audioStatus = AudioStatus.VOICE,
                            touchExplorationEnabled = false, speechError = false,
                            onSpeechChanged = {}, onSpeechRetry = {}, onEnd = { endClicks++ }, helpContent = { Text("Long privacy and exercise instructions") },
                            preview = { modifier -> Box(modifier.testTag("workout_preview")) },
                            showAudioControls = !trial,
                            endLabel = if (trial) R.string.camera_cancel else R.string.workout_end,
                            header = {
                                if (trial) {
                                    Text(stringResource(R.string.camera_title),
                                        style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
                                    Text(stringResource(R.string.camera_trial_disclosure),
                                        style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
                                }
                            },
                            scrollExtraControls = recoveryControls,
                            extraControls = {
                                if (recoveryControls) repeat(5) { index ->
                                    OutlinedButton(onClick = {}, modifier = Modifier.fillMaxWidth()
                                        .heightIn(min = 48.dp).testTag("recovery_control_$index")) {
                                        Text("Recovery action $index")
                                    }
                                }
                            },
                        )
                    }
                }
            }
        }
        compose.onNodeWithTag("workout_root").assertExists()
        compose.onNodeWithTag("workout_preview").assertExists()
            .assertWidthIsAtLeast(96.dp).assertHeightIsAtLeast(96.dp)
        compose.onAllNodesWithTag("workout_count").assertCountEquals(1)
        compose.onNodeWithTag("workout_status").assertExists()
        if (trial) compose.onNodeWithTag("workout_audio").assertDoesNotExist()
        else compose.onNodeWithTag("workout_audio").assertExists().assertHeightIsAtLeast(48.dp)
        compose.onNodeWithTag("workout_help").assertExists().assertHeightIsAtLeast(48.dp)
        compose.onAllNodesWithTag("workout_end").assertCountEquals(1)
        val root = compose.onNodeWithTag("workout_root").getUnclippedBoundsInRoot()
        (listOf("workout_preview", "workout_count", "workout_status", "workout_help", "workout_end") +
            if (trial) emptyList() else listOf("workout_audio"))
            .forEach { tag ->
                val bounds = compose.onNodeWithTag(tag).getUnclippedBoundsInRoot()
                assertTrue("$tag exceeds width at $width x $height, fontScale=$fontScale",
                    bounds.left >= root.left && bounds.right <= root.right)
                assertTrue("$tag exceeds height at $width x $height, fontScale=$fontScale",
                    bounds.top >= root.top && bounds.bottom <= root.bottom)
            }
        if (recoveryControls) {
            compose.onNodeWithTag("recovery_control_4").performScrollTo()
            compose.onNodeWithTag("recovery_control_4").assertExists()
            listOf("workout_preview", "workout_end", "recovery_control_4").forEach { tag ->
                val bounds = compose.onNodeWithTag(tag).getUnclippedBoundsInRoot()
                assertTrue("$tag remains reachable while recovery controls scroll",
                    bounds.left >= root.left && bounds.right <= root.right &&
                        bounds.top >= root.top && bounds.bottom <= root.bottom)
            }
        }
        compose.onNodeWithTag("workout_end").performClick()
        compose.runOnIdle { assertEquals(1, endClicks) }
    }
}
