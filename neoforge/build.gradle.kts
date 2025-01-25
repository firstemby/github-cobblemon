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

val includeRootResources = configurations.create("includeRootResources") {
    isCanBeConsumed = false
    isCanBeResolved = true
    isCanBeDeclared = true
    attributes {
        attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, objects.named(LibraryElements.RESOURCES))
    }
}

//Something really fun here.
//The order of these configs NEEDS to go cobblemon -> neoForge -> dependencies
//Nasty, but cobblemon adds a dependency to MDGs minecraft source generation (access widener conversion)
//And MDG only creates the additionalRuntimeConfiguration dependency scope if the "version" fiels has already been configured
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
        create("server") {
            server()
        }
    }
    mods {
        create("cobblemon") {
            sourceSet(sourceSets.main.get())
        }
    }
}

dependencies {
    implementation("com.cobblemon:mod:1.6.0-SNAPSHOT")
    includeRootResources("com.cobblemon:mod-resources:1.6.0-SNAPSHOT")

    api(libs.molang)
    "additionalRuntimeClasspath"(libs.molang)

    implementation("thedarkcolour:kotlinforforge-neoforge:5.7.0:slim")
    "additionalRuntimeClasspath"("org.jetbrains.kotlin:kotlin-stdlib:2.1.0")
    "additionalRuntimeClasspath"("org.jetbrains.kotlin:kotlin-reflect:2.1.0")
    "additionalRuntimeClasspath"("org.jetbrains.kotlinx:kotlinx-coroutines-core")
    "additionalRuntimeClasspath"("org.jetbrains.kotlinx:kotlinx-serialization-core:1.7.3")
    "additionalRuntimeClasspath"("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    compileOnly(libs.bundles.mongo)

    compileOnly(libs.graal)
    "additionalRuntimeClasspath"(libs.graal)

    compileOnly("net.fabricmc:sponge-mixin:0.15.5+mixin.0.8.7")
    compileOnly("io.github.llamalad7:mixinextras-fabric:0.4.1")
    //api(libs.jei.api)
    //compileOnly(libs.bundles.fabric.integrations.compileOnly) {
    //    isTransitive = false
    //}

    testImplementation(libs.bundles.unitTesting)
}

tasks.register<Copy>("copyModResources") {
    dependsOn(includeRootResources)
    from(provider {
        includeRootResources.map { zipTree(it) }
    })
    into(tasks.processResources.get().destinationDir)
}

tasks.processResources {
    dependsOn(tasks.named("copyModResources"))
}