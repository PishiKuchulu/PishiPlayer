plugins { id("com.android.library"); id("org.jetbrains.kotlin.android") }
android { namespace = "dev.pishi.player.capabilities"; compileSdk = 35; defaultConfig { minSdk = 23 } }
dependencies { implementation(project(":player-api")) }
