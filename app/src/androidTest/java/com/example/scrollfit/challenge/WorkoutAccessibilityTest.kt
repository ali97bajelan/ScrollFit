package com.example.scrollfit.challenge

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.fetchSemanticsNode
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.scrollfit.R
import com.example.scrollfit.feedback.AudioStatus
import com.example.scrollfit.platform.pose.CameraPoseStatus
import com.example.scrollfit.ui.ScrollFitTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WorkoutAccessibilityTest {
    @get:Rule val compose = createComposeRule()

    @Test fun challengeCountExposesProgressAndOnePoliteTrackingNode() {
        showWorkout(required = 10)
        compose.onAllNodesWithTag("workout_count").assertCountEquals(1)
        val count = compose.onNodeWithTag("workout_count").fetchSemanticsNode().config
        assertEquals(ProgressBarRangeInfo(3f, 0f..10f, 0), count[SemanticsProperties.ProgressBarRangeInfo])
        assertEquals(compose.activity.getString(R.string.challenge_progress, 3, 10),
            count[SemanticsProperties.StateDescription])
        val status = compose.onNodeWithTag("workout_status").fetchSemanticsNode().config
        assertEquals(LiveRegionMode.Polite, status[SemanticsProperties.LiveRegion])
        val audio = compose.onNodeWithTag("workout_audio").fetchSemanticsNode().config
        assertEquals(Role.Switch, audio[SemanticsProperties.Role])
        assertTrue(audio.contains(SemanticsProperties.ToggleableState))
        compose.onNodeWithTag("workout_audio").assertHasClickAction()
    }

    @Test fun standaloneCountDoesNotInventAProgressTarget() {
        showWorkout(required = null, showAudioControls = false, endLabel = R.string.camera_cancel)
        val count = compose.onNodeWithTag("workout_count").fetchSemanticsNode().config
        assertFalse(count.contains(SemanticsProperties.ProgressBarRangeInfo))
        assertTrue(count[SemanticsProperties.StateDescription].isNotBlank())
        compose.onNodeWithTag("workout_audio").assertDoesNotExist()
        compose.onNodeWithText(compose.activity.getString(R.string.camera_cancel)).assertExists()
    }

    @Test fun cameraErrorsUseTheSinglePoliteStatusNodeImmediately() {
        showWorkout(required = 10, status = CameraPoseStatus.CAMERA_ERROR)
        compose.onAllNodesWithTag("workout_status").assertCountEquals(1)
        val status = compose.onNodeWithTag("workout_status").fetchSemanticsNode().config
        assertEquals(LiveRegionMode.Polite, status[SemanticsProperties.LiveRegion])
        assertEquals(listOf(compose.activity.getString(R.string.camera_error)), status[SemanticsProperties.Text])
    }

    private fun showWorkout(required: Int?, status: CameraPoseStatus = CameraPoseStatus.TRACKING,
        showAudioControls: Boolean = true, endLabel: Int = R.string.workout_end) {
        compose.setContent {
            ScrollFitTheme {
                Box(Modifier.fillMaxSize().testTag("root")) {
                    WorkoutContent(
                        state = WorkoutUiState("run", 3, required,
                            if (required == null) R.string.push_up_repetitions else R.string.challenge_progress,
                            status),
                        speechEnabled = true, speechLoaded = true, audioStatus = AudioStatus.VOICE,
                        touchExplorationEnabled = false, speechError = false,
                        onSpeechChanged = {}, onSpeechRetry = {}, onEnd = {}, helpContent = { Text("Help") },
                        preview = { modifier -> Box(modifier.testTag("preview")) },
                        showAudioControls = showAudioControls, endLabel = endLabel,
                    )
                }
            }
        }
        compose.onNodeWithTag("root").assertExists()
        compose.onNodeWithTag("workout_count").assertExists()
    }
}
