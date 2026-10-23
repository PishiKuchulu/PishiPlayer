plugins {
    id("com.android.application") version "8.9.2" apply false
    id("com.android.library") version "8.9.2" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
    id("org.jetbrains.kotlin.jvm") version "2.0.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21" apply false
}

// JVM-target alignment lives in each Android module's own build file
// (compileOptions 17 + Kotlin jvmTarget 17). Two root-level attempts failed in
// CI for hook-timing reasons: afterEvaluate was too late (run 39943671700),
// and a plugins.withId callback ran before AGP registered its extension
// (run 39944168638). Per-module settings are evaluated with the module, which
// is the timing AGP itself expects.
