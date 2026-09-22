
// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.jetbrains.kotlin.android) apply false
}

tasks.register("packageAll") {
    group = "build"
    description = "Builds debug and release APKs plus the release Android App Bundle."
    dependsOn(":app:assembleDebug", ":app:assembleRelease", ":app:bundleRelease")
}
