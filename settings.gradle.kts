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
//        maven("https://repo1.maven.org/maven2/") //zstd
        mavenLocal()
        maven("https://jitpack.io")
    }
}


rootProject.name = "AppSnapShotor"
include(":app")
include(":api")
include(":hiddenapi")
include(":systemapi")
include(":io-nativefs")
include(":io-zstd")
include(":io-tar")
include(":provider")