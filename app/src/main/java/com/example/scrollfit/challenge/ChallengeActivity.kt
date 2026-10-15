package com.example.scrollfit.challenge

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.LayoutDirection
import com.example.scrollfit.core.data.ChallengeMode
import com.example.scrollfit.core.exercise.Exercise
import com.example.scrollfit.monitoring.MonitorRuntime

/** Explicit, unexported entry, used only by a visible blocker button. Extras never award credit. */
class ChallengeActivity : ComponentActivity() {
    private data class Request(val target: String?, val exercise: Exercise?, val only: Boolean, val version: Long)
    private var request by mutableStateOf<Request?>(null)
    private var version = 0L
    private var cancelCurrent: (() -> Unit)? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        acceptRequest(intent)
        setContent {
            val direction = if (LocalConfiguration.current.layoutDirection == android.view.View.LAYOUT_DIRECTION_RTL)
                LayoutDirection.Rtl else LayoutDirection.Ltr
            CompositionLocalProvider(LocalLayoutDirection provides direction) {
                MaterialTheme {
                    request?.let { selected ->
                        key(selected.version) {
                            ChallengeScreen(if (selected.only) ChallengeMode.EXERCISE_ONLY else ChallengeMode.JUST_IN_TIME,
                                selected.target, selected.exercise, onClose = { finish() },
                                registerCancellation = { cancelCurrent = it })
                        }
                    }
                }
            }
        }
    }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        cancelCurrent?.invoke() // Invalidate before waiting for Compose to dispose the old content.
        setIntent(intent)
        acceptRequest(intent) // Replacing the keyed flow invalidates/cancels its old session.
    }
    private fun acceptRequest(intent: Intent) {
        request = Request(intent.getStringExtra(TARGET),
            Exercise.entries.firstOrNull { it.name == intent.getStringExtra(EXERCISE) },
            intent.getBooleanExtra(ONLY, false), ++version)
    }
    override fun onResume() { super.onResume(); MonitorRuntime.activityResumed() }
    override fun onPause() { MonitorRuntime.activityPaused(); super.onPause() }

    companion object {
        private const val TARGET = "target"
        private const val EXERCISE = "exercise"
        private const val ONLY = "only"
        fun intent(context: Context, target: String, exercise: Exercise?) =
            Intent(context, ChallengeActivity::class.java).putExtra(TARGET, target)
                .putExtra(EXERCISE, exercise?.name).putExtra(ONLY, exercise == null)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
    }
}
