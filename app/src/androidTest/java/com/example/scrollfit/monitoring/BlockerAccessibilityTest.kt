package com.example.scrollfit.monitoring

import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.scrollfit.R
import com.example.scrollfit.core.usage.BlockReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BlockerAccessibilityTest {
    @Test fun blockerButtonsExposeLabelsAndAtLeast48DpTargetsInReadingOrder() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val expected = listOf(
            context.getString(R.string.ux_take_break),
            context.getString(R.string.ux_exercise_without_rewards),
            context.getString(R.string.ux_open_scrollfit),
            context.getString(R.string.monitor_disable),
        )
        lateinit var labels: List<String>
        lateinit var minHeights: List<Int>
        lateinit var accessibleLabels: List<String>
        lateinit var clickable: List<Boolean>
        lateinit var focusable: List<Boolean>
        instrumentation.runOnMainSync {
            val content = buildOverlayContent(
                context = context,
                label = "Example app",
                reason = BlockReason.APP_BUDGET,
                offers = emptyList(),
                offerError = false,
                style = OverlayStyle.resolve(context),
                actions = OverlayActions({}, {}, null, {}, {}),
            )
            val root = FrameLayout(context).apply { addView(content) }
            val width = (360 * context.resources.displayMetrics.density).toInt()
            root.measure(
                View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
            )
            root.layout(0, 0, root.measuredWidth, root.measuredHeight)
            val buttons = descendants(root).filterIsInstance<Button>()
            labels = buttons.map { it.text.toString() }
            minHeights = buttons.map { it.minimumHeight }
            accessibleLabels = buttons.map { it.createAccessibilityNodeInfo().text.toString() }
            clickable = buttons.map { it.isClickable }
            focusable = buttons.map { it.isFocusable }
        }

        assertEquals(expected, labels)
        assertEquals(expected, accessibleLabels)
        assertTrue(clickable.all { it })
        assertTrue(focusable.all { it })
        assertTrue(minHeights.all { it >= (48 * context.resources.displayMetrics.density).toInt() })
    }

    private fun descendants(view: View): List<View> = buildList {
        add(view)
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) addAll(descendants(view.getChildAt(index)))
        }
    }
}
