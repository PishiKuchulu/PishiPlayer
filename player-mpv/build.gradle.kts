plugins { id("com.android.library"); id("org.jetbrains.kotlin.android") }
// minSdk is 26 here (the app matches): the libmpv AAR's manifest floor is API 26,
// so the manifest merger rejects anything lower. The bundled mpv/FFmpeg build is
// GPLv3 — see DOCS/SOFTWARE_ENGINE.md and DOCS/THIRD_PARTY_LICENSES.md before
// distributing binaries.
android { namespace = "dev.pishi.player.mpv"; compileSdk = 35; defaultConfig { minSdk = 26 }; compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 } }
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach { compilerOptions.jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
dependencies {
    implementation(project(":player-api"))
    implementation("dev.jdtech.mpv:libmpv:0.4.1")
}
