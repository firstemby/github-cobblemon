/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.render.structure

import com.cobblemon.mod.common.Cobblemon
import java.io.File
import java.io.InputStream
import java.net.JarURLConnection

/**
 * Classpath enumeration helpers shared by the structure-preview loaders. Bundled mod data
 * (`data/<ns>/...`) lives either in an exploded resources directory (dev) or inside a jar (prod);
 * both layouts are walked here so new files are discovered automatically with no code changes.
 */
object StructurePreviewResources {

    /**
     * Lists every classpath resource whose name starts with [dirPrefix] (no leading slash, e.g.
     * `data/cobblemon/structure`) and ends with [suffix] (e.g. `.nbt`). Returns full resource
     * names suitable for [openStream]. Safe: returns whatever it can find, logging and skipping
     * roots it can't read.
     */
    fun listResources(dirPrefix: String, suffix: String): List<String> {
        val result = LinkedHashSet<String>()
        val classLoader = StructurePreviewResources::class.java.classLoader
        try {
            val roots = classLoader.getResources(dirPrefix)
            while (roots.hasMoreElements()) {
                val url = roots.nextElement()
                try {
                    when (url.protocol) {
                        "file" -> {
                            val base = File(url.toURI())
                            base.walkTopDown()
                                .filter { it.isFile && it.name.endsWith(suffix) }
                                .forEach { file ->
                                    val normalized = file.path.replace('\\', '/')
                                    val idx = normalized.indexOf(dirPrefix)
                                    if (idx >= 0) result.add(normalized.substring(idx))
                                }
                        }
                        "jar" -> {
                            val connection = url.openConnection() as JarURLConnection
                            val entries = connection.jarFile.entries()
                            while (entries.hasMoreElements()) {
                                val name = entries.nextElement().name
                                if (name.startsWith(dirPrefix) && name.endsWith(suffix)) result.add(name)
                            }
                        }
                    }
                } catch (e: Exception) {
                    Cobblemon.LOGGER.warn("Failed to enumerate structure-preview resources under {}", url, e)
                }
            }
        } catch (e: Exception) {
            Cobblemon.LOGGER.warn("Failed to list structure-preview resources for {}", dirPrefix, e)
        }
        return result.toList()
    }

    /** Opens a classpath resource by full name (with or without a leading slash). */
    fun openStream(resourceName: String): InputStream? {
        val path = if (resourceName.startsWith("/")) resourceName else "/$resourceName"
        return StructurePreviewResources::class.java.getResourceAsStream(path)
    }
}
