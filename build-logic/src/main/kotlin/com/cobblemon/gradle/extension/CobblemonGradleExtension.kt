package com.cobblemon.gradle.extension

import org.gradle.api.file.FileCollection
import org.gradle.api.provider.Property

interface CobblemonGradleExtension {
    val accessWideners: Property<FileCollection>

    companion object {
        const val EXTENSION_NAME = "cobblemon"
    }
}