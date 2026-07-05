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
    }
}

rootProject.name = "RetroVault"
include(":app")
include(":core-model")
include(":core-ui")
include(":data-supabase")
include(":feature-store")
include(":core-emulator")
include(":core-input")
include(":feature-player")
include(":data-download")
include(":data-saves")
include(":data-billing")
include(":data-library")
include(":data-settings")
