plugins { id("com.android.library"); id("org.jetbrains.kotlin.android"); id("org.jetbrains.kotlin.plugin.compose") }
android { namespace = "dev.pishi.player.ui"; compileSdk = 35; defaultConfig { minSdk = 23 }; buildFeatures { compose = true } }
dependencies {
    implementation(project(":player-api"))
    implementation("androidx.compose.ui:ui:1.7.8")
    implementation("androidx.compose.foundation:foundation:1.7.8")
    implementation("androidx.compose.material3:material3:1.3.1")
}
