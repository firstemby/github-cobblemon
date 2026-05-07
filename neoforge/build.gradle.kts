/*
 *
 *  * Copyright (C) 2023 Cobblemon Contributors
 *  *
 *  * This Source Code Form is subject to the terms of the Mozilla Public
 *  * License, v. 2.0. If a copy of the MPL was not distributed with this
 *  * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 */

plugins {
    id("cobblemon.platform-conventions")
    id("cobblemon.publish-conventions")
}

loom.convertAw2At(project(":common").tasks.withType<Jar>().named("jar"), listOf("cobblemon-common.accesswidener"))

repositories {
    maven(url = "${rootProject.projectDir}/deps")
    maven(url = "https://thedarkcolour.github.io/KotlinForForge/")
    maven(url = "https://api.modrinth.com/maven")
    maven(url = "https://maven.neoforged.net/releases")
    mavenLocal()
}

dependencies {
    forgeUserdev(variantOf(libs.neoforge.api) { classifier("userdev") })
    //shadowCommon group: 'commons-io', name: 'commons-io', version: '2.6'
    //modImplementation(libs.flywheelForge)
    //include(libs.flywheelForge)
    runtimeOnly(libs.bundles.neoforge.integrations.runtimeOnly)
    runtimeOnly(libs.bundles.mongo)

    implementation(libs.neo.kotlin.forge)

    implementation(project(":common")) {
        isTransitive = false
    }
    bundle(project(path = ":common")) {
        isTransitive = false
    }
    testImplementation(project(":common"))

    listOf(
        libs.bundles.graal,
        libs.bundles.mongo
    ).forEach {
        include(it)
    }
    runtimeOnly(libs.bundles.graal)

    listOf(
        libs.molang
    ).forEach {
        runtimeOnly(it)
        include(it)
    }

}

tasks {
    shadowJar {
        exclude("architectury-common.accessWidener")
        exclude("architectury.common.json")
    }

    processResources {
        inputs.property("version", rootProject.version)
        inputs.property("minecraft_version", rootProject.property("mc_version").toString())
        inputs.property("java_version", rootProject.property("java_version").toString())

        filesMatching("META-INF/neoforge.mods.toml") {
            expand(
                "version" to rootProject.version,
                "minecraft_version" to rootProject.property("mc_version").toString(),
                "java_version" to rootProject.property("java_version").toString()
            )
        }
    }
}

tasks {
    sourcesJar {
        val depSources = project(":common").tasks.sourcesJar
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
        dependsOn(depSources)
        from(depSources.get().archiveFile.map { zipTree(it) }) {
            exclude("architectury.accessWidener")
        }
    }
}