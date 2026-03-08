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
        mavenLocal()
        maven("https://jitpack.io")
    }
}


rootProject.name = "AppSnapShotor"
include(":app")
include(":api")
include(":hiddenapi")
include(":systemapi")
include(":native")
//include(":provider-filesystem")
include(":provider-appmanager")
include(":provider-datasyncer")

//includeBuild("Android-DataBackup")
//includeBuild("Android-DataBackup/source-next")