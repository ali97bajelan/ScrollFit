package com.example.scrollfit.monitoring

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.PixelFormat
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.example.scrollfit.R
import com.example.scrollfit.ScrollFitApplication
import com.example.scrollfit.core.budget.SessionAccess
import com.example.scrollfit.core.budget.SessionAction
import com.example.scrollfit.core.data.ChallengeMode
import com.example.scrollfit.core.exercise.Exercise
import com.example.scrollfit.core.usage.BlockReason
import com.example.scrollfit.core.usage.OverlayState
import com.example.scrollfit.core.budget.OfferResult
import com.example.scrollfit.challenge.label
import com.example.scrollfit.challenge.message
import com.example.scrollfit.ui.ProductTokens
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Touch-blocking app-area overlay with system navigation left accessible. */
class BudgetOverlay(
    private val service: AccessibilityService,
    private val home: () -> Unit,
    private val openScrollFit: () -> Unit,
    private val stop: () -> Unit,
    private val failed: () -> Unit,
    private val earn: (String, Exercise?) -> Unit,
    private val sessionAction: (SessionAccess, SessionAction) -> Unit,
) {
    private val manager = requireNotNull(service.getSystemService(WindowManager::class.java))
    private var showing: Triple<String, BlockReason, SessionAccess?>? = null
    private val windows = OverlayState<View>({ view ->
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.CENTER
            setTitle("ScrollFit budget")
        }
        manager.addView(view, params)
    }, { manager.removeViewImmediate(it) }, failed)

    val visible get() = windows.current != null

    fun isCurrent(access: SessionAccess) = visible &&
        showing?.second == BlockReason.SESSION_BOUNDARY && showing?.third == access

    fun show(packageName: String, reason: BlockReason, access: SessionAccess?): Boolean {
        if (reason == BlockReason.SESSION_BOUNDARY) require(access != null)
        if (showing == Triple(packageName, reason, access) && visible) return true
        val label = runCatching {
            @Suppress("DEPRECATION")
            service.packageManager.getApplicationLabel(
                service.packageManager.getApplicationInfo(packageName, 0),
            ).toString()
        }.getOrDefault(packageName)
        var currentView: View? = null
        fun isCurrentView() = currentView != null && windows.current === currentView
        val isSessionPrompt = reason == BlockReason.SESSION_BOUNDARY
        val actions = OverlayActions(
            onTakeBreak = {
                if (isCurrentView()) {
                    if (isSessionPrompt) sessionAction(requireNotNull(access), SessionAction.FINISH)
                    else home()
                }
            },
            onEarn = { exercise -> if (isCurrentView()) earn(packageName, exercise) },
            onContinue = if (isSessionPrompt) {
                { if (isCurrentView()) sessionAction(requireNotNull(access), SessionAction.CONTINUE) }
            } else null,
            onOpenPanel = { if (isCurrentView()) openScrollFit() },
            onStop = { if (isCurrentView()) stop() },
        )
        val style = OverlayStyle.resolve(service)
        val content = buildOverlayContent(
            context = service,
            label = label,
            reason = reason,
            offers = emptyList(),
            offerError = false,
            style = style,
            actions = actions,
        )
        currentView = content
        val attached = windows.replace(content)
        if (!attached) return false
        showing = Triple(packageName, reason, access)
        if (isSessionPrompt) return true

        val app = service.application as ScrollFitApplication
        app.challengeScope.launch {
            val offerState = try {
                val engine = app.persistence.budgetEngine("blocker-offers")
                val prefs = app.persistence.preferences.values.first()
                val offers = Exercise.entries.sortedBy { it != prefs.lastExercise }.map {
                    it to engine.offer(packageName, it, ChallengeMode.JUST_IN_TIME)
                }
                offers to false
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                emptyList<Pair<Exercise, OfferResult>>() to true
            }
            // Keep the attached window in place and ignore work completed after replacement.
            if (!isCurrentView()) return@launch
            updateOverlayContent(content, offerState.first, offerState.second)
        }
        return true
    }

    fun dismiss(): Boolean {
        val removed = windows.dismiss()
        if (removed) showing = null
        return removed
    }
}

/** Shared native hierarchy. Offers can be filled after their asynchronous lookup without replacing the window. */
internal fun buildOverlayContent(
    context: Context,
    label: String,
    reason: BlockReason,
    offers: List<Pair<Exercise, OfferResult>>,
    offerError: Boolean,
    style: OverlayStyle,
    actions: OverlayActions,
): View = OverlayContentView(context, label, reason, offers, offerError, style, actions)

private fun updateOverlayContent(
    view: View,
    offers: List<Pair<Exercise, OfferResult>>,
    offerError: Boolean,
) {
    (view as? OverlayContentView)?.showOffers(offers, offerError)
}

