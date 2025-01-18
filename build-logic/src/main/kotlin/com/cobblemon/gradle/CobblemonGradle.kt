package com.cobblemon.gradle

import com.cobblemon.gradle.extension.CobblemonGradleExtension
import com.cobblemon.gradle.extension.tasks.ConvertAccessWidenersTask
import net.neoforged.nfrtgradle.CreateMinecraftArtifacts
import org.gradle.api.Plugin
import org.gradle.api.Project

class CobblemonGradle : Plugin<Project> {
    override fun apply(project: Project) {
        val extension = project.extensions.create(
            CobblemonGradleExtension.EXTENSION_NAME,
            CobblemonGradleExtension::class.java
        )
        project.tasks.register(ConvertAccessWidenersTask.NAME, ConvertAccessWidenersTask::class.java) {
            it.accessWideners = extension.accessWideners.get()
        }
        project.tasks.withType(CreateMinecraftArtifacts::class.java) { createArchiveTask ->
            createArchiveTask.dependsOn(project.tasks.named(ConvertAccessWidenersTask.NAME))
            createArchiveTask.accessTransformers.from(project.tasks.named(ConvertAccessWidenersTask.NAME).get().outputs.files)
        }
    }
}