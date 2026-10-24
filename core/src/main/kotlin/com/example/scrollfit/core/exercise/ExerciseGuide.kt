package com.example.scrollfit.core.exercise

/** Camera viewpoint needed to make the exercise landmarks understandable. */
enum class GuideViewpoint {
    SIDE,
    FRONT_OR_SLIGHT_ANGLE,
    FRONT,
}

/** Static poses used by the local, non-scoring movement demonstration. */
enum class GuidePose {
    PUSH_UP_TOP,
    PUSH_UP_LOWERED,
    STANDING,
    SQUAT_LOWERED,
    JACK_CLOSED,
    JACK_OPEN,
    KNEE_LEFT_UP,
    KNEE_RIGHT_UP,
}

/**
 * Positioning information for one exercise. A landmark group is a complete
 * alternative set: every landmark in one group must be visible together.
 */
data class ExerciseGuide(
    val exercise: Exercise,
    val viewpoint: GuideViewpoint,
    val requiredLandmarkGroups: List<Set<Int>>,
    val demonstration: List<GuidePose>,
)

/** Canonical guide definitions shared by positioning and camera readiness. */
object ExerciseGuides {
    private val guides = mapOf(
        Exercise.PUSH_UP to ExerciseGuide(
            exercise = Exercise.PUSH_UP,
            viewpoint = GuideViewpoint.SIDE,
            requiredLandmarkGroups = listOf(
                setOf(11, 13, 15, 23),
                setOf(12, 14, 16, 24),
            ),
            demonstration = listOf(
                GuidePose.PUSH_UP_TOP,
                GuidePose.PUSH_UP_LOWERED,
                GuidePose.PUSH_UP_TOP,
            ),
        ),
        Exercise.SQUAT to ExerciseGuide(
            exercise = Exercise.SQUAT,
            viewpoint = GuideViewpoint.FRONT_OR_SLIGHT_ANGLE,
            requiredLandmarkGroups = listOf(setOf(11, 12, 23, 24, 25, 26, 27, 28)),
            demonstration = listOf(
                GuidePose.STANDING,
                GuidePose.SQUAT_LOWERED,
                GuidePose.STANDING,
            ),
        ),
        Exercise.JUMPING_JACK to ExerciseGuide(
            exercise = Exercise.JUMPING_JACK,
            viewpoint = GuideViewpoint.FRONT,
            requiredLandmarkGroups = listOf(setOf(11, 12, 15, 16, 23, 24, 27, 28)),
            demonstration = listOf(
                GuidePose.JACK_CLOSED,
                GuidePose.JACK_OPEN,
                GuidePose.JACK_CLOSED,
            ),
        ),
        Exercise.ALTERNATING_KNEE_RAISE to ExerciseGuide(
            exercise = Exercise.ALTERNATING_KNEE_RAISE,
            viewpoint = GuideViewpoint.FRONT,
            requiredLandmarkGroups = listOf(setOf(11, 12, 23, 24, 25, 26, 27, 28)),
            demonstration = listOf(
                GuidePose.STANDING,
                GuidePose.KNEE_LEFT_UP,
                GuidePose.STANDING,
                GuidePose.KNEE_RIGHT_UP,
                GuidePose.STANDING,
            ),
        ),
    )

    /** Exhaustive `when` makes a new exercise require an explicit guide. */
    fun forExercise(exercise: Exercise): ExerciseGuide = when (exercise) {
        Exercise.PUSH_UP -> guides.getValue(exercise)
        Exercise.SQUAT -> guides.getValue(exercise)
        Exercise.JUMPING_JACK -> guides.getValue(exercise)
        Exercise.ALTERNATING_KNEE_RAISE -> guides.getValue(exercise)
    }
}
