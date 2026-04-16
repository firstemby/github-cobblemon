/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.storage.adapter.flatfile

import com.cobblemon.mod.common.api.storage.PokemonStore
import com.cobblemon.mod.common.api.storage.StorePosition
import com.mojang.logging.LogUtils
import net.minecraft.core.RegistryAccess
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.NbtAccounter
import net.minecraft.nbt.NbtIo
import net.minecraft.util.ProblemReporter
import net.minecraft.world.level.storage.TagValueInput
import net.minecraft.world.level.storage.TagValueOutput
import java.io.File
import java.util.*

/**
 * A [OneToOneFileStoreAdapter] that can arbitrarily save a single [PokemonStore] into an [CompoundTag] with the
 * help of Minecraft's [NbtIo]. This is arguably the best persistence method for [PokemonStore]s and is absolutely
 * the most efficient [FileStoreAdapter].
 *
 * @author Hiroku
 * @since November 30th, 2021
 */
open class NBTStoreAdapter(
    rootFolder: String,
    useNestedFolders: Boolean,
    folderPerClass: Boolean,
) : OneToOneFileStoreAdapter<CompoundTag>(rootFolder, useNestedFolders, folderPerClass, "dat") {
   companion object {
       private val LOGGER = LogUtils.getLogger()
   }

    override fun <E : StorePosition, T : PokemonStore<E>> serialize(store: T, registryAccess: RegistryAccess): CompoundTag {
        ProblemReporter.ScopedCollector(ProblemReporter.RootFieldPathElement(store.uuid.toString()), LOGGER).use { reporter ->
            val output = TagValueOutput.createWithContext(reporter, registryAccess)
            store.save(output)
            return output.buildResult()
        }
    }

    override fun save(file: File, serialized: CompoundTag) = NbtIo.writeCompressed(serialized, file.toPath())
    override fun <E, T : PokemonStore<E>> load(file: File, storeClass: Class<out T>, uuid: UUID, registryAccess: RegistryAccess): T? {
        val store = try {
            storeClass.getConstructor(UUID::class.java, UUID::class.java).newInstance(uuid, uuid)
        } catch (exception: NoSuchMethodException) {
            storeClass.getConstructor(UUID::class.java).newInstance(uuid)
        }
        return try {
            val nbt = NbtIo.readCompressed(file.toPath(), NbtAccounter.unlimitedHeap())
            ProblemReporter.ScopedCollector(ProblemReporter.RootFieldPathElement(store.uuid.toString()), LOGGER).use { reporter ->
                val output = TagValueInput.create(reporter, registryAccess, nbt)
                store.load(output)
                store
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }
}
