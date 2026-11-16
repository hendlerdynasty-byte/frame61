pluginManagement {
    repositories {
        maven { url = uri("https://plugins.gradle.org/m2/") }
        gradlePluginPortal()
        google()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io")
    }
}

rootProject.name = "frame61"

plugins {
}

include(":app")
include(":player")
include(":recs")
include(":core:data")
include(":core:design")
include(":core:meta")
include(":core:net")
include(":scraper:engine")
include(":scraper:resolver")
include(":sources:free")
