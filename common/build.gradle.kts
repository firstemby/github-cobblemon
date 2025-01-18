import org.gradle.internal.impldep.org.junit.experimental.categories.Categories.CategoryFilter.exclude

plugins {
    kotlin("jvm") version "2.1.0"
    id ("net.neoforged.moddev") version "2.0.75"
    id("net.kyori.blossom") version "2.1.0"
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
    gradlePluginPortal()
    maven("https://maven.architectury.dev/")
    maven("https://maven.fabricmc.net/")
    maven("https://maven.blamejared.com/")
    maven("https://api.modrinth.com/maven")
    maven(url = "${rootProject.projectDir}/deps")
}

cobblemon {
    accessWideners = provider {
        return@provider sourceSets.main.get().resources.filter() { file ->
            return@filter file.extension.equals("accesswidener")
        }
    }
}

dependencies {
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
}

tasks.processResources {
    exclude("*.accesswidener")
}

neoForge {
    neoFormVersion = "1.21.1-20240808.144430"
    interfaceInjectionData.from("src/main/resources/interfaces.json")
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
                property("version", project.version as String)
                property("isSnapshot", if(rootProject.property("snapshot") == "true") "true" else "false")
                /*
                property("gitCommit", versioning.info.commit)
                property("branch", versioning.info.branch)
                System.getProperty("buildNumber")?.let { property("buildNumber", it) }
                property("timestamp", OffsetDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ofPattern("MM/dd/yyyy hh:mm:ss")) + " UTC")
                 */
            }
        }
    }
}