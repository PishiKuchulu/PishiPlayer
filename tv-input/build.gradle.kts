plugins { id("com.android.library"); id("org.jetbrains.kotlin.android") }
android { namespace = "dev.pishi.player.input"; compileSdk = 35; defaultConfig { minSdk = 23 } }
dependencies { implementation(project(":player-api")); testImplementation("junit:junit:4.13.2") }
