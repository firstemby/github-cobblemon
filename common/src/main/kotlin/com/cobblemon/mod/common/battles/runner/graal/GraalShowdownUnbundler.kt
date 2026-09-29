/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.battles.runner.graal

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.util.fromJson
import com.google.gson.GsonBuilder
import java.io.File
import java.io.FileInputStream
import java.io.InputStreamReader

/**
 * Unbundles a zipped Showdown file found within {@code resources/data/showdown.zip} for use within the GraalShowdownService.
 * This will do a metadata check prior to unbundling and stop if there is already an unbundled showdown package
 * of the same version.
 *
 * @since  February 27, 2023
 * @author Deltric
 */
class GraalShowdownUnbundler {

    private val gson = GsonBuilder()
        .disableHtmlEscaping()
        .create()

    fun attemptUnbundle() {
        val showdownDir = File("showdown")
        val metadata = loadShowdownMetadata()

        if (showdownDir.exists() && !Cobblemon.config.autoUpdateShowdown) return
        checkNotNull(metadata) { "Missing Showdown bundle metadata" }
        val currentFile = File(showdownDir, "showdown.json")
        val current = if (currentFile.exists()) readShowdownMetadata(currentFile) else null
        if (current?.showdownVersion == metadata.showdownVersion) return
        val zip = checkNotNull(javaClass.getResourceAsStream("/data/${Cobblemon.MODID}/showdown.zip"))
        val backup = zip.use { zipInput ->
            val metadataStream = checkNotNull(javaClass.getResourceAsStream("/data/${Cobblemon.MODID}/showdown.json"))
            metadataStream.use { metadataInput ->
                ShowdownBundleInstaller.install(showdownDir.toPath(), zipInput, metadataInput)
            }
        }
        Cobblemon.LOGGER.info("Installed Showdown version {}; previous installation: {}", metadata.showdownVersion, backup)
    }

    private fun loadShowdownMetadata() : ShowdownMetadata? {
        try {
            val inputStream = javaClass.getResourceAsStream("/data/${Cobblemon.MODID}/showdown.json")!!
            return InputStreamReader(inputStream).use { gson.fromJson<ShowdownMetadata>(it) }
        } catch (exception: Exception) {
            exception.printStackTrace()
        }
        return null
    }

    private fun readShowdownMetadata(target: File) : ShowdownMetadata? {
        try {
            InputStreamReader(FileInputStream(target)).use {
                return gson.fromJson<ShowdownMetadata>(it)
            }
        } catch (exception: Exception) {
            exception.printStackTrace()
            return null
        }
    }

    private data class ShowdownMetadata(val showdownVersion: Double)

}
