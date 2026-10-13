package com.example.scrollfit.platform.pose

import android.content.Context
import java.security.MessageDigest

/** Versioned asset is packaged at build time; no runtime network fallback. */
object PoseModel {
    const val ASSET_PATH = "pose_landmarker_lite.task"
    const val SOURCE_URL =
        "https://storage.googleapis.com/mediapipe-models/pose_landmarker/pose_landmarker_lite/float16/1/pose_landmarker_lite.task"
    const val SHA256 = "59929e1d1ee95287735ddd833b19cf4ac46d29bc7afddbbf6753c459690d574a"
    const val SIZE_BYTES = 5_777_746L

    fun verify(context: Context) {
        val digest = MessageDigest.getInstance("SHA-256")
        var size = 0L
        context.assets.open(ASSET_PATH).use { input ->
            val buffer = ByteArray(8192)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
                size += count
            }
        }
        val actual = digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
        check(size == SIZE_BYTES && actual == SHA256) { "Bundled pose model integrity check failed" }
    }
}
