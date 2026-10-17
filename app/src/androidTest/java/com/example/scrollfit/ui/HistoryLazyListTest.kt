package com.example.scrollfit.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.scrollfit.core.data.*
import com.example.scrollfit.core.exercise.Exercise
import com.example.scrollfit.core.outcomes.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class HistoryLazyListTest {
    @get:Rule val compose = createComposeRule()

    @Test fun thousandsOfDaysComposeLazilyAndOldestDayRemainsReachable() {
        val presentation = historyWithDays(1_095)
        val oldest = presentation.summary.olderDays.last().day.toString()
        compose.setContent {
            ScrollFitTheme {
                Box(Modifier.fillMaxSize().requiredSize(360.dp, 640.dp).testTag("history_root")) {
                    HistoryContent(presentation, loading = false, onRefresh = {})
                }
            }
        }

        compose.onNodeWithTag("history_list").assertExists()
        compose.onAllNodesWithTag("history_day").fetchSemanticsNodes().let { initiallyComposed ->
            assertTrue("only visible day cards should be composed, got ${initiallyComposed.size}",
                initiallyComposed.size < 30)
        }
        compose.onNodeWithTag("history_card_$oldest").assertDoesNotExist()
        compose.onNodeWithTag("history_list").performScrollToNode(hasTestTag("history_card_$oldest"))
        compose.onNodeWithTag("history_card_$oldest").assertExists()
    }

    @Test fun refreshedSnapshotCannotReceiveTheBlockedOldProjection() {
        val oldDay = LocalDate.parse("2026-10-01")
        val newDay = LocalDate.parse("2026-10-17")
        val data = ProductData().apply {
            loading = false
            snapshot = productSnapshot(oldDay)
        }
        val oldPrepareStarted = CountDownLatch(1)
        val releaseOldPrepare = CountDownLatch(1)
        val oldPrepareFinished = CountDownLatch(1)
        compose.setContent {
            ScrollFitTheme {
                Box(Modifier.fillMaxSize()) {
                    HistoryHost(data, prepare = { source ->
                        if (source.day == oldDay.toString()) {
                            oldPrepareStarted.countDown()
                            try { releaseOldPrepare.await(5, TimeUnit.SECONDS) }
                            finally { oldPrepareFinished.countDown() }
                            presentation(listOf(oldDay))
                        } else {
                            presentation(listOf(newDay, newDay.minusDays(1)))
                        }
                    })
                }
            }
        }

        assertTrue("old projection did not start", oldPrepareStarted.await(5, TimeUnit.SECONDS))
        compose.runOnIdle {
            data.loading = true
            data.revision++
        }
        compose.runOnIdle {
            data.snapshot = productSnapshot(newDay)
            data.loading = false
        }
        compose.waitUntil(5_000) {
            compose.onAllNodesWithTag("history_list").fetchSemanticsNodes().any { node ->
                node.config[SemanticsProperties.CollectionInfo].rowCount == 11
            }
        }
        releaseOldPrepare.countDown()
        assertTrue("canceled old projection did not return", oldPrepareFinished.await(5, TimeUnit.SECONDS))
        compose.waitForIdle()
        val list = compose.onNodeWithTag("history_list").fetchSemanticsNode().config
        assertEquals(11, list[SemanticsProperties.CollectionInfo].rowCount)
    }

    private fun historyWithDays(dayCount: Int): HistoryPresentation {
        val today = LocalDate.parse("2026-10-17")
        fun day(offset: Long) = DayOutcome(today.minusDays(offset), null, emptyMap(), null,
            Exercise.entries.associateWith { ExerciseOutcome(0, 0, 0, 0) },
            DayCoverage(CoverageStatus.UNKNOWN, 0, 86_400_000, null, emptySet(), false),
            inProgress = offset == 0L, accountingConsistent = true)
        val recent = (0L..6L).map(::day)
        val older = (7L until dayCount.toLong()).map(::day)
        val activity = Exercise.entries.associateWith { ExerciseOutcome(0, 0, 0, 0) }
        val summary = OutcomeSummary(recent, older, null, null, activity,
            UsageComparison(null, null, null, ComparisonBlock.INSUFFICIENT_COVERAGE),
            UsageComparison(null, null, null, ComparisonBlock.INSUFFICIENT_COVERAGE))
        return HistoryPresentation(summary, emptySet(), emptyMap())
    }

    private fun productSnapshot(day: LocalDate) = ProductSnapshot(day.toString(), "UTC", AppPreferences(),
        emptyList(), emptyList(), emptyMap(), CombinedUsage(day.toString()), emptyMap(), emptyMap(),
        VisibleHistory(emptyList(), emptyList(), emptyList()))

    private fun presentation(days: List<LocalDate>): HistoryPresentation {
        fun outcome(day: LocalDate) = DayOutcome(day, null, emptyMap(), null,
            Exercise.entries.associateWith { ExerciseOutcome(0, 0, 0, 0) },
            DayCoverage(CoverageStatus.UNKNOWN, 0, 86_400_000, null, emptySet(), false),
            inProgress = false, accountingConsistent = true)
        return HistoryPresentation(OutcomeSummary(days.map(::outcome), emptyList(), null, null,
            Exercise.entries.associateWith { ExerciseOutcome(0, 0, 0, 0) },
            UsageComparison(null, null, null, ComparisonBlock.INSUFFICIENT_COVERAGE),
            UsageComparison(null, null, null, ComparisonBlock.INSUFFICIENT_COVERAGE)), emptySet(), emptyMap())
    }
}