private class OverlayContentView(
    context: Context,
    label: String,
    reason: BlockReason,
    offers: List<Pair<Exercise, OfferResult>>,
    offerError: Boolean,
    private val style: OverlayStyle,
    private val actions: OverlayActions,
) : ScrollView(context) {
    private val content = LinearLayout(context)
    private val offerSection = LinearLayout(context)

    init {
        isFillViewport = true
        setBackgroundColor(style.surface)
        layoutDirection = resources.configuration.layoutDirection
        content.orientation = LinearLayout.VERTICAL
        content.gravity = Gravity.TOP
        content.layoutDirection = layoutDirection
        content.setPadding(dp(ProductTokens.space24), dp(ProductTokens.space24),
            dp(ProductTokens.space24), dp(ProductTokens.space24))
        addView(content, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
        ))

        addText(content, context.getString(if (reason == BlockReason.SESSION_BOUNDARY)
            R.string.session_title else R.string.monitor_blocked), style.titleTextSizeSp)
        addText(content, label, 20f)
        addText(content, context.getString(when (reason) {
            BlockReason.COMBINED_CEILING -> R.string.monitor_combined_exhausted
            BlockReason.APP_BUDGET -> R.string.product_app_exhausted
            BlockReason.SESSION_BOUNDARY -> R.string.session_explanation
        }), style.bodyTextSizeSp)

        addButton(content, context.getString(R.string.ux_take_break), primary = true,
            action = actions.onTakeBreak)

        offerSection.orientation = LinearLayout.VERTICAL
        offerSection.layoutDirection = layoutDirection
        content.addView(offerSection, verticalParams())
        showOffers(offers, offerError)

        actions.onContinue?.let {
            addButton(content, context.getString(R.string.session_continue), primary = false, action = it)
        }
        addButton(content, context.getString(R.string.ux_exercise_without_rewards),
            primary = false) { actions.onEarn(null) }

        val divider = View(context).apply { setBackgroundColor(style.outline) }
        content.addView(divider, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(1),
        ).apply { topMargin = dp(ProductTokens.space16); bottomMargin = dp(ProductTokens.space8) })
        addText(content, context.getString(R.string.ux_other_options), style.bodyTextSizeSp)
        addButton(content, context.getString(R.string.ux_open_scrollfit), primary = false,
            action = actions.onOpenPanel)
        addButton(content, context.getString(R.string.monitor_disable), primary = false,
            action = actions.onStop)
    }

    fun showOffers(offers: List<Pair<Exercise, OfferResult>>, offerError: Boolean) {
        offerSection.removeAllViews()
        if (offers.isEmpty() && !offerError) {
            offerSection.visibility = View.GONE
            return
        }
        offerSection.visibility = View.VISIBLE
        addText(offerSection, context.getString(R.string.ux_earn_time), style.bodyTextSizeSp)
        offers.forEach { (exercise, result) ->
            addText(offerSection, context.getString(exercise.label()), 18f)
            when (result) {
                is OfferResult.Available -> {
                    addText(offerSection, context.getString(R.string.challenge_terms,
                        result.offer.repetitionsRequired, result.offer.rewardMs / 1000), style.bodyTextSizeSp)
                    addText(offerSection, context.getString(R.string.challenge_capacity,
                        result.offer.awardCapacityMs / 1000), style.bodyTextSizeSp)
                    if (result.offer.reduced) {
                        addText(offerSection, context.getString(R.string.challenge_reduced), style.bodyTextSizeSp)
                    }
                    addButton(offerSection, context.getString(R.string.ux_earn_time), primary = true) {
                        actions.onEarn(exercise)
                    }
                }
                is OfferResult.Refused -> addText(offerSection,
                    context.getString(result.reason.message()), style.bodyTextSizeSp)
            }
        }
        if (offerError) addText(offerSection,
            context.getString(R.string.challenge_storage_error), style.bodyTextSizeSp, style.error)
    }

    private fun addButton(
        target: LinearLayout,
        label: String,
        primary: Boolean,
        action: () -> Unit,
    ) {
        val background = if (primary) style.primary else style.secondary
        val foreground = if (primary) style.onPrimary else style.onSecondary
        val button = Button(context).apply {
            text = label
            textSize = style.buttonTextSizeSp
            isAllCaps = false
            isSingleLine = false
            maxLines = 3
            gravity = Gravity.CENTER
            minHeight = dp(style.minimumInteractiveHeightDp)
            setPadding(dp(ProductTokens.space16), dp(ProductTokens.space8),
                dp(ProductTokens.space16), dp(ProductTokens.space8))
            backgroundTintList = ColorStateList.valueOf(background)
            setTextColor(foreground)
            setOnClickListener { action() }
        }
        target.addView(button, verticalParams())
    }

    private fun addText(
        target: LinearLayout,
        value: String,
        sizeSp: Float,
        color: Int = style.onSurface,
    ) {
        val text = TextView(context).apply {
            this.text = value
            setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
            setTextColor(color)
            setPadding(0, dp(ProductTokens.space8), 0, dp(ProductTokens.space8))
        }
        target.addView(text, verticalParams())
    }

    private fun verticalParams() = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT,
    ).apply { topMargin = dp(ProductTokens.space4) }

    private fun dp(value: androidx.compose.ui.unit.Dp): Int =
        (value.value * resources.displayMetrics.density).toInt()

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
