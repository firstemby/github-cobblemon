package com.cobblemon.gradle.extension.tasks

import dev.architectury.at.AccessChange
import dev.architectury.at.AccessTransform
import dev.architectury.at.AccessTransformSet
import dev.architectury.at.ModifierChange
import dev.architectury.at.io.AccessTransformFormats
import net.fabricmc.accesswidener.AccessWidenerReader
import net.fabricmc.accesswidener.AccessWidenerVisitor
import org.cadixdev.bombe.type.signature.MethodSignature
import org.gradle.api.DefaultTask
import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputFiles
import org.gradle.api.tasks.TaskAction
import java.io.Writer
import java.nio.file.Files

abstract class ConvertAccessWidenersTask : DefaultTask() {
    abstract var accessWideners: FileCollection
        @InputFiles get

    @Internal
    val accessTransformerDir = project.layout.buildDirectory.dir("access-transformers")

    @OutputFiles
    val accessTransformers = project.files ({
        accessWideners.files.map {
            accessTransformerDir.get().file(it.name.replace("accesswidener", "accesstransformer.cfg"))
        }
    })

    @TaskAction
    fun convertAccessWideners() {
        accessWideners.files.forEach { file ->
            val awFile = Files.newBufferedReader(file.toPath())
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

            val outputFile = accessTransformerDir.get().file(
                file.name.replace("accesswidener", "accesstransformer.cfg")
            )
            var outputWriter: Writer? = null
            try {
                outputWriter = outputFile.asFile.writer()
                AccessTransformFormats.FML.write(outputWriter, result)
            }
            finally {
                outputWriter?.flush()
                outputWriter?.close()
            }
        }

    }

    private fun toAt(access: AccessWidenerReader.AccessType): AccessTransform {
        return when (access) {
            AccessWidenerReader.AccessType.ACCESSIBLE -> AccessTransform.of(AccessChange.PUBLIC)
            AccessWidenerReader.AccessType.EXTENDABLE, AccessWidenerReader.AccessType.MUTABLE -> AccessTransform.of(
                AccessChange.PUBLIC,
                ModifierChange.REMOVE
            )
        }
    }
    companion object {
        const val NAME = "convertAccessWideners"
    }
}