package com.example.scrollfit.platform.pose

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import com.example.scrollfit.core.pose.Landmark
import com.example.scrollfit.core.pose.PoseEstimator
import com.example.scrollfit.core.pose.PoseSample
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker

/** Create/use/close on the same serial worker; caller owns the bitmap. */
class MediaPipePoseEstimator(context: Context) : PoseEstimator<Bitmap> {
    private val detector: PoseLandmarker
    private var closed = false
    private var lastTimestampMs = -1L

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

    override fun estimate(frame: Bitmap, timestampMonotonicMs: Long): PoseSample? {
        check(!closed)
        require(timestampMonotonicMs > lastTimestampMs)
        lastTimestampMs = timestampMonotonicMs
        val image = BitmapImageBuilder(frame).build()
        try {
            val body = detector.detectForVideo(image, timestampMonotonicMs)
                .landmarks().firstOrNull() ?: return null
            return PoseSample(timestampMonotonicMs, body.map {
                Landmark(it.x(), it.y(), it.z(), it.visibility().orElse(0f), it.presence().orElse(0f))
            })
        } finally {
            image.close()
        }
    }

    override fun close() {
        if (!closed) {
            closed = true
            detector.close()
        }
    }
}

/** Rotate pixels once; front-camera mirroring belongs only to preview projection. */
internal fun uprightBitmap(bitmap: Bitmap, rotationDegrees: Int): Bitmap {
    require(rotationDegrees in setOf(0, 90, 180, 270))
    if (rotationDegrees == 0) return bitmap
    return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height,
        Matrix().apply { postRotate(rotationDegrees.toFloat()) }, false)
}
