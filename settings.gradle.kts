pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "math-renderer"

include(":packages:compose-math")
project(":packages:compose-math").projectDir = file("packages/compose-math")

include(":samples:compose-math-sample")
project(":samples:compose-math-sample").projectDir = file("samples/compose-math-sample/app")
