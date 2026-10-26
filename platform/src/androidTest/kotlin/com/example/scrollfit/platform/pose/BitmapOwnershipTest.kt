package com.example.scrollfit.platform.pose

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.scrollfit.core.pose.PoseSample
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Collections
import java.util.IdentityHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
class BitmapOwnershipTest {
    @Test fun sameCallerBitmapRemainsUsableAcrossInference() {
        val callerBitmap = Bitmap.createBitmap(8, 4, Bitmap.Config.ARGB_8888)
        callerBitmap.setPixel(0, 0, Color.MAGENTA)
        val ownedCopies = mutableListOf<Bitmap>()
        val images = mutableListOf<RecordingImage>()
        val backend = RecordingBackend { copy ->
            assertNotSame(callerBitmap, copy)
            assertEquals(Bitmap.Config.ARGB_8888, copy.config)
            ownedCopies += copy
            RecordingImage { timestamp -> PoseSample(timestamp, emptyList(), 99f) }
                .also(images::add)
        }
        val estimator = MediaPipePoseEstimator.createForTesting(backend)

        try {
            val first = estimator.estimate(callerBitmap, 100)
            assertEquals(2f, first!!.imageAspectRatio, 0f)
            assertFalse(callerBitmap.isRecycled)
            assertEquals(Color.MAGENTA, callerBitmap.getPixel(0, 0))

            val second = estimator.estimate(callerBitmap, 200)
            assertEquals(2f, second!!.imageAspectRatio, 0f)
            assertFalse(callerBitmap.isRecycled)
            assertEquals(Color.MAGENTA, callerBitmap.getPixel(0, 0))

            assertEquals(2, ownedCopies.size)
            assertNotSame(callerBitmap, ownedCopies[0])
            assertNotSame(ownedCopies[0], ownedCopies[1])
            assertTrue(ownedCopies.all { it.isRecycled })
            assertEquals(listOf(1, 1), images.map { it.closeCount })
        } finally {
            estimator.close()
            callerBitmap.recycle()
        }
    }

