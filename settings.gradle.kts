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

rootProject.name = "PocketCLI"

include(":app")
include(":core:model")
include(":core:ui")
include(":core:security")
include(":data:local")
include(":data:opencode")
include(":runtime:remote")
include(":feature:sessions")
include(":feature:chat")
include(":feature:settings")
