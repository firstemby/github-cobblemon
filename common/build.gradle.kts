import org.jetbrains.kotlin.gradle.internal.ir.types.IdSignatureValues.result

plugins {
    kotlin("jvm") version "2.1.0"
    id ("net.neoforged.moddev") version "2.0.75"
    id("net.kyori.blossom") version "2.1.0"
    id("com.cobblemon.gradle")
    id("com.github.johnrengelman.shadow") version "8.1.1"
    id("maven-publish")
}
group = "com.cobblemon"
version = "1.6.0-SNAPSHOT"

tasks.create<Zip>("zipResources") {
    dependsOn("processResources")
    from(tasks.processResources.get().destinationDir)
    this.archiveBaseName = "mod-resources"
}

tasks.jar {
    this.archiveBaseName = "mod"
    exclude {
        it.file.absolutePath.startsWith(tasks.processResources.get().destinationDir.path)
    }
}

tasks.assemble {
    dependsOn(tasks.named("zipResources"))
}

artifacts {
    //This basically allows the zipped resources to be added as a dependency by the loader specific
    //projects. Gradle by default needs the 'default' configuration here, but it seems like MDG wants
    //'runtimeElements'
    add("archives", tasks.getByName<Zip>("zipResources"))
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
    maven(url = "../deps")
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
                    project.projectDir.parentFile.resolve("HEADER").forEachLine {
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
                property("isSnapshot", "true")
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