plugins { id("com.android.application"); id("org.jetbrains.kotlin.android"); id("org.jetbrains.kotlin.plugin.compose") }
android {
    namespace = "dev.pishi.player.app"; compileSdk = 35
    // minSdk 26 (was 23): floor imposed by the libmpv AAR used by :player-mpv.
    defaultConfig { applicationId = "dev.pishi.player"; minSdk = 26; targetSdk = 35; versionCode = 4; versionName = "0.5.0" }
    buildFeatures { compose = true }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    // Per-ABI APKs keep the ~20-24 MiB of libmpv/FFmpeg natives per ABI out of
    // each other's downloads; the universal APK is kept for direct sideloading.
    splits { abi { isEnable = true; reset(); include("arm64-v8a", "armeabi-v7a", "x86_64"); isUniversalApk = true } }
}
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach { compilerOptions.jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
dependencies {
    implementation(project(":player-api")); implementation(project(":player-android")); implementation(project(":player-mpv")); implementation(project(":subtitle-core")); implementation(project(":subtitle-android")); implementation(project(":device-capabilities")); implementation(project(":tv-input")); implementation(project(":playback-storage")); implementation(project(":player-ui-android"))
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.compose.ui:ui:1.7.8")
    implementation("androidx.compose.foundation:foundation:1.7.8")
    implementation("androidx.media3:media3-ui:1.6.1")
    implementation("androidx.compose.ui:ui-tooling-preview:1.7.8")
    implementation("androidx.compose.material3:material3:1.3.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
}
