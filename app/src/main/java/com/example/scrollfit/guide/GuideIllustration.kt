package com.example.scrollfit.guide

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import com.example.scrollfit.core.exercise.GuidePose

/** A bundled vector-style still; it remains visible when motion is disabled. */
@Composable
fun GuideMovementIllustration(
    pose: GuidePose,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    val background = MaterialTheme.colorScheme.surfaceContainerLow
    val figure = MaterialTheme.colorScheme.primary
    val outline = MaterialTheme.colorScheme.outline
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1.65f)
            .clip(RoundedCornerShape(16.dp))
            .semantics { this.contentDescription = contentDescription },
    ) {
        drawRect(background)
        when (pose) {
            GuidePose.PUSH_UP_TOP -> drawPushUp(figure, lowered = false)
            GuidePose.PUSH_UP_LOWERED -> drawPushUp(figure, lowered = true)
            GuidePose.STANDING -> drawStandingFigure(figure)
            GuidePose.SQUAT_LOWERED -> drawSquat(figure)
            GuidePose.JACK_CLOSED -> drawJumpingJack(figure, open = false)
            GuidePose.JACK_OPEN -> drawJumpingJack(figure, open = true)
            GuidePose.KNEE_LEFT_UP -> drawKneeRaise(figure, left = true)
            GuidePose.KNEE_RIGHT_UP -> drawKneeRaise(figure, left = false)
        }
        drawLine(
            color = outline.copy(alpha = 0.7f),
            start = Offset(size.width * 0.08f, size.height * 0.94f),
            end = Offset(size.width * 0.92f, size.height * 0.94f),
            strokeWidth = size.minDimension * 0.012f,
        )
    }
}

/** A local schematic showing the phone's viewpoint and the exercise area. */
@Composable
fun GuidePhonePlacementIllustration(
    artwork: GuidePlacementArtwork,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    val background = MaterialTheme.colorScheme.surfaceContainerLow
    val figure = MaterialTheme.colorScheme.primary
    val coneColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.18f)
    val outline = MaterialTheme.colorScheme.outline
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1.7f)
            .clip(RoundedCornerShape(16.dp))
            .semantics { this.contentDescription = contentDescription },
    ) {
        drawRect(background)
        if (artwork == GuidePlacementArtwork.SIDE_PROFILE) {
            drawSidePlacement(figure, outline, coneColor)
        } else {
            drawFrontPlacement(figure, outline, coneColor)
        }
        drawLine(
            color = outline.copy(alpha = 0.7f),
            start = Offset(size.width * 0.08f, size.height * 0.98f),
            end = Offset(size.width * 0.92f, size.height * 0.98f),
            strokeWidth = size.minDimension * 0.012f,
        )
    }
}

private fun DrawScope.drawPushUp(color: Color, lowered: Boolean) {
    val y = if (lowered) 0.61f else 0.48f
    val head = p(0.17f, y - 0.04f)
    val shoulder = p(0.29f, y)
    val elbow = p(if (lowered) 0.40f else 0.40f, y + if (lowered) 0.10f else 0.06f)
    val wrist = p(0.53f, y + if (lowered) 0.08f else 0.10f)
    val hip = p(0.64f, y + if (lowered) 0.04f else 0.01f)
    val knee = p(0.79f, y + if (lowered) 0.05f else 0.03f)
    val ankle = p(0.91f, y + if (lowered) 0.05f else 0.03f)
    line(color, shoulder, elbow, wrist)
    line(color, shoulder, hip, knee, ankle)
    circle(color, head, 0.045f)
}

private fun DrawScope.drawStandingFigure(color: Color) {
    val head = p(0.5f, 0.15f)
    val shoulderL = p(0.39f, 0.29f)
    val shoulderR = p(0.61f, 0.29f)
    val elbowL = p(0.34f, 0.43f)
    val elbowR = p(0.66f, 0.43f)
    val handL = p(0.32f, 0.57f)
    val handR = p(0.68f, 0.57f)
    val hip = p(0.5f, 0.53f)
    val kneeL = p(0.43f, 0.72f)
    val kneeR = p(0.57f, 0.72f)
    val ankleL = p(0.43f, 0.91f)
    val ankleR = p(0.57f, 0.91f)
    line(color, shoulderL, hip, shoulderR)
    line(color, shoulderL, elbowL, handL)
    line(color, shoulderR, elbowR, handR)
    line(color, hip, kneeL, ankleL)
    line(color, hip, kneeR, ankleR)
    circle(color, head, 0.07f)
}

private fun DrawScope.drawSquat(color: Color) {
    val head = p(0.5f, 0.17f)
    val shoulderL = p(0.39f, 0.31f)
    val shoulderR = p(0.61f, 0.31f)
    val hip = p(0.5f, 0.55f)
    val kneeL = p(0.35f, 0.70f)
    val kneeR = p(0.65f, 0.70f)
    val ankleL = p(0.31f, 0.91f)
    val ankleR = p(0.69f, 0.91f)
    line(color, shoulderL, hip, shoulderR)
    line(color, shoulderL, p(0.34f, 0.43f), p(0.43f, 0.48f))
    line(color, shoulderR, p(0.66f, 0.43f), p(0.57f, 0.48f))
    line(color, hip, kneeL, ankleL)
    line(color, hip, kneeR, ankleR)
    circle(color, head, 0.07f)
}

