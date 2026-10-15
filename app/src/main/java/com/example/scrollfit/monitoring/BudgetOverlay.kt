package com.example.scrollfit.monitoring

import android.accessibilityservice.AccessibilityService
import android.graphics.Color
import android.graphics.PixelFormat
import android.view.*
import android.widget.*
import com.example.scrollfit.R
import com.example.scrollfit.core.budget.SessionAccess
import com.example.scrollfit.core.budget.SessionAction
import com.example.scrollfit.core.usage.BlockReason
import com.example.scrollfit.core.budget.OfferResult
import com.example.scrollfit.core.data.ChallengeMode
import com.example.scrollfit.core.exercise.Exercise
import com.example.scrollfit.core.usage.OverlayState
import com.example.scrollfit.ScrollFitApplication
import com.example.scrollfit.challenge.label
import com.example.scrollfit.challenge.message
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Touch-blocking app-area overlay with system navigation left accessible. */
class BudgetOverlay(private val service: AccessibilityService,
    private val home: () -> Unit, private val panel: () -> Unit, private val stop: () -> Unit,
    private val failed: () -> Unit,
    private val earn: (String, Exercise?) -> Unit,
    private val sessionAction: (SessionAccess, SessionAction) -> Unit) {
    private val manager = requireNotNull(service.getSystemService(WindowManager::class.java))
    private var showing: Triple<String, BlockReason, SessionAccess?>? = null
    private val windows = OverlayState<View>({ view ->
        val params = WindowManager.LayoutParams(WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT).apply {
            gravity = Gravity.CENTER; setTitle("ScrollFit budget")
        }
        manager.addView(view, params)
    }, { manager.removeViewImmediate(it) }, failed)
    val visible get() = windows.current != null

    fun isCurrent(access: SessionAccess) = visible && showing?.second == BlockReason.SESSION_BOUNDARY && showing?.third == access

    fun show(packageName: String, reason: BlockReason, access: SessionAccess?): Boolean {
        if (reason == BlockReason.SESSION_BOUNDARY) require(access != null)
        if (showing == Triple(packageName, reason, access) && visible) return true
        val label = runCatching {
            @Suppress("DEPRECATION")
            service.packageManager.getApplicationLabel(service.packageManager.getApplicationInfo(packageName, 0)).toString()
        }.getOrDefault(packageName)
        val content = LinearLayout(service).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_VERTICAL
            layoutDirection = service.resources.configuration.layoutDirection; setPadding(dp(24), dp(24), dp(24), dp(24))
            addView(text(service.getString(if (reason == BlockReason.SESSION_BOUNDARY)
                R.string.session_title else R.string.monitor_blocked), 26f))
            addView(text(label, 20f))
            addView(text(service.getString(when (reason) {
                BlockReason.COMBINED_CEILING -> R.string.monitor_combined_exhausted
                BlockReason.APP_BUDGET -> R.string.product_app_exhausted
                BlockReason.SESSION_BOUNDARY -> R.string.session_explanation
            }), 18f))
            addView(button(R.string.monitor_home, home))
            addView(button(R.string.monitor_panel, panel))
            addView(button(R.string.monitor_disable, stop))
        }
        val scroll = ScrollView(service).apply {
            setBackgroundColor(Color.rgb(25, 32, 45)); layoutDirection = service.resources.configuration.layoutDirection; addView(content)
        }
        val attached = windows.replace(scroll)
        if (attached) {
            showing = Triple(packageName, reason, access)
            if (reason == BlockReason.SESSION_BOUNDARY) {
                val displayed = requireNotNull(access)
                content.addView(button(R.string.session_continue) {
                    if (windows.current === scroll) sessionAction(displayed, SessionAction.CONTINUE)
                }, 3)
                content.addView(button(R.string.session_finish) {
                    if (windows.current === scroll) sessionAction(displayed, SessionAction.FINISH)
                }, 4)
                return true
            }
            val app = service.application as ScrollFitApplication
            app.challengeScope.launch {
                try {
                    val engine = app.persistence.budgetEngine("blocker-offers")
                    val prefs = app.persistence.preferences.values.first()
                    val offers = Exercise.entries.sortedBy { it != prefs.lastExercise }.map {
                        it to engine.offer(packageName, it, ChallengeMode.JUST_IN_TIME)
                    }
                    // Ignore lookups completed after dismissal, replacement or target switching.
                    if (windows.current !== scroll) return@launch
                    offers.forEach { (exercise, result) ->
                        content.addView(text(service.getString(exercise.label()), 18f))
                        when (result) {
                            is OfferResult.Available -> {
                                content.addView(text(service.getString(R.string.challenge_terms,
                                    result.offer.repetitionsRequired, result.offer.rewardMs / 1000), 16f))
                                content.addView(text(service.getString(R.string.challenge_capacity, result.offer.awardCapacityMs / 1000), 16f))
                                if (result.offer.reduced) content.addView(text(service.getString(R.string.challenge_reduced), 16f))
                                content.addView(button(exercise.label()) { if (windows.current === scroll) earn(packageName, exercise) })
                            }
                            is OfferResult.Refused -> content.addView(text(service.getString(result.reason.message()), 16f))
                        }
                    }
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) {
                    if (windows.current === scroll) content.addView(text(service.getString(R.string.challenge_storage_error), 16f))
                }
            }
            content.addView(button(R.string.challenge_only) { if (windows.current === scroll) earn(packageName, null) })
        }
        return attached
    }
    fun dismiss(): Boolean {
        val removed = windows.dismiss()
        if (removed) showing = null
        return removed
    }
    private fun text(value: String, size: Float) = TextView(service).apply {
        text = value; textSize = size; setTextColor(Color.WHITE); setPadding(0, dp(8), 0, dp(8))
    }
    private fun button(resource: Int, action: () -> Unit) = Button(service).apply {
        text = service.getString(resource); minHeight = dp(48); setOnClickListener { action() }
    }
    private fun dp(value: Int) = (value * service.resources.displayMetrics.density).toInt()
}
