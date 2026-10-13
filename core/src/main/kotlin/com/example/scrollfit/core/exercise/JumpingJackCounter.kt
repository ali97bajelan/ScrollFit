package com.example.scrollfit.core.exercise

import com.example.scrollfit.core.pose.CountingProgress
import com.example.scrollfit.core.pose.ExerciseCounter
import com.example.scrollfit.core.pose.Landmark
import com.example.scrollfit.core.pose.PoseSample
import kotlin.math.acos
import kotlin.math.hypot

/** Provisional front-view movement thresholds, independent of the other counters. */
data class JumpingJackConfig(
    val closedArmAngleDegrees: Double = 30.0,
    val openArmAngleDegrees: Double = 140.0,
    val closedFeetTorsoRatio: Double = 0.65,
    val openFeetTorsoRatio: Double = 1.6,
    val minOutwardFootTorsoRatio: Double = 0.35,
    val smoothingAlpha: Double = 0.35,
    val minConfidence: Float = 0.5f,
    val stablePhaseMs: Long = 100,
    val minTransitionMs: Long = 200,
    val maxCycleMs: Long = 5_000,
    val maxSampleGapMs: Long = 500,
)

/**
 * Serial caller feeds raw upright poses before UI coalescing. Both arms and
 * both feet must reach the same endpoint together. Loss clears partial movement
 * and smoothing; completed repetitions survive until reset(). No form grading.
 */
class JumpingJackCounter(private val config: JumpingJackConfig = JumpingJackConfig()) : ExerciseCounter {
    private enum class Phase { SEEKING_CLOSED, CLOSED, OPEN }
    private enum class Position { CLOSED, OPEN, BETWEEN }
    private data class Geometry(val arms: List<Double>, val feet: Double, val outward: List<Double>)
    private var phase = Phase.SEEKING_CLOSED
    private var smoothed: Geometry? = null
    private var candidate: Position? = null
    private var candidateSinceMs = 0L
    private var phaseSinceMs = 0L
    private var lastTimestampMs = -1L
    private var repetitions = 0

    init {
        require(config.closedArmAngleDegrees >= 0 &&
            config.closedArmAngleDegrees < config.openArmAngleDegrees && config.openArmAngleDegrees < 180)
        require(config.closedFeetTorsoRatio > 0 &&
            config.closedFeetTorsoRatio < config.openFeetTorsoRatio && config.openFeetTorsoRatio.isFinite())
        require(config.minOutwardFootTorsoRatio > 0 && config.minOutwardFootTorsoRatio.isFinite())
        require(config.smoothingAlpha > 0 && config.smoothingAlpha <= 1)
        require(config.minConfidence in 0f..1f)
        require(config.stablePhaseMs > 0 && config.minTransitionMs > 0 &&
            config.maxCycleMs / 2 > config.minTransitionMs && config.maxSampleGapMs > 0)
    }

