/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.habitats.spawningstyle

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.api.habitats.HabitatPool
import com.cobblemon.mod.common.api.habitats.HabitatPools
import com.cobblemon.mod.common.api.habitats.HabitatSpawn
import com.cobblemon.mod.common.util.DataKeys
import com.cobblemon.mod.common.util.asIdentifierDefaultingNamespace
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.nbt.Tag
import net.minecraft.resources.ResourceLocation

/**
 * Holds habitat block specific details about the methodology behind how the habitat block will spawn Pokémon. This is
 * either an [ActivatedHabitatSpawning] or a [NaturalHabitatSpawning].
 *
 * @see ActivatedHabitatSpawning
 * @see NaturalHabitatSpawning
 * @author Hiroku
 * @since February 14th, 2026
 */
sealed interface HabitatSpawningStyle {
    val type: ResourceLocation

    fun writeToNBT(nbt: CompoundTag)
    fun readFromNBT(nbt: CompoundTag)

    fun writePoolToNBT(pool: HabitatPool<*>, nbt: CompoundTag) {
        nbt.putString(DataKeys.HABITAT_POOL_ID, pool.id.toString())
        if (pool.id.path.startsWith("custom__")) {
            nbt.putString(DataKeys.HABITAT_POOL_NAME, pool.name)
            val spawnsListNBT = ListTag()
            for (spawn in pool.spawns) {
                val spawnTag = CompoundTag()
                spawn.writeToNBT(spawnTag)
                spawnsListNBT.add(spawnTag)
            }
            nbt.put(DataKeys.HABITAT_POOL_SPAWNS, spawnsListNBT)
        }
    }
    
    companion object {
        fun <T : HabitatSpawn, P : HabitatPool<T>> readPoolFromNBT(nbt: CompoundTag, poolInitializer: () -> P, spawnInitializer: () -> T, defaultPool: () -> P): P {
            var pool: P
            val poolId = nbt.getString(DataKeys.HABITAT_POOL_ID).asIdentifierDefaultingNamespace()
            if (nbt.contains(DataKeys.HABITAT_POOL_NAME)) {
                // It's one that was stored on the block
                pool = poolInitializer()
                pool.id = poolId
                pool.name = nbt.getString(DataKeys.HABITAT_POOL_NAME)
                val spawnsListNBT = nbt.getList(DataKeys.HABITAT_POOL_SPAWNS, Tag.TAG_COMPOUND.toInt())
                val spawns = mutableListOf<T>()
                spawnsListNBT.forEach { spawnTag ->
                    try {
                        val spawn = spawnInitializer().apply { readFromNBT(spawnTag as CompoundTag) }
                        spawns.add(spawn)
                    } catch (_: Exception) {
                        // If there's an error reading a spawn, skip it and log the error.
                        Cobblemon.LOGGER.error("Error reading activated habitat spawn from NBT for habitat block, skipping.")
                    }
                }
                pool.spawns = spawns
            } else {
                pool = HabitatPools.habitatPoolsById[poolId] as? P ?: defaultPool()
            }
            
            return pool
        }
    }
}