    @Test fun recycledInputRejectedBeforeNativeEntry() {
        val backend = RecordingBackend { RecordingImage { null } }
        val estimator = MediaPipePoseEstimator.createForTesting(backend)
        val recycled = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888).apply { recycle() }
        val valid = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)

        try {
            assertThrows(IllegalArgumentException::class.java) { estimator.estimate(recycled, 100) }
            assertEquals(0, backend.createCount)
            assertNull(estimator.estimate(valid, 100))
            assertEquals(1, backend.createCount)
        } finally {
            estimator.close()
            valid.recycle()
        }
    }

    @Test fun wrapperConstructionFailureReleasesOnlyTheOwnedCopy() {
        val callerBitmap = Bitmap.createBitmap(3, 2, Bitmap.Config.ARGB_8888)
        val attemptedCopy = AtomicReference<Bitmap>()
        val backend = RecordingBackend { copy ->
            attemptedCopy.set(copy)
            throw IllegalStateException("wrapper construction failed")
        }
        val estimator = MediaPipePoseEstimator.createForTesting(backend)

        try {
            assertThrows(IllegalStateException::class.java) { estimator.estimate(callerBitmap, 100) }
            assertTrue(attemptedCopy.get().isRecycled)
            assertFalse(callerBitmap.isRecycled)
            assertEquals(0, backend.closeCount)
        } finally {
            estimator.close()
            callerBitmap.recycle()
        }
    }

    @Test fun timestampsMustIncreaseBeforeAnotherNativeInference() {
        val backend = RecordingBackend { RecordingImage { null } }
        val estimator = MediaPipePoseEstimator.createForTesting(backend)
        val bitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)

        try {
            assertNull(estimator.estimate(bitmap, 100))
            assertThrows(IllegalArgumentException::class.java) { estimator.estimate(bitmap, 100) }
            assertThrows(IllegalArgumentException::class.java) { estimator.estimate(bitmap, 99) }
            assertEquals(1, backend.createCount)
        } finally {
            estimator.close()
            bitmap.recycle()
        }
    }

    @Test fun inferenceFailureAndNoPoseCloseTheirWrappersAndCopies() {
        val callerBitmap = Bitmap.createBitmap(4, 2, Bitmap.Config.ARGB_8888)
        val copies = mutableListOf<Bitmap>()
        val images = mutableListOf<RecordingImage>()
        val backend = RecordingBackend { copy ->
            copies += copy
            RecordingImage { timestamp ->
                if (timestamp == 100L) throw IllegalStateException("inference failed")
                null
            }.also(images::add)
        }
        val estimator = MediaPipePoseEstimator.createForTesting(backend)

        try {
            assertThrows(IllegalStateException::class.java) { estimator.estimate(callerBitmap, 100) }
            assertNull(estimator.estimate(callerBitmap, 200))
            assertEquals(listOf(1, 1), images.map { it.closeCount })
            assertTrue(copies.all { it.isRecycled })
            assertFalse(callerBitmap.isRecycled)
        } finally {
            estimator.close()
            callerBitmap.recycle()
        }
    }

    @Test fun closeIsIdempotentAndRejectsLaterInference() {
        val backend = RecordingBackend { RecordingImage { null } }
        val estimator = MediaPipePoseEstimator.createForTesting(backend)
        val bitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)

        try {
            estimator.close()
            estimator.close()
            assertEquals(1, backend.closeCount)
            assertThrows(IllegalStateException::class.java) { estimator.estimate(bitmap, 100) }
        } finally {
            bitmap.recycle()
        }
    }

    @Test fun cropRotationAliasesReleasedOnce() {
        for (partialCrop in listOf(false, true)) {
            for (rotation in listOf(0, 90, 180, 270)) {
                val source = Bitmap.createBitmap(4, 2, Bitmap.Config.ARGB_8888)
                val crop = if (partialCrop) Bitmap.createBitmap(source, 1, 0, 3, 2) else source
                val upright = uprightBitmap(crop, rotation)
                val expectedWidth = if (rotation == 90 || rotation == 270) crop.height else crop.width
                val expectedHeight = if (rotation == 90 || rotation == 270) crop.width else crop.height
                assertEquals(expectedWidth, upright.width)
                assertEquals(expectedHeight, upright.height)

                val resources = listOf(upright, crop, source)
                val unique = Collections.newSetFromMap(IdentityHashMap<Bitmap, Boolean>()).apply {
                    addAll(resources)
                }
                val releases = IdentityHashMap<Bitmap, Int>()
                try {
                    releaseDistinctByIdentity(resources) { bitmap ->
                        releases[bitmap] = (releases[bitmap] ?: 0) + 1
                        bitmap.recycle()
                    }

                    assertEquals(unique.size, releases.size)
                    assertTrue(releases.values.all { it == 1 })
                    assertTrue(unique.all { it.isRecycled })
                } finally {
                    releaseDistinctByIdentity(resources) { bitmap ->
                        if (!bitmap.isRecycled) bitmap.recycle()
                    }
                }
            }
        }
    }

    @Test fun closeWaitsForInference() {
        val worker = Executors.newSingleThreadExecutor()
        val inferenceStarted = CountDownLatch(1)
        val releaseInference = CountDownLatch(1)
        val closeRan = AtomicBoolean(false)

        try {
            val inference = worker.submit {
                inferenceStarted.countDown()
                check(releaseInference.await(5, TimeUnit.SECONDS))
            }
            assertTrue(inferenceStarted.await(5, TimeUnit.SECONDS))
            enqueueAfterInference(worker) { closeRan.set(true) }
            assertFalse(closeRan.get())

            releaseInference.countDown()
            inference.get(5, TimeUnit.SECONDS)
            worker.submit {}.get(5, TimeUnit.SECONDS)
            assertTrue(closeRan.get())
        } finally {
            releaseInference.countDown()
            worker.shutdownNow()
        }
    }

    private class RecordingBackend(
        private val create: (Bitmap) -> PoseInferenceImage,
    ) : PoseInferenceBackend {
        var createCount = 0
            private set
        var closeCount = 0
            private set

        override fun createImage(bitmap: Bitmap): PoseInferenceImage {
            createCount++
            return create(bitmap)
        }

        override fun close() {
            closeCount++
        }
    }

    private class RecordingImage(
        private val infer: (Long) -> PoseSample?,
    ) : PoseInferenceImage {
        var closeCount = 0
            private set

        override fun estimate(timestampMonotonicMs: Long): PoseSample? = infer(timestampMonotonicMs)

        override fun close() {
            closeCount++
        }
    }
}
