package com.example.scrollfit

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.scrollfit.ui.ScrollFitApp
import com.example.scrollfit.spike.SpikeRuntime
import com.example.scrollfit.monitoring.MonitorRuntime

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { ScrollFitApp() }
    }

    override fun onResume() {
        super.onResume()
        SpikeRuntime.onActivityResume()
        MonitorRuntime.activityResumed()
    }

    override fun onPause() {
        MonitorRuntime.activityPaused()
        super.onPause()
    }
}
