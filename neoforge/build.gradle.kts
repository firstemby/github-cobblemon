import org.gradle.internal.impldep.bsh.commands.dir
import java.nio.file.Files
import java.nio.file.StandardOpenOption
import kotlin.io.path.name

plugins {
    kotlin("jvm") version "2.1.0"
    id ("net.neoforged.moddev") version "2.0.75"
    id("com.cobblemon.gradle")
}

repositories {
    mavenCentral()
    maven("https://maven.fabricmc.net/")
    maven("https://maven.blamejared.com/")
    maven("https://api.modrinth.com/maven")
    maven(url = "https://thedarkcolour.github.io/KotlinForForge/")
    maven(url = "../deps")
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
    //Originally I had this jar-in-jared, but the way that NF does class loading for JiJed dependencies
    //Makes it so that they can't access minecraft classes. So instead we do an implementation dependency
    //and make sure that the FMLModType variable is set in this jar's manifest
    implementation("com.cobblemon:mod:1.6.0-SNAPSHOT")
    includeRootResources("com.cobblemon:mod-resources:1.6.0-SNAPSHOT")
    "accessWidener"("com.cobblemon:mod-accesswideners:1.6.0-SNAPSHOT")

    //additionalRuntimeClasspathLibraries are libraries that need to be added to the task runtime classpath
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

tasks.register("addAccessTransformersToToml") {
    dependsOn(tasks.processResources)
    doLast {
        tasks.processResources.get().outputs.files.filter {
            it.path.endsWith("accesstransformer.cfg")
        }
    }
}

//Add access tranformers to neoforge.mods.toml
tasks.processResources {
    val accessTransformers = mutableSetOf<String>()
    eachFile {
        if (name.endsWith("accesstransformer.cfg")) {
            accessTransformers.add(relativeSourcePath.pathString)
        }
    }
    doLast {
        val modsFile = Files.walk(destinationDir.toPath()).filter { it.name == "neoforge.mods.toml" }.findFirst().orElse(null)
        if (modsFile != null) {
            val rawFile = Files.readString(modsFile)
            val genAccessTransformersStr = accessTransformers.joinToString { "[[accessTransformers]]\nfile=\"$it\"\n" }
            val transformedFile = rawFile.replace("ACCESS_TRANSFORMERS_TEMPLATE", genAccessTransformersStr)
            Files.writeString(modsFile, transformedFile, StandardOpenOption.TRUNCATE_EXISTING)
        }
    }
}