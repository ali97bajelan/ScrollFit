package com.example.scrollfit.core.exercise

import com.example.scrollfit.core.pose.CountingProgress
import com.example.scrollfit.core.pose.ExerciseCounter
import com.example.scrollfit.core.pose.Landmark
import com.example.scrollfit.core.pose.PoseSample
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.hypot

/** Provisional front-view thresholds; independent of the other exercise detectors. */
data class KneeRaiseConfig(
    val raisedKneeTorsoRatio: Double = 0.35,
    val standingKneeTorsoRatio: Double = 0.8,
    val standingKneeAngleDegrees: Double = 155.0,
    val smoothingAlpha: Double = 0.35,
    val minConfidence: Float = 0.5f,
    val stablePhaseMs: Long = 100,
    val minTransitionMs: Long = 200,
    val maxRaisedMs: Long = 5_000,
    val maxSampleGapMs: Long = 500,
)

/**
 * A stable standing -> left OR right lift -> standing counts once. Each side
 * owns its lift identity; switching sides without returning discards the cycle.
 * Same-side complete cycles also count. Serial raw poses arrive before UI
 * coalescing. Loss clears partial motion; counts survive until reset().
 */
class AlternatingKneeRaiseCounter(private val config: KneeRaiseConfig = KneeRaiseConfig()) : ExerciseCounter {
    private enum class Phase { SEEKING_STANDING, STANDING, LEFT_RAISED, RIGHT_RAISED }
    private enum class Position { STANDING, LEFT_RAISED, RIGHT_RAISED, BOTH_RAISED, BETWEEN }
    private data class Leg(val kneeDrop: Double, val angle: Double)
    private var phase = Phase.SEEKING_STANDING
    private var smoothed: List<Leg>? = null
    private var candidate: Position? = null
    private var candidateSinceMs = 0L
    private var phaseSinceMs = 0L
    private var lastTimestampMs = -1L
    private var repetitions = 0

    init {
        require(config.raisedKneeTorsoRatio.isFinite() && config.raisedKneeTorsoRatio >= 0 &&
            config.standingKneeTorsoRatio.isFinite() && config.raisedKneeTorsoRatio < config.standingKneeTorsoRatio)
        require(config.standingKneeAngleDegrees > 90 && config.standingKneeAngleDegrees <= 180)
        require(config.smoothingAlpha > 0 && config.smoothingAlpha <= 1)
        require(config.minConfidence in 0f..1f)
        require(config.stablePhaseMs > 0 && config.minTransitionMs > 0 &&
            config.maxRaisedMs / 2 > config.minTransitionMs && config.maxSampleGapMs > 0)
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
        val rawPosition = position(raw)
        // A lifted opposite knee cannot silently become the return of this leg.
        if (rawPosition == Position.BOTH_RAISED ||
            phase == Phase.LEFT_RAISED && rawPosition == Position.RIGHT_RAISED ||
            phase == Phase.RIGHT_RAISED && rawPosition == Position.LEFT_RAISED ||
            isRaised() && timestampMonotonicMs - phaseSinceMs > config.maxRaisedMs) {
            clearCycle()
            return progress(true)
        }
        fun ema(old: Double?, value: Double) = old?.let { it + config.smoothingAlpha * (value - it) } ?: value
        val old = smoothed
        val smooth = raw.mapIndexed { i, leg ->
            Leg(ema(old?.get(i)?.kneeDrop, leg.kneeDrop), ema(old?.get(i)?.angle, leg.angle))
        }
        smoothed = smooth
        // EMA tails must never manufacture standing or the wrong lifted side.
        val endpoint = position(smooth).takeIf { it == rawPosition } ?: Position.BETWEEN
        if (endpoint == Position.BETWEEN) {
            candidate = null
            return progress(true)
        }
        if (candidate != endpoint) {
            candidate = endpoint
            candidateSinceMs = timestampMonotonicMs
        }
        if (timestampMonotonicMs - candidateSinceMs < config.stablePhaseMs) return progress(true)
        when {
            phase == Phase.SEEKING_STANDING && endpoint == Position.STANDING -> {
                phase = Phase.STANDING
                phaseSinceMs = timestampMonotonicMs
            }
            phase == Phase.STANDING && endpoint in setOf(Position.LEFT_RAISED, Position.RIGHT_RAISED) -> {
                if (candidateSinceMs - phaseSinceMs < config.minTransitionMs) clearCycle()
                else {
                    phase = if (endpoint == Position.LEFT_RAISED) Phase.LEFT_RAISED else Phase.RIGHT_RAISED
                    phaseSinceMs = timestampMonotonicMs
                }
            }
            isRaised() && endpoint == Position.STANDING -> {
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

    private fun isRaised() = phase == Phase.LEFT_RAISED || phase == Phase.RIGHT_RAISED

    private fun position(legs: List<Leg>): Position {
        fun down(i: Int) = legs[i].kneeDrop >= config.standingKneeTorsoRatio &&
            legs[i].angle >= config.standingKneeAngleDegrees
        fun raised(i: Int) = legs[i].kneeDrop <= config.raisedKneeTorsoRatio
        return when {
            down(0) && down(1) -> Position.STANDING
            raised(0) && raised(1) -> Position.BOTH_RAISED
            raised(0) && down(1) -> Position.LEFT_RAISED
            down(0) && raised(1) -> Position.RIGHT_RAISED
            else -> Position.BETWEEN
        }
    }

    private fun valid(l: Landmark) = l.x in 0f..1f && l.y in 0f..1f && l.z.isFinite() &&
        l.visibility in config.minConfidence..1f && l.presence in config.minConfidence..1f

    private fun geometry(sample: PoseSample): List<Leg>? {
        val joints = sample.landmarks
        if (!listOf(11, 12, 23, 24, 25, 26, 27, 28).all { valid(joints[it]) }) return null
        val aspect = sample.imageAspectRatio.toDouble()
        val hips = (joints[24].x - joints[23].x) * aspect
        val shoulders = (joints[12].x - joints[11].x) * aspect
        if (abs(hips) < 0.0001 || abs(shoulders) < 0.0001 || hips * shoulders <= 0) return null
        val legs = mutableListOf<Leg>()
        for (side in 0..1) {
            val shoulder = joints[11 + side]
            val hip = joints[23 + side]
            val knee = joints[25 + side]
            val ankle = joints[27 + side]
            if (shoulder.y >= hip.y || knee.y >= ankle.y) return null
            val torso = hypot((hip.x - shoulder.x) * aspect, (hip.y - shoulder.y).toDouble())
            val ax = (hip.x - knee.x) * aspect
            val ay = (hip.y - knee.y).toDouble()
            val bx = (ankle.x - knee.x) * aspect
            val by = (ankle.y - knee.y).toDouble()
            val thigh = hypot(ax, ay)
            val shin = hypot(bx, by)
            if (torso < 0.0001 || thigh < 0.0001 || shin < 0.0001) return null
            val angle = Math.toDegrees(acos(((ax * bx + ay * by) / (thigh * shin)).coerceIn(-1.0, 1.0)))
            legs.add(Leg((knee.y - hip.y) / torso, angle))
        }
        return legs
    }

    private fun clearCycle() {
        phase = Phase.SEEKING_STANDING
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
