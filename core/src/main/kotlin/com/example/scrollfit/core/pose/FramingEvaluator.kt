package com.example.scrollfit.core.pose

import com.example.scrollfit.core.exercise.Exercise
import com.example.scrollfit.core.exercise.ExerciseGuides

enum class FramingIssue {
    NO_RELIABLE_POSE,
    SHOW_REQUIRED_PARTS,
    MOVE_BACK,
    MOVE_CLOSER,
    CENTER_BODY,
    FRAMED,
}

/** A transient framing decision; landmarks are deliberately not retained. */
data class FramingObservation(val timestampMs: Long, val issue: FramingIssue)

/** Evaluates raw, upright, unmirrored poses before [PoseFilter] can discard evidence. */
object FramingEvaluator {
    private const val MIN_CONFIDENCE = 0.5f
    private const val SAFE_MIN = 0.05f
    private const val SAFE_MAX = 0.95f
    private const val CENTER_MIN = 0.35f
    private const val CENTER_MAX = 0.65f
    private const val PUSH_UP_MIN_WIDTH = 0.35f
    private const val STANDING_MIN_HEIGHT = 0.45f

    fun evaluate(exercise: Exercise, sample: PoseSample?, timestampMs: Long): FramingObservation {
        if (!isReliable(sample, timestampMs)) {
            return FramingObservation(timestampMs, FramingIssue.NO_RELIABLE_POSE)
        }
        sample ?: return FramingObservation(timestampMs, FramingIssue.NO_RELIABLE_POSE)

        val groups = ExerciseGuides.forExercise(exercise).requiredLandmarkGroups
        val completeGroups = groups.mapNotNull { group ->
            val joints = group.map(sample.landmarks::get)
            if (joints.all(::isConfident)) {
                // PG01 puts the preferred/left push-up side first, so maxByOrNull keeps
                // deterministic left-side tie resolution.
                val confidence = joints.minOf { minOf(it.visibility, it.presence) }
                confidence to joints
            } else null
        }
        val required = completeGroups.maxByOrNull { it.first }?.second
            ?: return FramingObservation(timestampMs, FramingIssue.SHOW_REQUIRED_PARTS)

        val minX = required.minOf { it.x }
        val maxX = required.maxOf { it.x }
        val minY = required.minOf { it.y }
        val maxY = required.maxOf { it.y }

        val issue = when {
            required.any { it.x !in 0f..1f || it.y !in 0f..1f } ||
                minX < SAFE_MIN || maxX > SAFE_MAX || minY < SAFE_MIN || maxY > SAFE_MAX ->
                FramingIssue.MOVE_BACK
            exercise == Exercise.PUSH_UP && maxX - minX < PUSH_UP_MIN_WIDTH ->
                FramingIssue.MOVE_CLOSER
            exercise != Exercise.PUSH_UP && maxY - minY < STANDING_MIN_HEIGHT ->
                FramingIssue.MOVE_CLOSER
            (minX + maxX) / 2f !in CENTER_MIN..CENTER_MAX -> FramingIssue.CENTER_BODY
            else -> FramingIssue.FRAMED
        }
        return FramingObservation(timestampMs, issue)
    }

    private fun isReliable(sample: PoseSample?, timestampMs: Long): Boolean {
        if (sample == null || timestampMs < 0 || sample.timestampMonotonicMs < 0 ||
            sample.timestampMonotonicMs != timestampMs || sample.landmarks.size != 33 ||
            !sample.imageAspectRatio.isFinite() || sample.imageAspectRatio <= 0f) return false
        return sample.landmarks.all { landmark ->
            landmark.x.isFinite() && landmark.y.isFinite() && landmark.z.isFinite() &&
                landmark.visibility in 0f..1f && landmark.presence in 0f..1f
        }
    }

    private fun isConfident(landmark: Landmark) =
        landmark.visibility >= MIN_CONFIDENCE && landmark.presence >= MIN_CONFIDENCE
}
