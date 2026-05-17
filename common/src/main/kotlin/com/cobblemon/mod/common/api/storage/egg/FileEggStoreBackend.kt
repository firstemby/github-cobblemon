/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.storage.egg

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.pokemon.Pokemon
import com.mojang.serialization.DynamicOps
import java.io.File
import java.nio.file.Path
import java.util.UUID
import net.minecraft.server.MinecraftServer
import net.minecraft.world.level.storage.LevelResource

/**
 * Base class for file-based egg store backends that use Pokemon.CODEC
 * with a specific [DynamicOps] for serialization.
 */
abstract class FileEggStoreBackend(private val fileExtension: String) : EggStoreBackend {
    protected lateinit var savePath: Path

    protected abstract val ops: DynamicOps<*>
    protected abstract fun readFromFile(file: File): Any
    protected abstract fun writeToFile(file: File, data: Any)

    override fun setup(server: MinecraftServer) {
        savePath = server.getWorldPath(LevelResource.ROOT).resolve("eggs")
        savePath.toFile().mkdirs()
    }

    @Suppress("UNCHECKED_CAST")
    override fun load(eggId: UUID): Pokemon? {
        val file = filePath(eggId)
        if (!file.exists() || file.length() == 0L) return null
        return try {
            val data = readFromFile(file)
            Pokemon.CODEC.decode(ops as DynamicOps<Any>, data).getOrThrow {
                Cobblemon.LOGGER.error("Error decoding egg $eggId: $it")
                throw UnsupportedOperationException()
            }.first
        } catch (e: Exception) {
            Cobblemon.LOGGER.error("Failed to load egg $eggId", e)
            null
        }
    }

    @Suppress("UNCHECKED_CAST")
    override fun save(eggId: UUID, pokemon: Pokemon) {
        val file = filePath(eggId)
        file.parentFile.mkdirs()
        val encoded = Pokemon.CODEC.encodeStart(ops as DynamicOps<Any>, pokemon).result().get()
        writeToFile(file, encoded)
    }

    override fun delete(eggId: UUID) {
        filePath(eggId).delete()
    }

    private fun filePath(eggId: UUID): File {
        val subfolder = eggId.toString().substring(0, 2)
        return savePath.resolve("$subfolder/$eggId.$fileExtension").toFile()
    }
}
