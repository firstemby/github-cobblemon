package com.cobblemon.gradle.extension.tasks

import org.gradle.api.DefaultTask
import org.gradle.api.Task
import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.OutputFiles
import org.gradle.api.tasks.TaskAction

abstract class UnzipAccessWidenersTask : DefaultTask() {
    abstract var accessWidenerZips: FileCollection
        @InputFiles get

    @OutputFiles
    val outputFiles = project.provider {
        project.files(accessWidenerZips.flatMap { zip ->
            project.zipTree(zip).files.map { OUTPUT_DIR.get().file(it.name) }
        })
    }

    @Internal
    val OUTPUT_DIR = project.layout.buildDirectory.dir("cobblemon-working/access-wideners")

    @TaskAction
    fun run() {
        project.copy {
            it.from(accessWidenerZips.map(project::zipTree))
            it.into(OUTPUT_DIR)
        }
    }
}