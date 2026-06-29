pluginManagement {
    repositories {
        gradlePluginPortal()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}
rootProject.name = "rlauxe-viewer"

include("uibase")
include("viewer")

// these are placed inside the jar/uberjar
project(":viewer").name = "rlauxe-viewer"
project(":uibase").name = "rlauxe-uibase"


