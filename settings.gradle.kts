pluginManagement {
    repositories {
        gradlePluginPortal()
        // com.pambrose.kotlinter is published to Maven Central, not the Gradle Plugin Portal.
        mavenCentral()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
rootProject.name = "jev4k"
