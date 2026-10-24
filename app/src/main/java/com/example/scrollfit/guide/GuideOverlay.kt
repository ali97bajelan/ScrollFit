package com.example.scrollfit.guide

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import com.example.scrollfit.core.exercise.Exercise
import com.example.scrollfit.core.pose.PreviewProjection

/** An advisory target drawn in camera-image coordinates, outside FIT_CENTER letterbox bars. */
@Composable
fun GuideOverlay(
    exercise: Exercise,
    imageWidth: Int,
    imageHeight: Int,
    mirrored: Boolean,
    visible: Boolean,
    modifier: Modifier = Modifier,
) {
    if (!visible || imageWidth <= 0 || imageHeight <= 0) return
    val backing = MaterialTheme.colorScheme.surface
    val figure = MaterialTheme.colorScheme.primary
    Canvas(modifier) {
        if (size.width <= 0f || size.height <= 0f) return@Canvas
        val image = PreviewProjection.imageRect(imageWidth, imageHeight, size.width, size.height)
        fun point(x: Float, y: Float): Offset {
            val projected = PreviewProjection.point(x, y, imageWidth, imageHeight,
                size.width, size.height, mirrored)
            return Offset(projected.first, projected.second)
        }

        val points = when (exercise) {
            Exercise.PUSH_UP -> sideTarget(::point)
            Exercise.JUMPING_JACK -> jumpingJackTarget(::point)
            Exercise.ALTERNATING_KNEE_RAISE -> kneeRaiseTarget(::point)
            Exercise.SQUAT -> standingTarget(::point)
        }
        clipRect(image.left, image.top, image.left + image.width, image.top + image.height) {
            drawTarget(points, backing, figure)
        }
    }
}

private data class TargetPoints(
    val head: Offset,
    val shoulders: List<Offset>,
    val elbows: List<Offset>,
    val wrists: List<Offset>,
    val hips: List<Offset>,
    val knees: List<Offset>,
    val ankles: List<Offset>,
)

private fun sideTarget(point: (Float, Float) -> Offset) = TargetPoints(
    head = point(0.14f, 0.48f),
    shoulders = listOf(point(0.25f, 0.52f)),
    elbows = listOf(point(0.37f, 0.65f)),
    wrists = listOf(point(0.50f, 0.65f)),
    hips = listOf(point(0.60f, 0.55f)),
    knees = listOf(point(0.77f, 0.60f)),
    ankles = listOf(point(0.90f, 0.61f)),
)

private fun standingTarget(point: (Float, Float) -> Offset) = TargetPoints(
    head = point(0.50f, 0.13f),
    shoulders = listOf(point(0.39f, 0.29f), point(0.61f, 0.29f)),
    elbows = listOf(point(0.33f, 0.43f), point(0.67f, 0.43f)),
    wrists = listOf(point(0.31f, 0.57f), point(0.69f, 0.57f)),
    hips = listOf(point(0.50f, 0.53f)),
    knees = listOf(point(0.43f, 0.72f), point(0.57f, 0.72f)),
    ankles = listOf(point(0.42f, 0.91f), point(0.58f, 0.91f)),
)

private fun jumpingJackTarget(point: (Float, Float) -> Offset) = TargetPoints(
    head = point(0.50f, 0.14f),
    shoulders = listOf(point(0.39f, 0.30f), point(0.61f, 0.30f)),
    elbows = listOf(point(0.29f, 0.19f), point(0.71f, 0.19f)),
    wrists = listOf(point(0.18f, 0.06f), point(0.82f, 0.06f)),
    hips = listOf(point(0.50f, 0.54f)),
    knees = listOf(point(0.40f, 0.72f), point(0.60f, 0.72f)),
    ankles = listOf(point(0.28f, 0.91f), point(0.72f, 0.91f)),
)

private fun kneeRaiseTarget(point: (Float, Float) -> Offset) = TargetPoints(
    head = point(0.50f, 0.14f),
    shoulders = listOf(point(0.39f, 0.30f), point(0.61f, 0.30f)),
    elbows = listOf(point(0.33f, 0.43f), point(0.67f, 0.43f)),
    wrists = listOf(point(0.31f, 0.57f), point(0.69f, 0.57f)),
    hips = listOf(point(0.50f, 0.53f)),
    knees = listOf(point(0.41f, 0.51f), point(0.59f, 0.72f)),
    ankles = listOf(point(0.34f, 0.64f), point(0.60f, 0.91f)),
)

private fun DrawScope.drawTarget(points: TargetPoints, backing: Color, figure: Color) {
    val limbs = buildList {
        points.shoulders.forEachIndexed { index, shoulder ->
            add(points.head to shoulder)
            add(shoulder to points.hips.first())
            if (points.shoulders.size > 1) {
                add(shoulder to points.elbows[index])
                add(points.elbows[index] to points.wrists[index])
                add(points.hips.first() to points.knees[index])
                add(points.knees[index] to points.ankles[index])
            } else {
                add(shoulder to points.elbows.first())
                add(points.elbows.first() to points.wrists.first())
                add(points.hips.first() to points.knees.first())
                add(points.knees.first() to points.ankles.first())
            }
        }
    }
    val backingWidth = 8.dp.toPx()
    val lineWidth = 3.5.dp.toPx()
    limbs.forEach { (start, end) ->
        drawLine(backing, start, end, backingWidth, cap = StrokeCap.Round)
        drawLine(figure, start, end, lineWidth, cap = StrokeCap.Round)
    }
    val headRadius = 8.dp.toPx()
    drawCircle(backing, headRadius + 2.dp.toPx(), points.head)
    drawCircle(figure, headRadius, points.head)
    (points.shoulders + points.elbows + points.wrists + points.hips + points.knees + points.ankles)
        .forEach { joint ->
            drawCircle(backing, 5.dp.toPx(), joint)
            drawCircle(figure, 3.dp.toPx(), joint)
        }
}
