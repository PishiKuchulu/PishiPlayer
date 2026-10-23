plugins { id("com.android.library"); id("org.jetbrains.kotlin.android") }
android { namespace = "dev.pishi.player.input"; compileSdk = 35; defaultConfig { minSdk = 23 }; compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 } }
dependencies { implementation(project(":player-api")); testImplementation("junit:junit:4.13.2") }
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach { compilerOptions.jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
