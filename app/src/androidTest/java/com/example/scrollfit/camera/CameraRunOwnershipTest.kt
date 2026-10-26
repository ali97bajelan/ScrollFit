package com.example.scrollfit.camera

import android.content.Context
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.scrollfit.core.data.CameraChoice
import com.example.scrollfit.core.exercise.Exercise
import com.example.scrollfit.platform.pose.CameraPoseState
import com.example.scrollfit.platform.pose.CameraPoseStatus
import com.example.scrollfit.ui.ScrollFitTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CameraRunOwnershipTest {
    @get:Rule val compose = createComposeRule()

    @Test fun helpRecompositionAndLateCallbacksRespectRunOwnership() {
        val factory = RecordingCameraFactory()
        compose.setContent {
            val context = LocalContext.current
            val owner = LocalLifecycleOwner.current
            val preview = remember(context) { PreviewView(context) }
            var runKey by remember { mutableStateOf("first") }
            var helpOpen by remember { mutableStateOf(false) }
            var audioEnabled by remember { mutableStateOf(true) }
            var cameraVisible by remember { mutableStateOf(true) }
            var delivered by remember { mutableIntStateOf(0) }
            ScrollFitTheme {
                Column {
                    Button(onClick = { helpOpen = !helpOpen }, modifier = Modifier.testTag("help")) { Text("Help") }
                    Button(onClick = { audioEnabled = !audioEnabled }, modifier = Modifier.testTag("audio")) { Text("Audio") }
                    Button(onClick = { runKey = "second" }, modifier = Modifier.testTag("replace")) { Text("Replace") }
                    Button(onClick = { cameraVisible = false }, modifier = Modifier.testTag("dispose")) { Text("Dispose") }
                    if (helpOpen || audioEnabled) Text("State")
                    if (cameraVisible) {
                        CameraViewportSurface(context, owner, preview, Exercise.SQUAT, CameraChoice.REAR, runKey,
                            Modifier.size(160.dp), enabled = true, permission = true, resumed = true,
                            debugOverlay = false, runFactory = factory) { delivered++ }
                    }
                    Text("delivered=$delivered", modifier = Modifier.testTag("delivered"))
                }
            }
        }

        compose.waitForIdle()
        assertEquals(1, factory.opens)
        compose.onNodeWithTag("help").performClick()
        compose.onNodeWithTag("audio").performClick()
        compose.waitForIdle()
        assertEquals(1, factory.opens)
        assertEquals(0, factory.closes)

        compose.onNodeWithTag("replace").performClick()
        compose.waitForIdle()
        assertEquals(2, factory.opens)
        assertEquals(1, factory.closes)
        val deliveredBeforeLateEvent = compose.onNodeWithTag("delivered").fetchSemanticsNode().config
        factory.callbacks.first()(CameraPoseState(CameraPoseStatus.TRACKING_LOST))
        compose.waitForIdle()
        compose.onNodeWithTag("delivered").assertExists()
        assertEquals(deliveredBeforeLateEvent, compose.onNodeWithTag("delivered").fetchSemanticsNode().config)

        compose.onNodeWithTag("dispose").performClick()
        compose.waitForIdle()
        assertEquals(2, factory.closes)
    }

    private class RecordingCameraFactory : CameraRunFactory {
        var opens = 0
        var closes = 0
        val callbacks = mutableListOf<(CameraPoseState) -> Unit>()

        override fun open(context: Context, owner: LifecycleOwner, preview: PreviewView, exercise: Exercise,
            camera: CameraChoice, onState: (CameraPoseState) -> Unit): AutoCloseable {
            opens++
            callbacks += onState
            onState(CameraPoseState(CameraPoseStatus.TRACKING))
            return AutoCloseable { closes++ }
        }
    }
}
