package com.example.scrollfit.core.pose

enum class TrackingState { SEARCHING, TRACKING, LOST }
data class FilteredPose(val sample: PoseSample?, val tracking: TrackingState)

/** Optional geometry smoothing. Consumers still check their own required landmarks. */
class PoseFilter(
    requiredLandmarks: Set<Int>,
    private val minConfidence: Float = 0.5f,
    private val alpha: Float = 0.35f,
    private val resetGapMs: Long = 500,
    alternativeRequiredLandmarks: Set<Int> = emptySet(),
) {
    private val groups = listOf(requiredLandmarks.toSet()) +
        if (alternativeRequiredLandmarks.isEmpty()) emptyList() else listOf(alternativeRequiredLandmarks.toSet())
    private var previous: PoseSample? = null
    private var lastTimestampMs = -1L
    private var everTracked = false

    init {
        require(groups.all { group -> group.isNotEmpty() && group.all { it in 0..32 } })
        require(minConfidence in 0f..1f && alpha > 0f && alpha <= 1f && resetGapMs > 0)
    }

    fun accept(sample: PoseSample?, timestampMs: Long): FilteredPose {
        if (timestampMs < 0 || timestampMs <= lastTimestampMs) return missing()
        val gap = lastTimestampMs >= 0 && timestampMs - lastTimestampMs > resetGapMs
        lastTimestampMs = timestampMs
        val valid = sample != null && sample.timestampMonotonicMs == timestampMs &&
            sample.landmarks.size == 33 && sample.landmarks.all {
                it.x.isFinite() && it.y.isFinite() && it.z.isFinite() &&
                    it.visibility in 0f..1f && it.presence in 0f..1f
            } && groups.any { group -> group.all {
                    val landmark = sample.landmarks[it]
                    landmark.x in 0f..1f && landmark.y in 0f..1f &&
                        landmark.visibility >= minConfidence && landmark.presence >= minConfidence
                }
            }
        if (!valid) {
            previous = null
            return missing()
        }
        val current = sample
        val prior = if (gap) null else previous
        val smoothed = if (prior == null) current else current.copy(
            landmarks = current.landmarks.mapIndexed { index, landmark ->
                val old = prior.landmarks[index]
                // Never interpolate through a missing optional landmark.
                if (old.visibility < minConfidence || old.presence < minConfidence ||
                    landmark.visibility < minConfidence || landmark.presence < minConfidence) landmark
                else landmark.copy(
                    x = old.x + alpha * (landmark.x - old.x),
                    y = old.y + alpha * (landmark.y - old.y),
                    z = old.z + alpha * (landmark.z - old.z),
                )
            },
        )
        previous = smoothed
        everTracked = true
        return FilteredPose(smoothed, TrackingState.TRACKING)
    }

    private fun missing() = FilteredPose(null, if (everTracked) TrackingState.LOST else TrackingState.SEARCHING)

    fun reset() {
        previous = null
        lastTimestampMs = -1
        everTracked = false
    }
}

/** Generation and one-in-flight checks are shared between lifecycle/main and worker threads. */
class PoseSessionGate(private val maxFrameAgeMs: Long = 250) {
    private var generation = 0L
    private var active = false
    private var inFlight = false
    private var lastTimestampMs = -1L

    init { require(maxFrameAgeMs > 0) }

    @Synchronized fun start(): Long {
        generation++
        active = true
        inFlight = false
        lastTimestampMs = -1
        return generation
    }

    @Synchronized fun stop() {
        generation++
        active = false
        inFlight = false
    }

    @Synchronized fun isCurrent(token: Long) = active && token == generation

    @Synchronized fun isFresh(token: Long, timestampMs: Long, nowMs: Long) =
        isCurrent(token) && timestampMs >= 0 && timestampMs <= nowMs && nowMs - timestampMs <= maxFrameAgeMs

    @Synchronized fun admit(token: Long, timestampMs: Long, nowMs: Long): Boolean {
        if (!isFresh(token, timestampMs, nowMs) || inFlight || timestampMs <= lastTimestampMs) return false
        inFlight = true
        lastTimestampMs = timestampMs
        return true
    }

    @Synchronized fun finish(token: Long) {
        if (isCurrent(token)) inFlight = false
    }
}

/** FIT_CENTER pixel projection; front mirroring never changes body landmark indices. */
data class PreviewImageRect(val left: Float, val top: Float, val width: Float, val height: Float)

object PreviewProjection {
    /** The actual camera image area inside a FIT_CENTER preview, excluding letterbox bars. */
    fun imageRect(imageWidth: Int, imageHeight: Int, viewWidth: Float, viewHeight: Float): PreviewImageRect {
        require(imageWidth > 0 && imageHeight > 0 && viewWidth > 0 && viewHeight > 0)
        val scale = minOf(viewWidth / imageWidth, viewHeight / imageHeight)
        val width = imageWidth * scale
        val height = imageHeight * scale
        return PreviewImageRect((viewWidth - width) / 2f, (viewHeight - height) / 2f, width, height)
    }

    fun point(x: Float, y: Float, imageWidth: Int, imageHeight: Int,
              viewWidth: Float, viewHeight: Float, mirrored: Boolean): Pair<Float, Float> {
        val image = imageRect(imageWidth, imageHeight, viewWidth, viewHeight)
        return image.left + (if (mirrored) 1 - x else x) * image.width to
            image.top + y * image.height
    }
}
