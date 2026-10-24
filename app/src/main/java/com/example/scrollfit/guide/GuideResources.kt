package com.example.scrollfit.guide

import androidx.annotation.StringRes
import com.example.scrollfit.R
import com.example.scrollfit.core.exercise.Exercise
import com.example.scrollfit.core.exercise.GuidePose

enum class GuidePlacementArtwork {
    SIDE_PROFILE,
    FRONT_VIEW,
}

data class ExerciseGuideResources(
    @StringRes val title: Int,
    @StringRes val viewpoint: Int,
    @StringRes val placement: Int,
    @StringRes val startingCue: Int,
    @StringRes val oneRepetition: Int,
    @StringRes val coverage: Int,
    @StringRes val recovery: Int,
    @StringRes val formDisclaimer: Int,
    val placementArtwork: GuidePlacementArtwork,
)

/** Localized copy and placement-art mapping kept outside the Android-free core. */
fun guideResources(exercise: Exercise): ExerciseGuideResources = when (exercise) {
    Exercise.PUSH_UP -> ExerciseGuideResources(
        title = R.string.guide_push_up_title,
        viewpoint = R.string.guide_view_side,
        placement = R.string.guide_push_up_placement,
        startingCue = R.string.guide_push_up_start,
        oneRepetition = R.string.guide_push_up_one_rep,
        coverage = R.string.guide_push_up_coverage,
        recovery = R.string.guide_push_up_recovery,
        formDisclaimer = R.string.guide_no_form_scoring,
        placementArtwork = GuidePlacementArtwork.SIDE_PROFILE,
    )
    Exercise.SQUAT -> ExerciseGuideResources(
        title = R.string.guide_squat_title,
        viewpoint = R.string.guide_view_front_or_angle,
        placement = R.string.guide_squat_placement,
        startingCue = R.string.guide_squat_start,
        oneRepetition = R.string.guide_squat_one_rep,
        coverage = R.string.guide_squat_coverage,
        recovery = R.string.guide_squat_recovery,
        formDisclaimer = R.string.guide_no_form_scoring,
        placementArtwork = GuidePlacementArtwork.FRONT_VIEW,
    )
    Exercise.JUMPING_JACK -> ExerciseGuideResources(
        title = R.string.guide_jack_title,
        viewpoint = R.string.guide_view_front,
        placement = R.string.guide_jack_placement,
        startingCue = R.string.guide_jack_start,
        oneRepetition = R.string.guide_jack_one_rep,
        coverage = R.string.guide_jack_coverage,
        recovery = R.string.guide_jack_recovery,
        formDisclaimer = R.string.guide_no_form_scoring,
        placementArtwork = GuidePlacementArtwork.FRONT_VIEW,
    )
    Exercise.ALTERNATING_KNEE_RAISE -> ExerciseGuideResources(
        title = R.string.guide_knee_raise_title,
        viewpoint = R.string.guide_view_front,
        placement = R.string.guide_knee_raise_placement,
        startingCue = R.string.guide_knee_raise_start,
        oneRepetition = R.string.guide_knee_raise_one_rep,
        coverage = R.string.guide_knee_raise_coverage,
        recovery = R.string.guide_knee_raise_recovery,
        formDisclaimer = R.string.guide_no_form_scoring,
        placementArtwork = GuidePlacementArtwork.FRONT_VIEW,
    )
}

@StringRes
fun guidePoseLabel(pose: GuidePose): Int = when (pose) {
    GuidePose.PUSH_UP_TOP -> R.string.guide_pose_push_up_top
    GuidePose.PUSH_UP_LOWERED -> R.string.guide_pose_push_up_lowered
    GuidePose.STANDING -> R.string.guide_pose_standing
    GuidePose.SQUAT_LOWERED -> R.string.guide_pose_squat_lowered
    GuidePose.JACK_CLOSED -> R.string.guide_pose_jack_closed
    GuidePose.JACK_OPEN -> R.string.guide_pose_jack_open
    GuidePose.KNEE_LEFT_UP -> R.string.guide_pose_knee_left_up
    GuidePose.KNEE_RIGHT_UP -> R.string.guide_pose_knee_right_up
}
