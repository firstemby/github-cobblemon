plugins {
    kotlin("jvm") version "2.1.0"
    id ("net.neoforged.moddev") version "2.0.75"
    id("com.cobblemon.gradle")
}

buildscript {
    repositories {
        mavenCentral()
        gradlePluginPortal()
        maven("https://maven.architectury.dev/")
        maven("https://maven.fabricmc.net/")
    }
}

repositories {
    mavenCentral()
    maven("https://maven.fabricmc.net/")
    maven("https://maven.blamejared.com/")
    maven("https://api.modrinth.com/maven")
    maven(url = "https://thedarkcolour.github.io/KotlinForForge/")
    maven(url = "../deps")
}

dependencies {
    jarJar(implementation("com.cobblemon:mod:1.6.0-SNAPSHOT")) {}
    include("com.cobblemon:mod-resources:1.6.0-SNAPSHOT@zip")
    compileOnly(libs.bundles.mongo)
    implementation(libs.bundles.kotlin)
    api(libs.molang)
    compileOnly(libs.graal)
    api(libs.jei.api)
    compileOnly(libs.bundles.fabric.integrations.compileOnly) {
        isTransitive = false
    }
    compileOnly("net.fabricmc:sponge-mixin:0.15.5+mixin.0.8.7")
    compileOnly("io.github.llamalad7:mixinextras-fabric:0.4.1")
    testImplementation(libs.bundles.unitTesting)
    implementation(libs.neo.kotlin.forge)
}

cobblemon {
    accessWideners = provider {
        return@provider sourceSets.main.get().resources.filter() { file ->
            return@filter file.extension.equals("accesswidener")
        }
    }
}

neoForge {
    version = "21.1.95"
    runs {
        create("client") {
            client()
        }
    }
    mods {
        create("cobblemon") {
            sourceSets {
                sourceSets.main
            }
        }
    }
}

tasks.register<Copy>("copyModResources") {
    dependsOn(configurations.named("include"))
    from(configurations.named("include").get().resolve().map { zipTree(it) })
    into(tasks.processResources.get().destinationDir)
}

tasks.processResources {
    dependsOn(tasks.named("copyModResources"))
}