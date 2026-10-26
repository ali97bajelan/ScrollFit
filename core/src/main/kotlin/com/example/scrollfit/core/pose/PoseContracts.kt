package com.example.scrollfit.core.pose

/** Coordinates in the unmirrored, upright image; visibility and presence are in [0, 1]. */
data class Landmark(
    val x: Float,
    val y: Float,
    val z: Float,
    val visibility: Float,
    val presence: Float,
)

/** Landmark list uses MediaPipe's 33-landmark order. No image is retained. */
data class PoseSample(
    val timestampMonotonicMs: Long,
    val landmarks: List<Landmark>,
    /** Upright image width / height; needed for angles in normalized image coordinates. */
    val imageAspectRatio: Float = 1f,
)

/**
 * Platform adapter owns frame conversion, rotation and temporary resources.
 * The caller retains ownership of [Frame] and may reuse it after [estimate] returns.
 */
interface PoseEstimator<Frame> : AutoCloseable {
    fun estimate(frame: Frame, timestampMonotonicMs: Long): PoseSample?
}

/** Four separate implementations will own smoothing and cycle state in T07–T10. */
interface ExerciseCounter {
    fun accept(sample: PoseSample?, timestampMonotonicMs: Long): CountingProgress
    fun reset()
}

data class CountingProgress(val repetitions: Int, val trackingAvailable: Boolean)