    override fun accept(sample: PoseSample?, timestampMonotonicMs: Long): CountingProgress {
        if (timestampMonotonicMs < 0 || timestampMonotonicMs <= lastTimestampMs) return progress(false)
        if (lastTimestampMs >= 0 && timestampMonotonicMs - lastTimestampMs > config.maxSampleGapMs) clearCycle()
        lastTimestampMs = timestampMonotonicMs
        if (sample == null || sample.timestampMonotonicMs != timestampMonotonicMs ||
            sample.landmarks.size != 33 || !sample.imageAspectRatio.isFinite() || sample.imageAspectRatio <= 0) {
            clearCycle()
            return progress(false)
        }
        val raw = geometry(sample)
        if (raw == null) {
            clearCycle()
            return progress(false)
        }
        fun ema(old: Double?, current: Double) = old?.let {
            it + config.smoothingAlpha * (current - it)
        } ?: current
        val old = smoothed
        val smooth = Geometry(raw.arms.mapIndexed { i, v -> ema(old?.arms?.get(i), v) },
            ema(old?.feet, raw.feet), raw.outward.mapIndexed { i, v -> ema(old?.outward?.get(i), v) })
        smoothed = smooth
        if (phase == Phase.OPEN && timestampMonotonicMs - phaseSinceMs > config.maxCycleMs) {
            clearCycle()
            return progress(true)
        }
        // Require the raw endpoint too: EMA tails must not combine arms from
        // one frame with spread feet from a later, arms-down frame.
        val position = position(smooth).takeIf { it == position(raw) } ?: Position.BETWEEN
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
            phase == Phase.SEEKING_CLOSED && position == Position.CLOSED -> {
                phase = Phase.CLOSED
                phaseSinceMs = timestampMonotonicMs
            }
            phase == Phase.CLOSED && position == Position.OPEN -> {
                if (candidateSinceMs - phaseSinceMs < config.minTransitionMs) clearCycle()
                else {
                    phase = Phase.OPEN
                    phaseSinceMs = timestampMonotonicMs
                }
            }
            phase == Phase.OPEN && position == Position.CLOSED -> {
                if (candidateSinceMs - phaseSinceMs < config.minTransitionMs) clearCycle()
                else {
                    if (repetitions < Int.MAX_VALUE) repetitions++
                    phase = Phase.CLOSED
                    phaseSinceMs = timestampMonotonicMs
                }
            }
        }
        return progress(true)
    }

    private fun position(g: Geometry): Position = when {
        g.arms.all { it <= config.closedArmAngleDegrees } && g.feet <= config.closedFeetTorsoRatio -> Position.CLOSED
        g.arms.all { it >= config.openArmAngleDegrees } && g.feet >= config.openFeetTorsoRatio &&
            g.outward.all { it >= config.minOutwardFootTorsoRatio } -> Position.OPEN
        else -> Position.BETWEEN
    }

    private fun valid(l: Landmark) = l.x in 0f..1f && l.y in 0f..1f && l.z.isFinite() &&
        l.visibility in config.minConfidence..1f && l.presence in config.minConfidence..1f

    private fun geometry(sample: PoseSample): Geometry? {
        val joints = sample.landmarks
        if (!listOf(11, 12, 15, 16, 23, 24, 27, 28).all { valid(joints[it]) }) return null
        val aspect = sample.imageAspectRatio.toDouble()
        val hipSpan = (joints[24].x - joints[23].x) * aspect
        val shoulderSpan = (joints[12].x - joints[11].x) * aspect
        if (kotlin.math.abs(hipSpan) < 0.0001 || kotlin.math.abs(shoulderSpan) < 0.0001 ||
            hipSpan * shoulderSpan <= 0) return null
        val torsoLengths = mutableListOf<Double>()
        val arms = mutableListOf<Double>()
        for (side in 0..1) {
            val shoulder = joints[11 + side]
            val hip = joints[23 + side]
            val wrist = joints[15 + side]
            if (shoulder.y >= hip.y || joints[27 + side].y <= hip.y) return null
            val ax = (hip.x - shoulder.x) * aspect
            val ay = (hip.y - shoulder.y).toDouble()
            val bx = (wrist.x - shoulder.x) * aspect
            val by = (wrist.y - shoulder.y).toDouble()
            val torso = hypot(ax, ay)
            val arm = hypot(bx, by)
            if (torso < 0.0001 || arm < 0.0001) return null
            torsoLengths.add(torso)
            arms.add(Math.toDegrees(acos(((ax * bx + ay * by) / (torso * arm)).coerceIn(-1.0, 1.0))))
        }
        val torso = torsoLengths.average()
        val direction = if (hipSpan > 0) 1 else -1
        val feet = (joints[28].x - joints[27].x) * aspect * direction / torso
        if (feet < 0) return null // Crossed/swapped feet cannot establish a closed endpoint.
        val outward = listOf((joints[23].x - joints[27].x) * aspect * direction / torso,
            (joints[28].x - joints[24].x) * aspect * direction / torso)
        return Geometry(arms, feet, outward)
    }

    private fun clearCycle() {
        phase = Phase.SEEKING_CLOSED
        smoothed = null
        candidate = null
        candidateSinceMs = 0
        phaseSinceMs = 0
    }

    private fun progress(tracking: Boolean) = CountingProgress(repetitions, tracking)

    override fun reset() {
        clearCycle()
        repetitions = 0
        lastTimestampMs = -1
    }
}
