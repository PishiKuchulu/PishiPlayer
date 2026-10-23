plugins { id("com.android.application"); id("org.jetbrains.kotlin.android"); id("org.jetbrains.kotlin.plugin.compose") }
android {
    namespace = "dev.pishi.player.app"; compileSdk = 35
    defaultConfig { applicationId = "dev.pishi.player"; minSdk = 23; targetSdk = 35; versionCode = 3; versionName = "0.4.1" }
    buildFeatures { compose = true }
}
dependencies {
    implementation(project(":player-api")); implementation(project(":player-android")); implementation(project(":subtitle-core")); implementation(project(":subtitle-android")); implementation(project(":device-capabilities")); implementation(project(":tv-input")); implementation(project(":playback-storage")); implementation(project(":player-ui-android"))
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.compose.ui:ui:1.7.8")
    implementation("androidx.compose.foundation:foundation:1.7.8")
    implementation("androidx.media3:media3-ui:1.6.1")
    implementation("androidx.compose.ui:ui-tooling-preview:1.7.8")
    implementation("androidx.compose.material3:material3:1.3.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
}
