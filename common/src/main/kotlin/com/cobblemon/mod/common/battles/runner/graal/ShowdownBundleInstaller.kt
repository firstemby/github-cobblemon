/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.battles.runner.graal

import com.cobblemon.mod.common.util.FileUtils
import java.io.IOException
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path

internal object ShowdownBundleInstaller {
    fun install(
        directory: Path,
        zip: InputStream,
        metadata: InputStream,
        move: (Path, Path) -> Unit = { source, target -> Files.move(source, target) }
    ): Path? {
        zip.use { zipInput ->
            metadata.use { metadataInput ->
                val target = directory.toAbsolutePath().normalize()
                require(target.fileName.toString() == "showdown")
                val staging = Files.createTempDirectory(target.parent, "showdown-install-")
                var installFailure: Exception? = null
                try {
                    val archive = staging.resolve("showdown.zip")
                    Files.copy(zipInput, archive)
                    FileUtils.unzipFile(archive, staging)
                    check(Files.isRegularFile(staging.resolve("index.js"))) { "Showdown bundle has no entry point" }
                    Files.copy(metadataInput, staging.resolve("showdown.json"))
                    Files.delete(archive)
                    var backup: Path? = null
                    if (Files.exists(target)) {
                        val backupRoot = Files.createTempDirectory(target.parent, "showdown-backup-")
                        val backupTarget = backupRoot.resolve("showdown")
                        try {
                            move(target, backupTarget)
                            backup = backupTarget
                        } catch (error: Exception) {
                            try {
                                Files.deleteIfExists(backupRoot)
                            } catch (cleanupError: Exception) {
                                error.addSuppressed(cleanupError)
                            }
                            throw error
                        }
                    }
                    try {
                        move(staging, target)
                    } catch (publishError: Exception) {
                        if (backup != null) {
                            try {
                                move(backup, target)
                                Files.deleteIfExists(backup.parent)
                            } catch (restoreError: Exception) {
                                publishError.addSuppressed(restoreError)
                            }
                        }
                        throw publishError
                    }
                    return backup
                } catch (error: Exception) {
                    installFailure = error
                    throw error
                } finally {
                    try {
                        if (Files.exists(staging) && !staging.toFile().deleteRecursively()) {
                            throw IOException("Could not remove Showdown installation staging directory")
                        }
                    } catch (cleanupError: Exception) {
                        val primary = installFailure ?: throw cleanupError
                        primary.addSuppressed(cleanupError)
                    }
                }
            }
        }
    }
}
