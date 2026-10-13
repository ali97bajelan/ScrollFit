package com.example.scrollfit.platform.pose

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OfflinePoseTest {
    @Test fun packagedModelPassesIntegrityCheck() {
        PoseModel.verify(ApplicationProvider.getApplicationContext())
    }

    @Test fun blankImageRunsPackagedNativeInferenceWithoutAPose() {
        val bitmap = Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.BLACK)
        try {
            MediaPipePoseEstimator(ApplicationProvider.getApplicationContext()).use {
                assertNull(it.estimate(bitmap, 100))
                assertNull(it.estimate(bitmap, 200))
            }
        } finally {
            bitmap.recycle()
        }
    }

    @Test fun quarterTurnRotatesWithoutMirroringAnatomy() {
        val bitmap = Bitmap.createBitmap(2, 3, Bitmap.Config.ARGB_8888)
        bitmap.setPixel(0, 0, Color.RED)
        bitmap.setPixel(1, 0, Color.BLUE)
        val rotated = uprightBitmap(bitmap, 90)
        try {
            assertEquals(3, rotated.width)
            assertEquals(2, rotated.height)
            assertEquals(Color.RED, rotated.getPixel(2, 0))
            assertEquals(Color.BLUE, rotated.getPixel(2, 1))
        } finally {
            if (rotated !== bitmap) rotated.recycle()
            bitmap.recycle()
        }
    }
}
