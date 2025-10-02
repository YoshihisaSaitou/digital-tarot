pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // Add JitPack to resolve GitHub-hosted artifacts like PhotoView
        maven(url = "https://jitpack.io")
    }
}

rootProject.name = "DigitalTarotAndroidApp"
include(":app")

include(":OpenCV")
