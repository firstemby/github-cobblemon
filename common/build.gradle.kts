import dev.architectury.at.AccessChange
import dev.architectury.at.AccessTransform
import dev.architectury.at.AccessTransformSet
import dev.architectury.at.ModifierChange
import dev.architectury.at.io.AccessTransformFormats
import net.fabricmc.accesswidener.AccessWidenerReader
import net.fabricmc.accesswidener.AccessWidenerVisitor
import net.neoforged.nfrtgradle.CreateMinecraftArtifacts
import org.cadixdev.bombe.type.signature.MethodSignature
import java.io.FileWriter
import java.io.StringWriter
import java.io.Writer
import java.nio.file.Files
import java.nio.file.Path

plugins {
    kotlin("jvm") version "2.1.0"
    id ("net.neoforged.moddev") version "2.0.75"
    id("net.kyori.blossom") version "2.1.0"
}

buildscript {
    repositories {
        maven("https://maven.architectury.dev/")
        maven("https://maven.fabricmc.net/")
    }
    dependencies {
        classpath("dev.architectury:at:1.0.1")
        classpath("net.fabricmc:access-widener:2.1.0")
        classpath("net.neoforged.moddev:net.neoforged.moddev.gradle.plugin:2.0.75")
    }
}

repositories {
    maven(url = "${rootProject.projectDir}/deps")
}

dependencies {
    compileOnly(libs.bundles.mongo)
    implementation(libs.bundles.kotlin)
    api(libs.molang)
    compileOnly(libs.graal)
}

tasks.processResources {
    exclude("*.accesswidener")
}

tasks.create("convertAw") {
    inputs.file("E:\\Projects\\cobblemon\\common\\src\\main\\resources\\cobblemon-common.accesswidener")
    outputs.file(project.layout.buildDirectory.get().dir("cobblemon-working").file("accesstransformer.cfg"))
    doFirst {
        fun toAt(access: AccessWidenerReader.AccessType): AccessTransform {
            return when (access) {
                AccessWidenerReader.AccessType.ACCESSIBLE -> AccessTransform.of(AccessChange.PUBLIC)
                AccessWidenerReader.AccessType.EXTENDABLE, AccessWidenerReader.AccessType.MUTABLE -> AccessTransform.of(
                    AccessChange.PUBLIC,
                    ModifierChange.REMOVE
                )
            }
        }
        val awFile = Files.newBufferedReader(inputs.files.filter {it.toString().endsWith(".accesswidener")}.first().toPath())
        val result = AccessTransformSet.create();

        AccessWidenerReader(object : AccessWidenerVisitor {
            override fun visitClass(name: String, access: AccessWidenerReader.AccessType, transitive: Boolean) {
                result.getOrCreateClass(name).merge(toAt(access));
            }

            override fun visitMethod(owner: String, name: String, descriptor: String, access: AccessWidenerReader.AccessType, transitive: Boolean) {
                result.getOrCreateClass(owner).mergeMethod(MethodSignature.of(name, descriptor), toAt(access));
            }

            override fun visitField(owner: String, name: String, descriptor: String, access: AccessWidenerReader.AccessType, transitive: Boolean) {
                result.getOrCreateClass(owner).mergeField(name, toAt(access));
            }
        }).read(awFile)

        val outputFile = project.layout.buildDirectory.get().dir("cobblemon-working").file("accesstransformer.cfg").asFile
        var outputWriter: Writer? = null
        try {
             outputWriter = outputFile.writer()
            AccessTransformFormats.FML.write(outputWriter, result)
        }
        finally {
            outputWriter?.flush()
            outputWriter?.close()
        }
    }
}

tasks.withType(CreateMinecraftArtifacts::class.java) {
    dependsOn(tasks.named("convertAw"))
    this.accessTransformers = tasks.named("convertAw").get().outputs.files
}

neoForge {
    neoFormVersion = "1.21-20240613.152323"

    /*
    runs {
        client {
            client()
        }
        server {
            server()
        }
        data {
            data()
        }
    }
     */
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