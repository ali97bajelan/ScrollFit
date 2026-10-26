import java.security.MessageDigest

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.example.scrollfit.platform"
    compileSdk = 37
    defaultConfig {
        minSdk = 28
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    api(project(":core"))
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    // ExerciseGateDatabase publicly extends RoomDatabase, so downstream modules need this API.
    api(libs.androidx.room.runtime)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.mediapipe.tasks.vision)
    implementation(libs.androidx.room.ktx)
    implementation(libs.kotlinx.coroutines.android)
    ksp(libs.androidx.room.compiler)
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.junit)
}

// Retain real v1 (T03) and v2 (T04) exports when Android builds become available.
ksp { arg("room.schemaLocation", "$projectDir/schemas") }

// Never produce an APK whose first exercise launch requires a model download.
val verifyPoseModel by tasks.registering {
    val asset = layout.projectDirectory.file("src/main/assets/pose_landmarker_lite.task")
    inputs.file(asset)
    doLast {
        val file = asset.asFile
        check(file.isFile && file.length() == 5_777_746L) { "Missing or truncated offline pose model" }
        val digest = MessageDigest.getInstance("SHA-256").digest(file.readBytes())
            .joinToString("") { "%02x".format(it.toInt() and 255) }
        check(digest == "59929e1d1ee95287735ddd833b19cf4ac46d29bc7afddbbf6753c459690d574a") {
            "Offline pose model checksum mismatch"
        }
    }
}
tasks.named("preBuild") { dependsOn(verifyPoseModel) }
