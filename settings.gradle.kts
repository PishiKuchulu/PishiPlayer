pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }
dependencyResolutionManagement { repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS); repositories { google(); mavenCentral() } }
rootProject.name = "UniversalPlayer"
include(":app-android", ":player-api", ":player-android", ":subtitle-core", ":subtitle-android", ":device-capabilities", ":tv-input", ":playback-storage", ":player-ui-android")
