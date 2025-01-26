package com.cobblemon.gradle

import com.cobblemon.gradle.extension.CobblemonGradleExtension
import com.cobblemon.gradle.extension.tasks.ConvertAccessWidenersTask
import com.cobblemon.gradle.extension.tasks.UnzipAccessWidenersTask
import net.neoforged.nfrtgradle.CreateMinecraftArtifacts
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.attributes.LibraryElements
import org.gradle.language.jvm.tasks.ProcessResources

class CobblemonGradle : Plugin<Project> {
    override fun apply(project: Project) {
        val cobblemonExtension = project.extensions.create(
            CobblemonGradleExtension.EXTENSION_NAME,
            CobblemonGradleExtension::class.java
        )

        //This exists because all of
        //  - com.cobblemon:mod
        //  - com.cobblemon:mod-resources
        //  - com.cobblemon:accesswidener
        //technically point to the same dependency (the :common project). To pick the proper artifact from that project
        //we set attributes in that project and check for them here.
        val accessWideners = project.configurations.register("accessWidener") { configuration ->
            configuration.isCanBeConsumed = false
            configuration.isCanBeResolved = true
            configuration.isCanBeDeclared = true
            configuration.attributes {
                it.attribute(
                    LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE,
                    project.objects.named(LibraryElements::class.java, LibraryElements.OBJECTS)
                )
            }
        }
        val includeRootResources = project.configurations.register("includeRootResources") { configuration ->
            configuration.isCanBeConsumed = false
            configuration.isCanBeResolved = true
            configuration.isCanBeDeclared = true
            configuration.attributes {
                it.attribute(
                    LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE,
                    project.objects.named(LibraryElements::class.java, LibraryElements.RESOURCES)
                )
            }
        }

        val unzipAwTask = project.tasks.register("unzipAccessWideners", UnzipAccessWidenersTask::class.java) {
            it.accessWidenerZips = accessWideners.get()
        }


        val convertAwTask = project.tasks.register(ConvertAccessWidenersTask.NAME, ConvertAccessWidenersTask::class.java) {
            it.enabled = cobblemonExtension.convertAccessWideners
            it.dependsOn(unzipAwTask)
            it.accessWideners = project.files(unzipAwTask).plus(project.files(cobblemonExtension.additionalAccessWideners))
        }

        //Add root resources and access wideners to output jar
        project.tasks.named("processResources", ProcessResources::class.java) { task ->
            task.dependsOn(includeRootResources)
            if (cobblemonExtension.convertAccessWideners) {
                task.dependsOn(convertAwTask)
                task.from(convertAwTask)
            }
            else {
                task.dependsOn(unzipAwTask)
                task.from(unzipAwTask)
            }
            task.doLast {
                project.copy {
                    it.from(project.provider {
                        includeRootResources.get().resolve().map { project.zipTree(it) }
                    })
                    it.into(task.destinationDir)
                }
            }
        }

        project.tasks.withType(CreateMinecraftArtifacts::class.java) {
            it.dependsOn(convertAwTask)
            it.accessTransformers.from(convertAwTask)
        }
    }
}