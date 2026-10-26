package com.example.scrollfit.platform.pose

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import com.example.scrollfit.core.pose.Landmark
import com.example.scrollfit.core.pose.PoseEstimator
import com.example.scrollfit.core.pose.PoseSample
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker

/** MediaPipe wrapper lifecycle remains confined to the camera session's serial worker. */
internal interface PoseInferenceBackend : AutoCloseable {
    fun createImage(bitmap: Bitmap): PoseInferenceImage
}

internal interface PoseInferenceImage : AutoCloseable {
    fun estimate(timestampMonotonicMs: Long): PoseSample?
}

/** Caller keeps ownership of [Bitmap]; MediaPipe only sees a temporary estimator-owned copy. */
class MediaPipePoseEstimator private constructor(
    private val backend: PoseInferenceBackend,
) : PoseEstimator<Bitmap> {
    constructor(context: Context) : this(MediaPipePoseBackend(context))

    internal companion object {
        fun createForTesting(backend: PoseInferenceBackend): MediaPipePoseEstimator =
            MediaPipePoseEstimator(backend)
    }

    private var closed = false
    private var lastTimestampMs = -1L

    override fun estimate(frame: Bitmap, timestampMonotonicMs: Long): PoseSample? {
        check(!closed) { "Pose estimator is closed" }
        require(!frame.isRecycled) { "Cannot estimate a recycled bitmap" }
        require(timestampMonotonicMs > lastTimestampMs) { "Timestamps must increase" }
        lastTimestampMs = timestampMonotonicMs

        // Read metadata while the caller's bitmap is known to be live. BitmapImage.close()
        // may dispose its backing bitmap, so MediaPipe receives only this owned copy.
        val width = frame.width
        val height = frame.height
        val ownedCopy = frame.copy(Bitmap.Config.ARGB_8888, false)
            ?: error("Could not create an ARGB_8888 inference copy")
        try {
            val image = backend.createImage(ownedCopy)
            try {
                return image.estimate(timestampMonotonicMs)?.copy(
                    imageAspectRatio = width.toFloat() / height,
                )
            } finally {
                image.close()
            }
        } finally {
            if (!ownedCopy.isRecycled) ownedCopy.recycle()
        }
    }

    override fun close() {
        if (!closed) {
            closed = true
            backend.close()
        }
    }
}

private class MediaPipePoseBackend(context: Context) : PoseInferenceBackend {
    private val detector: PoseLandmarker

    init {
        PoseModel.verify(context)
        val options = PoseLandmarker.PoseLandmarkerOptions.builder()
            .setBaseOptions(BaseOptions.builder().setModelAssetPath(PoseModel.ASSET_PATH)
                .setDelegate(Delegate.CPU).build())
            // Synchronous VIDEO mode is also suitable for ordered camera frames on our worker.
            // It gives explicit image lifetime and single-in-flight ownership.
            .setRunningMode(RunningMode.VIDEO)
            .setNumPoses(1)
            .setMinPoseDetectionConfidence(0.5f)
            .setMinPosePresenceConfidence(0.5f)
            .setMinTrackingConfidence(0.5f)
            .setOutputSegmentationMasks(false)
            .build()
        detector = PoseLandmarker.createFromOptions(context.applicationContext, options)
    }

    override fun createImage(bitmap: Bitmap): PoseInferenceImage {
        val image: MPImage = BitmapImageBuilder(bitmap).build()
        return MediaPipePoseInferenceImage(detector, image)
    }

    override fun close() {
        detector.close()
    }
}

private class MediaPipePoseInferenceImage(
    private val detector: PoseLandmarker,
    private val image: MPImage,
) : PoseInferenceImage {
    override fun estimate(timestampMonotonicMs: Long): PoseSample? {
        val body = detector.detectForVideo(image, timestampMonotonicMs)
            .landmarks().firstOrNull() ?: return null
        return PoseSample(timestampMonotonicMs, body.map {
            Landmark(it.x(), it.y(), it.z(), it.visibility().orElse(0f), it.presence().orElse(0f))
        })
    }

    override fun close() = image.close()
}

/** Rotate pixels once; front-camera mirroring belongs only to preview projection. */
internal fun uprightBitmap(bitmap: Bitmap, rotationDegrees: Int): Bitmap {
    require(rotationDegrees in setOf(0, 90, 180, 270))
    if (rotationDegrees == 0) return bitmap
    return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height,
        Matrix().apply { postRotate(rotationDegrees.toFloat()) }, false)
}
