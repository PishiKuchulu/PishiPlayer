plugins {
    id("com.android.application") version "8.9.2" apply false
    id("com.android.library") version "8.9.2" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
    id("org.jetbrains.kotlin.jvm") version "2.0.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21" apply false
}

// JVM-target alignment for every Android module. Without this, AGP leaves the
// Java compile target at 1.8 while the Kotlin plugin targets JVM 17 (from the
// JDK running Gradle), and Kotlin's JVM-target validation fails the build
// ("Inconsistent JVM-target compatibility detected ... compileDebugJavaWithJavac
// (1.8) and compileDebugKotlin (17)") — first seen in CI run 39943156798 on
// :tv-input, but every Android module had the same mismatch.
//
// v3 note (run 39943671700, same error): setting compileOptions from a root
// afterEvaluate hook did NOT reach AGP's tasks. This version hooks the moment
// the Android base plugin is applied (com.android.base fires for both
// application and library modules), i.e. during module evaluation — strictly
// before AGP reads compileOptions in its own afterEvaluate.
subprojects {
    plugins.withId("com.android.base") {
        extensions.configure<com.android.build.api.dsl.CommonExtension<*, *, *, *, *, *>> {
            compileOptions {
                sourceCompatibility = JavaVersion.VERSION_17
                targetCompatibility = JavaVersion.VERSION_17
            }
        }
    }
    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
        compilerOptions.jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}
