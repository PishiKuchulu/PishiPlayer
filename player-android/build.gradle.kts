plugins { id("com.android.library"); id("org.jetbrains.kotlin.android") }
android { namespace = "dev.pishi.player.android"; compileSdk = 35; defaultConfig { minSdk = 23 } }
dependencies {
    implementation(project(":player-api"))
    implementation("androidx.media3:media3-exoplayer:1.6.1")
    implementation("androidx.media3:media3-exoplayer-hls:1.6.1")
    implementation("androidx.media3:media3-exoplayer-dash:1.6.1")
    implementation("androidx.media3:media3-ui:1.6.1")
}
