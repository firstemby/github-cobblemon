
import utilities.isSnapshot
import utilities.version
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

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
    id("cobblemon.base-conventions")
    id("cobblemon.publish-conventions")

    id("net.kyori.blossom")
    id("org.jetbrains.gradle.plugin.idea-ext")
}

architectury {
    common("neoforge", "fabric")
}

repositories {
    maven(url = "${rootProject.projectDir}/deps")
    maven(url = "https://api.modrinth.com/maven")
    maven(url = "https://maven.neoforged.net/releases")
    maven(url = "https://maven.gegy.dev")
    mavenLocal()
}

dependencies {
    implementation(libs.bundles.kotlin)
    modImplementation(libs.fabric.loader)
    modApi(libs.molang)

    // Integrations
    compileOnlyApi(libs.jei.api)
    modCompileOnly(libs.bundles.common.integrations.compileOnly) {
        isTransitive = false
    }
    // LambDynamicLights is handled differently because we need the Mojang-mappings version of it.
    modCompileOnly(libs.lambDynamicLights) {
        capabilities {
            requireCapability("dev.lambdaurora.lambdynamiclights:api-mojmap")
        }
    }
    // PhotonRT (our local fork of the Photonic Iris raytracing mod) exposes a dynamic-light injection API
    // we call from the photonic compat. Consume its published Mojang-mapped API variant from mavenLocal —
    // it's engine-free (no Iris/Sodium needed on this classpath). Plain compileOnly (NOT modCompileOnly):
    // the variant is already Mojang-mapped, so Loom would wrongly re-remap a modCompileOnly. The running
    // mod is the loose remapped jar in runClient/mods. Refresh via PhotonRT's `gradlew publishToMavenLocal`.
    compileOnly("dev.plasteredcrab:photonrt:0.1.0+1.21.1") {
        capabilities { requireCapability("dev.plasteredcrab.photonrt:api-mojmap") }
        isTransitive = false
    }
    // Flywheel has no common dep so just pick one and don't use any platform specific code in common
    // modCompileOnly(libs.flywheelFabric)

    // Showdown
    modCompileOnly(libs.graal.core)

    // Data Storage
    modCompileOnly(libs.bundles.mongo)

    // Unit Testing
    testImplementation(libs.bundles.unitTesting)
}

tasks.withType<Test> {
    useJUnitPlatform()
    testLogging {
        setEvents(listOf("failed"))
        setExceptionFormat("full")
    }
}

tasks.processResources {
    exclude("data/**/README.md")
}

sourceSets {
    main {
        blossom {
            kotlinSources {
                fun generateLicenseHeader() : String {
                    val builder = StringBuilder()
                    builder.append("/*\n")
                    rootProject.file("HEADER").forEachLine {
                        if(it.isEmpty()) {
                            builder.append(" *").append("\n")
                        } else {
                            builder.append(" * ").append(it).append("\n")
                        }
                    }

                    return builder.append(" */").append("\n").toString()
                }

                property("license", generateLicenseHeader())
                property("modid", "cobblemon")
                property("version", project.version())
                property("isSnapshot", if(rootProject.isSnapshot()) "true" else "false")
                property("gitCommit", versioning.info.commit)
                property("branch", versioning.info.branch)
                System.getProperty("buildNumber")?.let { property("buildNumber", it) }
                property("timestamp", OffsetDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ofPattern("MM/dd/yyyy hh:mm:ss")) + " UTC")
            }
        }
    }
}
