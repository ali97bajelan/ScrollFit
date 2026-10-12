package com.example.scrollfit.platform.data

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.scrollfit.core.data.CameraChoice
import com.example.scrollfit.core.exercise.Exercise
import com.example.scrollfit.core.exercise.RewardRule
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class PreferencesTest {
    @Test fun preferencesSurviveReopenAndConcurrentEdits() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val file = File(context.cacheDir, "t03-${UUID.randomUUID()}.preferences_pb")
        var job = SupervisorJob()
        fun repository(): PreferencesRepository = PreferencesRepository(PreferenceDataStoreFactory.create(
            scope = CoroutineScope(job + Dispatchers.IO), produceFile = { file },
        ))
        try {
            var repo = repository()
            repo.update { it.copy(
                zeroBaseEnabled = true, speechEnabled = false, camera = CameraChoice.FRONT,
                lastExercise = Exercise.ALTERNATING_KNEE_RAISE, sessionBoundaryMs = 60_000,
                rewardRules = it.rewardRules + (Exercise.SQUAT to RewardRule(12, 90)),
            ) }
            coroutineScope {
                awaitAll(async { repo.update { it.copy(combinedCeilingMs = 123_000) } },
                    async { repo.update { it.copy(sessionBoundaryMs = 45_000) } })
            }
            val expected = repo.values.first()
            assertEquals(123_000L, expected.combinedCeilingMs)
            assertEquals(45_000L, expected.sessionBoundaryMs)
            assertTrue(expected.zeroBaseEnabled); assertFalse(expected.speechEnabled)
            assertEquals(CameraChoice.FRONT, expected.camera)
            assertEquals(Exercise.ALTERNATING_KNEE_RAISE, expected.lastExercise)
            assertEquals(RewardRule(12, 90), expected.rewardRules.getValue(Exercise.SQUAT))
            job.cancelAndJoin()
            job = SupervisorJob(); repo = repository()
            assertEquals(expected, repo.values.first())
            try { repo.update { it.copy(combinedCeilingMs = 0) }; fail("invalid edit") } catch (_: IllegalArgumentException) { }
            assertEquals(expected, repo.values.first())
        } finally {
            job.cancelAndJoin(); file.delete()
        }
    }
}
