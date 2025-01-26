import com.cobblemon.gradle.extension.tasks.UnzipAccessWidenersTask

plugins {
    kotlin("jvm") version "2.1.0"
    id("fabric-loom") version "1.9-SNAPSHOT"
    id("com.cobblemon.gradle")}

repositories {
    mavenCentral()
    maven("https://maven.fabricmc.net/")
    maven("https://maven.blamejared.com/")
    maven("https://api.modrinth.com/maven")
    maven(url = "https://thedarkcolour.github.io/KotlinForForge/")
    maven(url = "../deps")
}

loom {
    mods {
        create("cobblemon") {
            sourceSet(sourceSets.main.get())
        }
    }
    accessWidenerPath = file("E:\\Projects\\cobblemon\\common\\src\\main\\resources\\cobblemon-common.accesswidener")
}

cobblemon {
    convertAccessWideners = false
}

dependencies {
    minecraft("com.mojang:minecraft:1.21.1")
    mappings(loom.officialMojangMappings())
    //Originally I had this jar-in-jared, but the way that NF does class loading for JiJed dependencies
    //Makes it so that they can't access minecraft classes. So instead we do an implementation dependency
    //and make sure that the FMLModType variable is set in this jar's manifest
    implementation("com.cobblemon:mod:1.6.0-SNAPSHOT")
    includeRootResources("com.cobblemon:mod-resources:1.6.0-SNAPSHOT")
    accessWidener("com.cobblemon:mod-accesswideners:1.6.0-SNAPSHOT")

    //additionalRuntimeClasspathLibraries are libraries that need to be added to the task runtime classpath
    api(libs.molang)
    //"additionalRuntimeClasspath"(libs.molang)

    //implementation("thedarkcolour:kotlinforforge-neoforge:5.7.0:slim")
    //"additionalRuntimeClasspath"("org.jetbrains.kotlin:kotlin-stdlib:2.1.0")
    //"additionalRuntimeClasspath"("org.jetbrains.kotlin:kotlin-reflect:2.1.0")
    //"additionalRuntimeClasspath"("org.jetbrains.kotlinx:kotlinx-coroutines-core")
    //"additionalRuntimeClasspath"("org.jetbrains.kotlinx:kotlinx-serialization-core:1.7.3")
    //"additionalRuntimeClasspath"("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    modImplementation(libs.fabric.loader)
    modImplementation(libs.fabric.api)
    modApi(libs.bundles.fabric)

    compileOnly(libs.bundles.mongo)

    implementation(libs.graal)
    //"additionalRuntimeClasspath"(libs.graal)

    //compileOnly("net.fabricmc:sponge-mixin:0.15.5+mixin.0.8.7")
    //compileOnly("io.github.llamalad7:mixinextras-fabric:0.4.1")
    //api(libs.jei.api)
    //compileOnly(libs.bundles.fabric.integrations.compileOnly) {
    //    isTransitive = false
    //}

    testImplementation(libs.bundles.unitTesting)
}

tasks.named("generateRemapClasspath") {
    dependsOn(tasks.named("unzipAccessWideners"))
}