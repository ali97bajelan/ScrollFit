package com.example.scrollfit.platform.pose

/** T06 must bundle this model before enabling inference; never download it at runtime. */
object PoseModel {
    const val ASSET_PATH = "pose_landmarker_lite.task"
    const val SOURCE_URL =
        "https://storage.googleapis.com/mediapipe-models/pose_landmarker/pose_landmarker_lite/float16/1/pose_landmarker_lite.task"
}
