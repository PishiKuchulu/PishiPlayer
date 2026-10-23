plugins { id("com.android.library"); id("org.jetbrains.kotlin.android") }
android { namespace = "dev.pishi.player.subtitle.android"; compileSdk = 35; defaultConfig { minSdk = 23 } }
dependencies { implementation(project(":subtitle-core")); implementation("androidx.media3:media3-ui:1.6.1") }
