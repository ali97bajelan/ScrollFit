import org.gradle.api.artifacts.VersionCatalogsExtension

plugins { alias(libs.plugins.kotlin.jvm) }

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions { jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17 }
}

val catalog = extensions.getByType(VersionCatalogsExtension::class.java).named("libs")

dependencies {
    testImplementation(libs.junit)
    testImplementation(catalog.findLibrary("kotlinx-coroutines-core").get())
    testImplementation(catalog.findLibrary("kotlinx-coroutines-test").get())
}
