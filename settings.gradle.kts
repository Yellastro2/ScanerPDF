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
        maven("https://artifactory-external.vkpartner.ru/artifactory/maven-rustore-exposed/") {
            content { includeGroupByRegex("ru\\.rustore.*") }
        }
        maven("https://jitpack.io") {
            content { includeGroupByRegex("cz\\.adaptech.*") }
        }
    }
}
rootProject.name = "ScannerAI"
include(":composeApp")