private fun DrawScope.drawJumpingJack(color: Color, open: Boolean) {
    val head = p(0.5f, 0.16f)
    val shoulderL = p(0.39f, 0.30f)
    val shoulderR = p(0.61f, 0.30f)
    val hip = p(0.5f, 0.54f)
    if (open) {
        line(color, shoulderL, p(0.31f, 0.19f), p(0.24f, 0.07f))
        line(color, shoulderR, p(0.69f, 0.19f), p(0.76f, 0.07f))
        line(color, hip, p(0.38f, 0.73f), p(0.27f, 0.92f))
        line(color, hip, p(0.62f, 0.73f), p(0.73f, 0.92f))
    } else {
        line(color, shoulderL, p(0.35f, 0.43f), p(0.34f, 0.57f))
        line(color, shoulderR, p(0.65f, 0.43f), p(0.66f, 0.57f))
        line(color, hip, p(0.46f, 0.73f), p(0.46f, 0.92f))
        line(color, hip, p(0.54f, 0.73f), p(0.54f, 0.92f))
    }
    line(color, shoulderL, hip, shoulderR)
    circle(color, head, 0.07f)
}

private fun DrawScope.drawKneeRaise(color: Color, left: Boolean) {
    val head = p(0.5f, 0.15f)
    val shoulderL = p(0.39f, 0.29f)
    val shoulderR = p(0.61f, 0.29f)
    val hip = p(0.5f, 0.53f)
    line(color, shoulderL, hip, shoulderR)
    line(color, shoulderL, p(0.34f, 0.43f), p(0.32f, 0.57f))
    line(color, shoulderR, p(0.66f, 0.43f), p(0.68f, 0.57f))
    if (left) {
        line(color, hip, p(0.61f, 0.63f), p(0.66f, 0.71f), p(0.60f, 0.72f))
        line(color, hip, p(0.42f, 0.73f), p(0.42f, 0.91f))
    } else {
        line(color, hip, p(0.39f, 0.63f), p(0.34f, 0.71f), p(0.40f, 0.72f))
        line(color, hip, p(0.58f, 0.73f), p(0.58f, 0.91f))
    }
    circle(color, head, 0.07f)
}

private fun DrawScope.drawSidePlacement(color: Color, outline: Color, cone: Color) {
    val camera = p(0.22f, 0.91f)
    val field = Path().apply {
        moveTo(camera.x, camera.y)
        lineTo(p(0.49f, 0.26f).x, p(0.49f, 0.26f).y)
        lineTo(p(0.94f, 0.75f).x, p(0.94f, 0.75f).y)
        close()
    }
    drawPath(field, cone)
    drawLine(outline, camera, p(0.94f, 0.75f), size.minDimension * 0.01f)
    drawLine(outline, camera, p(0.49f, 0.26f), size.minDimension * 0.01f)
    drawPhone(outline, camera)
    drawSidePerson(color)
}

private fun DrawScope.drawFrontPlacement(color: Color, outline: Color, cone: Color) {
    val camera = p(0.5f, 0.94f)
    val field = Path().apply {
        moveTo(camera.x, camera.y)
        lineTo(p(0.05f, 0.02f).x, p(0.05f, 0.02f).y)
        lineTo(p(0.95f, 0.02f).x, p(0.95f, 0.02f).y)
        close()
    }
    drawPath(field, cone)
    drawLine(outline, camera, p(0.05f, 0.02f), size.minDimension * 0.01f)
    drawLine(outline, camera, p(0.95f, 0.02f), size.minDimension * 0.01f)
    drawPhone(outline, camera)
    drawPlacementPerson(color)
}

private fun DrawScope.drawPhone(color: Color, center: Offset) {
    val width = size.width * 0.065f
    val height = size.height * 0.10f
    drawRoundRect(
        color = color,
        topLeft = Offset(center.x - width / 2f, center.y - height / 2f),
        size = Size(width, height),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(width * 0.16f),
        style = Stroke(width = size.minDimension * 0.018f),
    )
    drawCircle(color, radius = size.minDimension * 0.012f, center = Offset(center.x, center.y - height * 0.34f))
}

private fun DrawScope.drawSidePerson(color: Color) {
    val head = p(0.48f, 0.48f)
    val shoulder = p(0.54f, 0.52f)
    val elbow = p(0.60f, 0.62f)
    val wrist = p(0.66f, 0.62f)
    val hip = p(0.65f, 0.53f)
    val knee = p(0.72f, 0.59f)
    val ankle = p(0.77f, 0.62f)
    line(color, shoulder, elbow, wrist)
    line(color, shoulder, hip, knee, ankle)
    circle(color, head, 0.045f)
}

private fun DrawScope.drawPlacementPerson(color: Color) {
    val head = p(0.5f, 0.22f)
    val shoulderL = p(0.43f, 0.34f)
    val shoulderR = p(0.57f, 0.34f)
    val hip = p(0.5f, 0.57f)
    line(color, shoulderL, hip, shoulderR)
    line(color, shoulderL, p(0.38f, 0.44f), p(0.37f, 0.55f))
    line(color, shoulderR, p(0.62f, 0.44f), p(0.63f, 0.55f))
    line(color, hip, p(0.47f, 0.73f), p(0.47f, 0.87f))
    line(color, hip, p(0.53f, 0.73f), p(0.53f, 0.87f))
    circle(color, head, 0.055f)
}

private fun DrawScope.line(color: Color, start: Offset, end: Offset) {
    drawLine(color, start, end, strokeWidth = size.minDimension * 0.035f, cap = StrokeCap.Round)
}

private fun DrawScope.line(color: Color, start: Offset, middle: Offset, end: Offset) {
    line(color, start, middle)
    line(color, middle, end)
}

private fun DrawScope.line(color: Color, start: Offset, first: Offset, second: Offset, end: Offset) {
    line(color, start, first)
    line(color, first, second)
    line(color, second, end)
}

private fun DrawScope.circle(color: Color, center: Offset, radiusFraction: Float) {
    drawCircle(color, radius = size.minDimension * radiusFraction, center = center)
}

private fun DrawScope.p(x: Float, y: Float) = Offset(size.width * x, size.height * y)
