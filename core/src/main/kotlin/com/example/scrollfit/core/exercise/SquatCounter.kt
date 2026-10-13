package com.example.scrollfit.core.exercise

import com.example.scrollfit.core.pose.CountingProgress
import com.example.scrollfit.core.pose.ExerciseCounter
import com.example.scrollfit.core.pose.Landmark
import com.example.scrollfit.core.pose.PoseSample
import kotlin.math.acos
import kotlin.math.hypot

/** Provisional movement thresholds for device tuning; these do not grade posture. */
data class SquatConfig(
    val downHeightFraction: Double = 0.75,
    val upHeightFraction: Double = 0.90,
    val minStandingHeightRatio: Double = 1.4,
    val upAngleDegrees: Double = 155.0,
    val smoothingAlpha: Double = 0.35,
    val minConfidence: Float = 0.5f,
    val stablePhaseMs: Long = 120,
    val minTransitionMs: Long = 250,
    val maxCycleMs: Long = 10_000,
    val maxSampleGapMs: Long = 500,
)

/**
 * Serial, raw upright pose consumer. Both legs must lower relative to a stable
 * standing reference. Torso-normalized hip height works in front views where
 * projected knee angles can stay straight; angles confirm the standing return.
 * Loss discards calibration and incomplete movement, retaining completed counts.
 */
class SquatCounter(private val config: SquatConfig = SquatConfig()) : ExerciseCounter {
    private enum class Phase { SEEKING_STANDING, STANDING, LOWERED }
    private enum class Position { STANDING, LOWERED, BETWEEN }
    private data class Leg(val height: Double, val angle: Double)
    private var phase = Phase.SEEKING_STANDING
    private var smoothed: List<Leg>? = null
    private var standingHeights: List<Double>? = null
    private val calibrationHeights = mutableListOf<List<Double>>()
    private var candidate: Position? = null
    private var candidateSinceMs = 0L
    private var phaseSinceMs = 0L
    private var lastTimestampMs = -1L
    private var repetitions = 0

    init {
        require(config.downHeightFraction > 0 && config.downHeightFraction < config.upHeightFraction &&
            config.upHeightFraction < 1)
        require(config.minStandingHeightRatio.isFinite() && config.minStandingHeightRatio > 0)
        require(config.upAngleDegrees > 0 && config.upAngleDegrees < 180)
        require(config.smoothingAlpha > 0 && config.smoothingAlpha <= 1)
        require(config.minConfidence in 0f..1f)
        require(config.stablePhaseMs > 0 && config.minTransitionMs > 0 &&
            config.maxCycleMs / 2 > config.minTransitionMs && config.maxSampleGapMs > 0)
    }

    override fun accept(sample: PoseSample?, timestampMonotonicMs: Long): CountingProgress {
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
        val left = leg(sample, 0)
        val right = leg(sample, 1)
        if (left == null || right == null) {
            clearCycle()
            return progress(false)
        }
        val previous = smoothed
        val legs = listOf(left, right).mapIndexed { i, current ->
            val old = previous?.get(i)
            if (old == null) current else Leg(
                old.height + config.smoothingAlpha * (current.height - old.height),
                old.angle + config.smoothingAlpha * (current.angle - old.angle),
            )
        }
        smoothed = legs
        if (phase == Phase.LOWERED && timestampMonotonicMs - phaseSinceMs > config.maxCycleMs) {
            clearCycle()
            return progress(true)
        }
        val baseline = standingHeights
        val extended = legs.all { it.angle >= config.upAngleDegrees }
        val position = when {
            baseline == null -> if (extended && legs.all { it.height >= config.minStandingHeightRatio })
                Position.STANDING else Position.BETWEEN
            extended && legs.indices.all { legs[it].height >= baseline[it] * config.upHeightFraction } ->
                Position.STANDING
            legs.indices.all { legs[it].height <= baseline[it] * config.downHeightFraction } ->
                Position.LOWERED
            else -> Position.BETWEEN
        }
        if (position == Position.BETWEEN) {
            candidate = null
            calibrationHeights.clear()
            return progress(true)
        }
        if (candidate != position) {
            candidate = position
            candidateSinceMs = timestampMonotonicMs
            calibrationHeights.clear()
        }
        if (baseline == null && position == Position.STANDING) {
            // A bounded median of raw endpoint heights rejects isolated spikes
            // and avoids calibrating against the EMA's tail after standing up.
            calibrationHeights.add(listOf(left.height, right.height))
            if (calibrationHeights.size > 5) calibrationHeights.removeAt(0)
        }
        if (timestampMonotonicMs - candidateSinceMs < config.stablePhaseMs) return progress(true)
        // Dwell alone can be satisfied by two sparse frames; a median needs
        // at least three observations to reject a single calibration outlier.
        if (baseline == null && calibrationHeights.size < 3) return progress(true)
        when {
            phase == Phase.SEEKING_STANDING && position == Position.STANDING -> {
                standingHeights = legs.indices.map { side ->
                    val heights = calibrationHeights.map { it[side] }.sorted()
                    heights[heights.size / 2]
                }
                calibrationHeights.clear()
                phase = Phase.STANDING
                phaseSinceMs = timestampMonotonicMs
            }
            phase == Phase.STANDING && position == Position.LOWERED -> {
                if (candidateSinceMs - phaseSinceMs < config.minTransitionMs) clearCycle()
                else {
                    phase = Phase.LOWERED
                    phaseSinceMs = timestampMonotonicMs
                }
            }
            phase == Phase.LOWERED && position == Position.STANDING -> {
                if (candidateSinceMs - phaseSinceMs < config.minTransitionMs) clearCycle()
                else {
                    if (repetitions < Int.MAX_VALUE) repetitions++
                    phase = Phase.STANDING
                    phaseSinceMs = timestampMonotonicMs
                }
            }
        }
        return progress(true)
    }

    private fun valid(l: Landmark) = l.x in 0f..1f && l.y in 0f..1f && l.z.isFinite() &&
        l.visibility in config.minConfidence..1f && l.presence in config.minConfidence..1f

    private fun leg(sample: PoseSample, side: Int): Leg? {
        val shoulder = sample.landmarks[11 + side]
        val hip = sample.landmarks[23 + side]
        val knee = sample.landmarks[25 + side]
        val ankle = sample.landmarks[27 + side]
        if (!listOf(shoulder, hip, knee, ankle).all { valid(it) } ||
            shoulder.y >= hip.y || ankle.y <= hip.y) return null
        val aspect = sample.imageAspectRatio.toDouble()
        val torso = hypot((shoulder.x - hip.x) * aspect, (shoulder.y - hip.y).toDouble())
        val ax = (hip.x - knee.x) * aspect
        val ay = (hip.y - knee.y).toDouble()
        val bx = (ankle.x - knee.x) * aspect
        val by = (ankle.y - knee.y).toDouble()
        val thigh = hypot(ax, ay)
        val shin = hypot(bx, by)
        if (torso < 0.0001 || thigh < 0.0001 || shin < 0.0001) return null
        val angle = Math.toDegrees(acos(((ax * bx + ay * by) / (thigh * shin)).coerceIn(-1.0, 1.0)))
        return Leg((ankle.y - hip.y) / torso, angle)
    }

    private fun clearCycle() {
        phase = Phase.SEEKING_STANDING
        smoothed = null
        standingHeights = null
        calibrationHeights.clear()
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
