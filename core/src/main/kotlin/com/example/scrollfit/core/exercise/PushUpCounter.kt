package com.example.scrollfit.core.exercise

import com.example.scrollfit.core.pose.CountingProgress
import com.example.scrollfit.core.pose.ExerciseCounter
import com.example.scrollfit.core.pose.Landmark
import com.example.scrollfit.core.pose.PoseSample
import kotlin.math.acos
import kotlin.math.hypot

/** Provisional image-space thresholds; tune with device trials, not form assessments. */
data class PushUpConfig(
    val downAngleDegrees: Double = 105.0,
    val upAngleDegrees: Double = 155.0,
    val smoothingAlpha: Double = 0.35,
    val minConfidence: Float = 0.5f,
    val stablePhaseMs: Long = 100,
    val minTransitionMs: Long = 250,
    val maxCycleMs: Long = 10_000,
    val maxSampleGapMs: Long = 500,
)

/**
 * Serial caller owns this state. Consume raw, upright, unmirrored samples before
 * UI coalescing; this counter owns angle smoothing. Every observed loss discards
 * the incomplete cycle, while completed repetitions survive until reset().
 */
class PushUpCounter(private val config: PushUpConfig = PushUpConfig()) : ExerciseCounter {
    private enum class Phase { SEEKING_UP, UP, DOWN }
    private enum class Position { UP, DOWN, BETWEEN }
    private var phase = Phase.SEEKING_UP
    private var side: Int? = null
    private var smoothedAngle: Double? = null
    private var candidate: Position? = null
    private var candidateSinceMs = 0L
    private var phaseSinceMs = 0L
    private var lastTimestampMs = -1L
    private var repetitions = 0

    init {
        require(config.downAngleDegrees > 0 && config.downAngleDegrees < config.upAngleDegrees &&
            config.upAngleDegrees < 180)
        require(config.smoothingAlpha > 0 && config.smoothingAlpha <= 1)
        require(config.minConfidence in 0f..1f)
        require(config.stablePhaseMs > 0 && config.minTransitionMs > 0 &&
            config.maxCycleMs > 2 * config.minTransitionMs && config.maxSampleGapMs > 0)
    }

    override fun accept(sample: PoseSample?, timestampMonotonicMs: Long): CountingProgress {
        // Out-of-order callbacks cannot mutate any cycle or smoothing history.
        if (timestampMonotonicMs < 0 || timestampMonotonicMs <= lastTimestampMs) return progress(false)
        if (lastTimestampMs >= 0 && timestampMonotonicMs - lastTimestampMs > config.maxSampleGapMs) {
            clearCycle()
        }
        lastTimestampMs = timestampMonotonicMs
        if (sample == null || sample.timestampMonotonicMs != timestampMonotonicMs ||
            sample.landmarks.size != 33 || !sample.imageAspectRatio.isFinite() || sample.imageAspectRatio <= 0) {
            clearCycle()
            return progress(false)
        }

        fun validSide(offset: Int) = listOf(11, 13, 15, 23).all { valid(sample.landmarks[it + offset]) }
        if (side != null && !validSide(side!!)) clearCycle()
        if (side == null) {
            // Pin one anatomical side across the cycle, even if confidence ordering changes.
            side = (0..1).filter { validSide(it) }.maxByOrNull { offset ->
                listOf(11, 13, 15, 23).minOf {
                    val joint = sample.landmarks[it + offset]
                    minOf(joint.visibility, joint.presence)
                }
            }
        }
        val selected = side ?: return progress(false)
        val angle = elbowAngle(sample, selected)
        if (angle == null) {
            clearCycle()
            return progress(false)
        }
        val smooth = smoothedAngle?.let { it + config.smoothingAlpha * (angle - it) } ?: angle
        smoothedAngle = smooth
        if (phase == Phase.DOWN && timestampMonotonicMs - phaseSinceMs > config.maxCycleMs) {
            clearCycle()
            return progress(true)
        }
        val position = when {
            smooth >= config.upAngleDegrees -> Position.UP
            smooth <= config.downAngleDegrees -> Position.DOWN
            else -> Position.BETWEEN
        }
        if (position == Position.BETWEEN) {
            candidate = null
            return progress(true)
        }
        if (candidate != position) {
            candidate = position
            candidateSinceMs = timestampMonotonicMs
        }
        if (timestampMonotonicMs - candidateSinceMs < config.stablePhaseMs) return progress(true)
        when {
            phase == Phase.SEEKING_UP && position == Position.UP -> {
                phase = Phase.UP
                phaseSinceMs = timestampMonotonicMs
            }
            phase == Phase.UP && position == Position.DOWN -> {
                if (candidateSinceMs - phaseSinceMs < config.minTransitionMs) clearCycle()
                else {
                    phase = Phase.DOWN
                    phaseSinceMs = timestampMonotonicMs
                }
            }
            phase == Phase.DOWN && position == Position.UP -> {
                if (candidateSinceMs - phaseSinceMs < config.minTransitionMs) clearCycle()
                else {
                    if (repetitions < Int.MAX_VALUE) repetitions++
                    phase = Phase.UP
                    phaseSinceMs = timestampMonotonicMs
                }
            }
        }
        return progress(true)
    }

    private fun valid(l: Landmark) = l.x in 0f..1f && l.y in 0f..1f && l.z.isFinite() &&
        l.visibility in config.minConfidence..1f && l.presence in config.minConfidence..1f

    private fun elbowAngle(sample: PoseSample, offset: Int): Double? {
        val shoulder = sample.landmarks[11 + offset]
        val elbow = sample.landmarks[13 + offset]
        val wrist = sample.landmarks[15 + offset]
        val hip = sample.landmarks[23 + offset]
        val aspect = sample.imageAspectRatio.toDouble()
        val ax = (shoulder.x - elbow.x) * aspect
        val ay = (shoulder.y - elbow.y).toDouble()
        val bx = (wrist.x - elbow.x) * aspect
        val by = (wrist.y - elbow.y).toDouble()
        val a = hypot(ax, ay)
        val b = hypot(bx, by)
        val torso = hypot((hip.x - shoulder.x) * aspect, (hip.y - shoulder.y).toDouble())
        if (a < 0.0001 || b < 0.0001 || torso < 0.0001) return null
        return Math.toDegrees(acos(((ax * bx + ay * by) / (a * b)).coerceIn(-1.0, 1.0)))
    }

    private fun progress(tracking: Boolean) = CountingProgress(repetitions, tracking)

    private fun clearCycle() {
        phase = Phase.SEEKING_UP
        side = null
        smoothedAngle = null
        candidate = null
        candidateSinceMs = 0
        phaseSinceMs = 0
    }

    override fun reset() {
        clearCycle()
        repetitions = 0
        lastTimestampMs = -1
    }
}
