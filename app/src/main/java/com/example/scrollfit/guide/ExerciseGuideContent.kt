package com.example.scrollfit.guide

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.scrollfit.R
import com.example.scrollfit.core.exercise.Exercise
import com.example.scrollfit.core.exercise.ExerciseGuides
import com.example.scrollfit.core.exercise.GuidePose

/** Learn screen content with persistent actions and a separately scrollable guide. */
@Composable
fun ExerciseGuideContent(
    exercise: Exercise,
    onContinue: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        val resources = guideResources(exercise)
        val shortLandscape = maxWidth >= 560.dp && maxHeight < 420.dp
        Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            GuideScreenHeader(exercise)
            if (shortLandscape) {
                Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Column(Modifier.weight(0.9f).fillMaxHeight().verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(resources.placement), style = MaterialTheme.typography.bodyMedium)
                        GuidePhonePlacementIllustration(resources.placementArtwork,
                            stringResource(resources.placement), Modifier.fillMaxWidth(), decorative = true)
                    }
                    Column(Modifier.weight(1.1f).fillMaxHeight().verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        GuideMovementSequence(exercise)
                        GuideCopy(exercise)
                    }
                }
            } else {
                Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(resources.placement), style = MaterialTheme.typography.bodyMedium)
                    GuidePhonePlacementIllustration(resources.placementArtwork,
                        stringResource(resources.placement), Modifier.fillMaxWidth(), decorative = true)
                    GuideMovementSequence(exercise)
                    GuideCopy(exercise)
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onClose, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.challenge_close))
                }
                Button(onClick = onContinue, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.position_continue))
                }
            }
        }
    }
}

/** Full guide details reused inside the active workout's in-app help sheet. */
@Composable
fun ExerciseGuideDetails(exercise: Exercise, modifier: Modifier = Modifier) {
    val resources = guideResources(exercise)
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        GuideScreenHeader(exercise)
        Text(stringResource(resources.placement), style = MaterialTheme.typography.bodyMedium)
        GuidePhonePlacementIllustration(resources.placementArtwork,
            stringResource(resources.placement), Modifier.fillMaxWidth(), decorative = true)
        GuideMovementSequence(exercise)
        GuideCopy(exercise)
    }
}

@Composable
internal fun GuideScreenHeader(exercise: Exercise) {
    val resources = guideResources(exercise)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(R.string.position_learn_title), style = MaterialTheme.typography.headlineSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(resources.title), style = MaterialTheme.typography.titleMedium)
            Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.secondaryContainer) {
                Text(stringResource(resources.viewpoint), modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun GuideMovementSequence(exercise: Exercise) {
    val sequence = ExerciseGuides.forExercise(exercise).demonstration
    Text(stringResource(R.string.position_movement_steps), style = MaterialTheme.typography.titleSmall)
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        sequence.forEachIndexed { index, pose ->
            GuidePoseCard(pose, index + 1)
        }
    }
}

@Composable
private fun GuidePoseCard(pose: GuidePose, step: Int) {
    val label = stringResource(guidePoseLabel(pose))
    Card(Modifier.widthIn(min = 136.dp, max = 176.dp)) {
        Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            GuideMovementIllustration(pose, label, Modifier.fillMaxWidth(), decorative = true)
            Text(stringResource(R.string.position_step_number, step), style = MaterialTheme.typography.labelSmall)
            Text(label, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun GuideCopy(exercise: Exercise) {
    val resources = guideResources(exercise)
    GuideCopyLine(R.string.position_starting_position, resources.startingCue)
    GuideCopyLine(R.string.position_one_repetition, resources.oneRepetition)
    GuideCopyLine(R.string.position_body_coverage, resources.coverage)
    Text(stringResource(resources.formDisclaimer), style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun GuideCopyLine(@androidx.annotation.StringRes title: Int, @androidx.annotation.StringRes copy: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(stringResource(title), style = MaterialTheme.typography.titleSmall)
        Text(stringResource(copy), style = MaterialTheme.typography.bodyMedium)
    }
}
