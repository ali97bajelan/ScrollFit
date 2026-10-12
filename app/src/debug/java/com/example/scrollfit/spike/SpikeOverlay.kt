package com.example.scrollfit.spike

import android.accessibilityservice.AccessibilityService
import android.graphics.Color
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.example.scrollfit.R
import com.example.scrollfit.core.usage.BlockReason
import com.example.scrollfit.core.usage.BlockingSurface

/** Uses a connected service's overlay window; never launches a background Activity. */
class SpikeOverlay(
    private val service: AccessibilityService,
    private val home: () -> Unit,
    private val systemPanel: () -> Unit,
    private val stop: () -> Unit,
    private val onFailure: () -> Unit,
) : BlockingSurface {
    private val manager = requireNotNull(service.getSystemService(WindowManager::class.java))
    private var view: View? = null
    private var countdownText: TextView? = null
    var blocked = false
        private set
    val visible: Boolean get() = view != null

    fun countdown(remainingMillis: Long) {
        if (blocked) dismiss()
        if (view == null) {
            val label = text(16f).apply {
                setPadding(dp(16), dp(8), dp(16), dp(8))
                setBackgroundColor(Color.rgb(25, 32, 45))
            }
            if (attach(label, blocking = false)) countdownText = label
        }
        countdownText?.text = service.getString(R.string.spike_countdown, (remainingMillis + 999) / 1_000)
    }

    override fun show(packageName: String, reason: BlockReason) {
        require(reason == BlockReason.APP_BUDGET)
        if (blocked) return
        dismiss()
        val label = runCatching {
            service.packageManager.getApplicationLabel(service.packageManager.getApplicationInfo(packageName, 0)).toString()
        }.getOrDefault(packageName)
        val content = LinearLayout(service).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(dp(24), dp(24), dp(24), dp(24))
            addView(text(26f).apply { text = service.getString(R.string.spike_blocked_title) })
            addView(text(20f).apply { text = label })
            addView(text(18f).apply { text = service.getString(R.string.spike_blocked_body) })
            addView(button(R.string.spike_home, home))
            addView(button(R.string.spike_system_panel, systemPanel))
            addView(button(R.string.spike_stop, stop))
        }
        val scroll = ScrollView(service).apply {
            setBackgroundColor(Color.rgb(25, 32, 45))
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            addView(content)
        }
        if (attach(scroll, blocking = true)) blocked = true
    }

    override fun dismiss() {
        view?.let { runCatching { manager.removeViewImmediate(it) } }
        view = null
        countdownText = null
        blocked = false
    }

    private fun attach(next: View, blocking: Boolean): Boolean {
        val parameters = WindowManager.LayoutParams(
            if (blocking) WindowManager.LayoutParams.MATCH_PARENT else WindowManager.LayoutParams.WRAP_CONTENT,
            if (blocking) WindowManager.LayoutParams.MATCH_PARENT else WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                (if (blocking) 0 else WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE),
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = if (blocking) Gravity.CENTER else Gravity.TOP or Gravity.CENTER_HORIZONTAL
            setTitle("ScrollFit T02")
        }
        return try {
            manager.addView(next, parameters)
            view = next
            true
        } catch (_: RuntimeException) {
            onFailure()
            false
        }
    }

    private fun text(size: Float): TextView = TextView(service).apply {
        textSize = size
        setTextColor(Color.WHITE)
        setPadding(0, dp(8), 0, dp(8))
        layoutDirection = View.LAYOUT_DIRECTION_RTL
    }

    private fun button(label: Int, action: () -> Unit): Button = Button(service).apply {
        text = service.getString(label)
        minHeight = dp(48)
        setOnClickListener { action() }
    }

    private fun dp(value: Int): Int = (value * service.resources.displayMetrics.density).toInt()
}
