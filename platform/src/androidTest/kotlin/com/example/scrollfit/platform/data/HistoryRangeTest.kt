package com.example.scrollfit.platform.data

import android.content.Context
import android.os.Debug
import android.os.SystemClock
import android.util.Log
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.scrollfit.core.data.DurableRepository
import com.example.scrollfit.core.outcomes.OutcomeAggregator
import com.example.scrollfit.core.time.WallClock
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.ceil

/** I12-01 baseline harness. Metrics are evidence only; the proposed thresholds are not test assertions. */
@RunWith(AndroidJUnit4::class)
class HistoryRangeTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val clock = object : WallClock {
        override fun localDate() = LocalDate.parse("2026-10-26")
        override fun epochMillis() = LocalDate.parse("2026-10-26").atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli()
    }

    @Test fun largeHistoryBaselineRecordsLoadAggregationAndRoomQueryMetrics() = runBlocking {
        for (dayCount in listOf(7, 365, 1_095)) measureHistory(dayCount)
    }

    private suspend fun measureHistory(dayCount: Int) {
        val totalSelects = AtomicInteger()
        val targetSetSelects = AtomicInteger()
        val queryCallback = RoomDatabase.QueryCallback { sql, _ ->
            if (sql.trimStart().startsWith("SELECT", ignoreCase = true)) {
                totalSelects.incrementAndGet()
                if (sql.contains("monitoring_coverage_targets", ignoreCase = true)) targetSetSelects.incrementAndGet()
            }
        }
        val databaseName = "i12-${dayCount}-${UUID.randomUUID()}.db"
        val database = Room.databaseBuilder(context, ExerciseGateDatabase::class.java, databaseName)
            .addMigrations(ExerciseGateDatabase.MIGRATION_1_2, ExerciseGateDatabase.MIGRATION_2_3)
            .setQueryCallback(queryCallback, Executor { it.run() })
            .build()
        try {
            val expected = HistoryScaleSeed.seed(database.openHelper.writableDatabase, dayCount)
            val repo = DurableRepository(RoomStore(database), clock)
            repeat(2) { assertEquals(expected, repo.visibleHistory()) } // warm-up; excluded from samples

            val loadSamplesMs = mutableListOf<Double>()
            val aggregationSamplesMs = mutableListOf<Double>()
            val querySamples = mutableListOf<Int>()
            val targetQuerySamples = mutableListOf<Int>()
            val memorySampler = PeakPssSampler().also { it.start() }
            try {
                repeat(10) {
                    totalSelects.set(0)
                    targetSetSelects.set(0)
                    val loadStart = SystemClock.elapsedRealtimeNanos()
                    val actual = repo.visibleHistory()
                    loadSamplesMs += elapsedMs(loadStart)
                    querySamples += totalSelects.get()
                    targetQuerySamples += targetSetSelects.get()
                    assertEquals("visible history must equal the fixture contract", expected, actual)
                    val aggregationStart = SystemClock.elapsedRealtimeNanos()
                    val summary = OutcomeAggregator.summarize(actual, clock.localDate(), ZoneId.of("UTC"))
                    aggregationSamplesMs += elapsedMs(aggregationStart)
                    assertEquals(maxOf(7, dayCount - 1), summary.recentDays.size + summary.olderDays.size)
                }
            } finally {
                memorySampler.close()
            }

            val queryAverage = querySamples.average()
            val targetQueryAverage = targetQuerySamples.average()
            assertTrue("query callback should observe repository reads", queryAverage > 0)
            Log.i("I12_HISTORY", "days=$dayCount packages=20 repetitions=10 " +
                "loadP95Ms=${p95(loadSamplesMs)} aggregateP95Ms=${p95(aggregationSamplesMs)} " +
                "selectsPerLoadAvg=$queryAverage coverageTargetSelectsPerLoadAvg=$targetQueryAverage " +
                "peakProcessPssKb=${memorySampler.peakPssKb.get()} " +
                "visibleCoverageRows=${expected.coverage.size} visibleUsageRows=${expected.usage.size}")
        } finally {
            database.close()
            context.deleteDatabase(databaseName)
        }
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
            }.apply { name = "i12-pss-sampler"; isDaemon = true; start() }
        }

        override fun close() {
            active.set(false)
            if (::worker.isInitialized) worker.join(1_000)
        }
    }
}
