package com.example.scrollfit.ui

import android.os.Handler
import android.os.Looper
import android.os.Debug
import android.os.SystemClock
import android.util.Log
import android.view.FrameMetrics
import android.view.Window
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.scrollfit.core.data.*
import com.example.scrollfit.core.exercise.Exercise
import com.example.scrollfit.core.outcomes.*
import com.example.scrollfit.monitoring.TargetApp
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.ZoneId
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.ceil

/** I12 UI-side timing and real frame-duration capture for the full-history projection/list. */
@RunWith(AndroidJUnit4::class)
class HistoryPerformanceTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun sevenDayBaselineCapturesHistoryProjectionAndListMetrics() = measureHistory(7)

    @Test fun oneYearHistoryCapturesHistoryProjectionAndListMetrics() = measureHistory(365)

    @Test fun threeYearHistoryCapturesHistoryProjectionAndListMetrics() = measureHistory(1_095)

    private fun measureHistory(dayCount: Int) {
        val snapshot = syntheticSnapshot(dayCount, 20)
        val frameDurationsNs = Collections.synchronizedList(mutableListOf<Long>())
        val framesAvailable = CountDownLatch(1)
        val droppedFrames = AtomicInteger()
        val listener = Window.OnFrameMetricsAvailableListener { _, metrics, dropped ->
            val duration = metrics.getMetric(FrameMetrics.TOTAL_DURATION)
            if (duration > 0) {
                frameDurationsNs += duration
                framesAvailable.countDown()
            }
            droppedFrames.addAndGet(dropped)
        }
        val window = compose.activity.window
        val memorySampler = PeakPssSampler().also { it.start() }
        window.addOnFrameMetricsAvailableListener(listener, Handler(Looper.getMainLooper()))
        try {
            val projectionSamples = List(10) {
                val start = SystemClock.elapsedRealtimeNanos()
                prepareHistory(snapshot)
                elapsedMs(start)
            }
            val presentation = prepareHistory(snapshot)
            val firstRenderStart = SystemClock.elapsedRealtimeNanos()
            compose.setContent { ScrollFitTheme { HistoryContent(presentation, loading = false, onRefresh = {}) } }
            compose.waitForIdle()
            val firstRenderMs = elapsedMs(firstRenderStart)
            val initiallyComposed = compose.onAllNodesWithTag("history_day").fetchSemanticsNodes().size
            val oldestDay = (presentation.summary.olderDays.lastOrNull()
                ?: presentation.summary.recentDays.last()).day.toString()
            val scrollStart = SystemClock.elapsedRealtimeNanos()
            compose.onNodeWithTag("history_list").performScrollToNode(hasTestTag("history_card_$oldestDay"))
            compose.waitForIdle()
            val oldestScrollMs = elapsedMs(scrollStart)
            compose.onNodeWithTag("history_card_$oldestDay").assertExists()
            assertTrue("Android did not deliver frame metrics", framesAvailable.await(2, TimeUnit.SECONDS))

            assertTrue("initial composition should remain lazy: $initiallyComposed day cards",
                initiallyComposed < 30)
            val frames = frameDurationsNs.toList().filter { it > 0 }.map { it / 1_000_000.0 }
            Log.i("I12_HISTORY_UI", "days=$dayCount packages=20 repetitions=10 " +
                "projectionP95Ms=${p95(projectionSamples)} firstRenderWaitMs=$firstRenderMs " +
                "oldestScrollMs=$oldestScrollMs initiallyComposedDayCards=$initiallyComposed " +
                "frameCount=${frames.size} frameP95Ms=${frames.takeIf { it.isNotEmpty() }?.let(::p95)} " +
                "frameMaxMs=${frames.maxOrNull()} droppedFrameReports=${droppedFrames.get()} " +
                "peakProcessPssKb=${memorySampler.peakPssKb.get()}")
        } finally {
            window.removeOnFrameMetricsAvailableListener(listener)
            memorySampler.close()
        }
    }

    private fun syntheticSnapshot(dayCount: Int, packageCount: Int): ProductSnapshot {
        val today = LocalDate.parse("2026-10-26")
        val packages = (0 until packageCount).map { "fixture.pkg.${it.toString().padStart(2, '0')}" }
        val firstDay = today.minusDays((dayCount - 1).toLong())
        val usage = mutableListOf<DailyUsage>()
        val combined = mutableListOf<CombinedUsage>()
        val coverage = mutableListOf<CoverageInterval>()
        repeat(dayCount) { offset ->
            val day = firstDay.plusDays(offset.toLong())
            val hidden = offset == 0
            val dayUsage = packages.mapIndexed { index, packageName ->
                DailyUsage(packageName, day.toString(), baseConsumedMs = 60_000L + index * 100L)
            }
            if (!hidden) {
                usage += dayUsage
                combined += CombinedUsage(day.toString(), dayUsage.sumOf { it.baseConsumedMs })
            }
            val mode = offset % 4
            if (!hidden && mode != 3) {
                val zone = ZoneId.of(if (mode == 2) "Asia/Tehran" else "UTC")
                val start = day.atStartOfDay(zone).toInstant().toEpochMilli()
                val end = day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
                val length = when (mode) {
                    0 -> end - start
                    1 -> (end - start) / 2
                    else -> 3_600_000L
                }
                coverage += CoverageInterval("ui-$offset", "ui-session", "ui-${zone.id}", day.toString(),
                    zone.id, 0, length, start, start + length,
                    if (mode == 2) packages.take(5).toSet() else packages.toSet())
            }
        }
        val history = VisibleHistory(usage, combined, emptyList(), coverage,
            hiddenUsageDays = setOf(firstDay.toString()), coverageHiddenThroughDay = firstDay.toString())
        val configs = packages.mapIndexed { index, packageName ->
            TargetConfig(packageName, selected = index != packageCount - 1)
        }
        val apps = packages.mapIndexed { index, packageName -> TargetApp(packageName, "Synthetic app ${index + 1}") }
        return ProductSnapshot(today.toString(), "UTC", AppPreferences(), configs, apps, emptyMap(),
            CombinedUsage(today.toString()), emptyMap(), emptyMap(), history)
    }

    private fun elapsedMs(startNanos: Long) = (SystemClock.elapsedRealtimeNanos() - startNanos) / 1_000_000.0
    private fun p95(values: List<Double>): Double = values.sorted()[ceil(values.size * 0.95).toInt() - 1]

    private class PeakPssSampler : AutoCloseable {
        val peakPssKb = AtomicInteger()
        private val active = AtomicBoolean(false)
        private lateinit var worker: Thread

        fun start() {
            active.set(true)
            worker = Thread {
                val memory = Debug.MemoryInfo()
                while (active.get()) {
                    Debug.getMemoryInfo(memory)
                    peakPssKb.updateAndGet { maxOf(it, memory.totalPss) }
                    try { Thread.sleep(10) } catch (_: InterruptedException) { break }
                }
            }.apply { name = "i12-ui-pss-sampler"; isDaemon = true; start() }
        }

        override fun close() {
            active.set(false)
            if (::worker.isInitialized) worker.join(1_000)
        }
    }
}
