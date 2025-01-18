plugins {
    id("java-gradle-plugin")
    kotlin("jvm") version "2.1.0"
}

repositories {
    mavenCentral()
    gradlePluginPortal()
    maven("https://maven.architectury.dev/")
    maven("https://maven.fabricmc.net/")
}

dependencies {
    implementation("net.neoforged:moddev-gradle:2.0.75")
    implementation("dev.architectury:at:1.0.1")
    implementation("net.fabricmc:access-widener:2.1.0")
}

gradlePlugin {
    plugins {
        create("cobblemongradle") {
            id = "com.cobblemon.gradle"
            implementationClass = "com.cobblemon.gradle.CobblemonGradle"
            version = "1.0.0"
        }
    }
}