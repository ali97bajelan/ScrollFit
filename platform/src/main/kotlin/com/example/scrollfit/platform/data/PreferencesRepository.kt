package com.example.scrollfit.platform.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.example.scrollfit.core.data.AppPreferences
import com.example.scrollfit.core.data.CameraChoice
import com.example.scrollfit.core.exercise.Exercise
import com.example.scrollfit.core.exercise.RewardRule
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.exerciseGatePreferences by preferencesDataStore(name = "exercise-gate-preferences")

/** One application-scoped DataStore. Read/storage errors propagate; never clear counters to recover. */
class PreferencesRepository(private val store: DataStore<Preferences>) {
    val values: Flow<AppPreferences> = store.data.map(::decode)

    /** Transform the latest stored value atomically to avoid lost concurrent preference edits. */
    suspend fun update(transform: (AppPreferences) -> AppPreferences) {
        store.edit { preferences ->
            val value = transform(decode(preferences))
            // Revalidate a caller-owned mutable reward map before writing anything.
            val validated = value.copy(rewardRules = value.rewardRules.toMap())
            preferences[onboarding] = validated.onboardingCompleted
            preferences[monitoringEnabled] = validated.monitoringEnabled
            preferences[ceiling] = validated.combinedCeilingMs
            preferences[zeroBase] = validated.zeroBaseEnabled
            preferences[speech] = validated.speechEnabled
            preferences[camera] = validated.camera.name
            preferences[lastExercise] = validated.lastExercise.name
            preferences[sessionBoundary] = validated.sessionBoundaryMs
            Exercise.entries.forEach { exercise ->
                preferences[repetitions(exercise)] = validated.rewardRules.getValue(exercise).repetitions
                preferences[rewardSeconds(exercise)] = validated.rewardRules.getValue(exercise).rewardSeconds
            }
        }
    }

    companion object {
        fun open(context: Context) = PreferencesRepository(context.applicationContext.exerciseGatePreferences)
        private val onboarding = booleanPreferencesKey("onboarding_completed")
        private val ceiling = longPreferencesKey("combined_ceiling_ms")
        private val monitoringEnabled = booleanPreferencesKey("monitoring_enabled")
        private val zeroBase = booleanPreferencesKey("zero_base_enabled")
        private val speech = booleanPreferencesKey("speech_enabled")
        private val camera = stringPreferencesKey("camera")
        private val lastExercise = stringPreferencesKey("last_exercise")
        private val sessionBoundary = longPreferencesKey("session_boundary_ms")
        private fun repetitions(exercise: Exercise) = intPreferencesKey("${exercise.name}_repetitions")
        private fun rewardSeconds(exercise: Exercise) = longPreferencesKey("${exercise.name}_reward_seconds")
        private fun decode(value: Preferences): AppPreferences {
            val defaults = AppPreferences()
            return AppPreferences(
                onboardingCompleted = value[onboarding] ?: defaults.onboardingCompleted,
                monitoringEnabled = value[monitoringEnabled] ?: defaults.monitoringEnabled,
                combinedCeilingMs = value[ceiling] ?: defaults.combinedCeilingMs,
                zeroBaseEnabled = value[zeroBase] ?: defaults.zeroBaseEnabled,
                speechEnabled = value[speech] ?: defaults.speechEnabled,
                camera = value[camera]?.let(CameraChoice::valueOf) ?: defaults.camera,
                lastExercise = value[lastExercise]?.let(Exercise::valueOf) ?: defaults.lastExercise,
                sessionBoundaryMs = value[sessionBoundary] ?: defaults.sessionBoundaryMs,
                rewardRules = Exercise.entries.associateWith { exercise ->
                    val rule = defaults.rewardRules.getValue(exercise)
                    RewardRule(value[repetitions(exercise)] ?: rule.repetitions, value[rewardSeconds(exercise)] ?: rule.rewardSeconds)
                },
            )
        }
    }
}
