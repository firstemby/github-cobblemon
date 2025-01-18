import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import utilities.isSnapshot
import utilities.version

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
    alias(libs.plugins.moddev)

    id("cobblemon.base-conventions")
    id("cobblemon.publish-conventions")

    id("net.kyori.blossom")
    id("org.jetbrains.gradle.plugin.idea-ext")
    id("net.nemerosa.versioning") version "3.1.0"
}

neoForge {
    neoFormVersion = "1.21.1-20240808.144430"
}

repositories {
    maven(url = "${rootProject.projectDir}/deps")
    maven(url = "https://api.modrinth.com/maven")
    maven(url = "https://maven.fabricmc.net/")
    maven(url = "https://maven.neoforged.net/releases")
    mavenLocal()
}

dependencies {
    implementation(libs.bundles.kotlin)
    implementation(libs.bundles.mixins)

    api(libs.molang)
    compileOnly(libs.graal)
    compileOnly(libs.bundles.mongo)

    compileOnlyApi(libs.jei.api)
//    compileOnly(libs.bundles.fabric.integrations.compileOnly) {
//        isTransitive = false
//    }

    testImplementation(libs.bundles.unitTesting)
    testRuntimeOnly(libs.junit.fabric)

//    // Integrations
//    modCompileOnly(libs.bundles.fabric.integrations.compileOnly) {
//        isTransitive = false
//    }
}

tasks.withType<Test> {
    useJUnitPlatform()
    testLogging {
        setEvents(listOf("failed"))
        setExceptionFormat("full")
    }
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

// IDEA no longer automatically downloads sources/javadoc jars for dependencies, so we need to explicitly enable the behavior.
idea {
    module.isDownloadJavadoc = true
    module.isDownloadSources = true
}