package com.example.scrollfit.spike

import android.accessibilityservice.AccessibilityService
import android.app.AppOpsManager
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast
import com.example.scrollfit.R
import com.example.scrollfit.core.spike.SpikeCountdown
import com.example.scrollfit.core.usage.BlockReason

/** Debug-only T02 feasibility experiment. Receives package transitions, never screen content. */
class SpikeAccessibilityService : AccessibilityService() {
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var store: SpikeStore
    private lateinit var overlay: SpikeOverlay
    private lateinit var usage: SpikeUsageEvents
    private var clock = SpikeCountdown()
    private var connected = false
    private var validTarget: String? = null
    var targetForeground = false
        private set
    var errorResource: Int? = null
        private set
    val blocked: Boolean get() = connected && overlay.blocked
    val remainingMillis: Long get() = clock.remainingMillis

    private val tick = Runnable { refresh() }
    private val reconcile = Runnable {
        targetForeground = usage.foregroundPackage()?.let { it == validTarget } ?: false
        refresh()
    }
    private val preferencesListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == "enabled" || key == "target") configure()
    }
    private val usageListener = AppOpsManager.OnOpChangedListener { operation, _ ->
        if (operation == AppOpsManager.OPSTR_GET_USAGE_STATS) handler.post {
            targetForeground = false
            refresh()
        }
    }
    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            targetForeground = false
            handler.removeCallbacks(reconcile)
            refresh()
            if (intent.action == Intent.ACTION_USER_PRESENT && store.enabled) {
                handler.postDelayed(reconcile, 200)
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        if (connected) cleanup()
        store = SpikeStore(this)
        usage = SpikeUsageEvents(this)
        overlay = SpikeOverlay(
            this,
            home = { navigateSystem(GLOBAL_ACTION_HOME) },
            systemPanel = { navigateSystem(GLOBAL_ACTION_QUICK_SETTINGS) },
            stop = { stopTest() },
            onFailure = { fail(R.string.spike_overlay_failed) },
        )
        connected = true
        SpikeRuntime.connected(this)
        store.preferences.registerOnSharedPreferenceChangeListener(preferencesListener)
        requireNotNull(getSystemService(AppOpsManager::class.java)).startWatchingMode(
            AppOpsManager.OPSTR_GET_USAGE_STATS, packageName, usageListener,
        )
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(screenReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        else {
            @Suppress("DEPRECATION")
            registerReceiver(screenReceiver, filter)
        }
        configure()
    }

    private fun configure() {
        if (!connected) return
        // Start/reset/stop are explicit spike operations, separate from production accounting.
        handler.removeCallbacksAndMessages(null)
        overlay.dismiss()
        targetForeground = false
        errorResource = null
        validTarget = SpikeApps.list(this).firstOrNull { it.packageName == store.targetPackage }?.packageName
        clock = SpikeCountdown(consumedMillis = store.consumedMillis)
        if (store.enabled && validTarget == null) errorResource = R.string.spike_target_unavailable
        refresh()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val transition = event ?: return
        if (!connected || !store.enabled || transition.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        // Read only package metadata. Do not access text, source, nodes or rootInActiveWindow.
        val eventPackage = transition.packageName?.toString()
        // MainActivity.onResume explicitly pauses real app entry; do not let the banner's
        // own window event masquerade as a transition out of the selected target.
        if (eventPackage == packageName && overlay.visible && targetForeground) return
        handler.removeCallbacks(reconcile)
        targetForeground = validTarget != null && eventPackage == validTarget
        refresh()
    }

    fun onOwnActivityResumed() {
        if (!connected) return
        handler.removeCallbacks(reconcile)
        targetForeground = false
        refresh()
    }

    private fun interactive(): Boolean = requireNotNull(getSystemService(PowerManager::class.java)).isInteractive &&
        !requireNotNull(getSystemService(KeyguardManager::class.java)).isKeyguardLocked

    private fun refresh() {
        if (!connected) return
        handler.removeCallbacks(tick)
        val eligible = store.enabled && validTarget != null && targetForeground && interactive() &&
            SpikeAccess.hasUsageAccess(this) && errorResource == null
        clock.update(SystemClock.elapsedRealtime(), eligible && !overlay.blocked)
        if (clock.consumedMillis != store.consumedMillis && !store.persistConsumption(clock.consumedMillis)) {
            fail(R.string.spike_storage_failed)
            return
        }
        if (!eligible) {
            overlay.dismiss()
            return
        }
        if (clock.exhausted) {
            overlay.show(requireNotNull(validTarget), BlockReason.APP_BUDGET)
        } else {
            overlay.countdown(clock.remainingMillis)
            if (errorResource == null) handler.postDelayed(tick, minOf(1_000L, clock.remainingMillis))
        }
    }

    private fun fail(message: Int) {
        errorResource = message
        targetForeground = false
        clock.update(SystemClock.elapsedRealtime(), false)
        handler.removeCallbacksAndMessages(null)
        overlay.dismiss()
    }

    private fun navigateSystem(action: Int) {
        // System navigation is requested by a visible button, not a background Activity launch.
        if (!performGlobalAction(action)) {
            Toast.makeText(this, R.string.spike_navigation_failed, Toast.LENGTH_LONG).show()
        }
        // Wait for a real transition before dismissing the blocker.
    }

    private fun stopTest() {
        clock.update(SystemClock.elapsedRealtime(), false)
        if (!store.persistConsumption(clock.consumedMillis)) fail(R.string.spike_storage_failed)
        runCatching { store.stop() }.onFailure { fail(R.string.spike_storage_failed) }
        targetForeground = false
        refresh()
    }

    override fun onInterrupt() {
        if (connected) fail(R.string.spike_interrupted)
    }

    override fun onUnbind(intent: Intent): Boolean {
        cleanup()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        cleanup()
        super.onDestroy()
    }

    private fun cleanup() {
        if (!connected) return
        clock.update(SystemClock.elapsedRealtime(), false)
        store.persistConsumption(clock.consumedMillis)
        store.preferences.unregisterOnSharedPreferenceChangeListener(preferencesListener)
        handler.removeCallbacksAndMessages(null)
        requireNotNull(getSystemService(AppOpsManager::class.java)).stopWatchingMode(usageListener)
        runCatching { unregisterReceiver(screenReceiver) }
        overlay.dismiss()
        targetForeground = false
        connected = false
        SpikeRuntime.disconnected(this)
    }
}
