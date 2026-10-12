package com.example.scrollfit.monitoring

import android.accessibilityservice.AccessibilityService
import android.graphics.Color
import android.graphics.PixelFormat
import android.view.*
import android.widget.*
import com.example.scrollfit.R
import com.example.scrollfit.core.budget.BudgetBlock
import com.example.scrollfit.core.usage.OverlayState

/** Touch-blocking app-area overlay with system navigation left accessible. */
class BudgetOverlay(private val service: AccessibilityService,
    private val home: () -> Unit, private val panel: () -> Unit, private val stop: () -> Unit,
    private val failed: () -> Unit) {
    private val manager = requireNotNull(service.getSystemService(WindowManager::class.java))
    private var showing: Pair<String, BudgetBlock>? = null
    private val windows = OverlayState<View>({ view ->
        val params = WindowManager.LayoutParams(WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT).apply {
            gravity = Gravity.CENTER; setTitle("ScrollFit budget")
        }
        manager.addView(view, params)
    }, { manager.removeViewImmediate(it) }, failed)
    val visible get() = windows.current != null

    fun show(packageName: String, reason: BudgetBlock): Boolean {
        require(reason != BudgetBlock.TARGET_UNAVAILABLE)
        if (showing == (packageName to reason) && visible) return true
        val label = runCatching {
            @Suppress("DEPRECATION")
            service.packageManager.getApplicationLabel(service.packageManager.getApplicationInfo(packageName, 0)).toString()
        }.getOrDefault(packageName)
        val content = LinearLayout(service).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL; setPadding(dp(24), dp(24), dp(24), dp(24))
            addView(text(service.getString(R.string.monitor_blocked), 26f))
            addView(text(label, 20f))
            addView(text(service.getString(if (reason == BudgetBlock.COMBINED_CEILING)
                R.string.monitor_combined_exhausted else R.string.monitor_app_exhausted), 18f))
            addView(button(R.string.monitor_home, home))
            addView(button(R.string.monitor_panel, panel))
            addView(button(R.string.monitor_disable, stop))
        }
        val scroll = ScrollView(service).apply {
            setBackgroundColor(Color.rgb(25, 32, 45)); layoutDirection = View.LAYOUT_DIRECTION_RTL; addView(content)
        }
        val attached = windows.replace(scroll)
        if (attached) showing = packageName to reason
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
