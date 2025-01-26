package com.cobblemon.gradle.extension

import org.gradle.api.file.FileCollection

abstract class CobblemonGradleExtension {
    var additionalAccessWideners: FileCollection? = null
    var convertAccessWideners = true

    companion object {
        const val EXTENSION_NAME = "cobblemon"
    }
}