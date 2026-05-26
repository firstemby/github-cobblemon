/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.habitats

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.api.spawning.detail.PokemonSpawnDetail
import com.cobblemon.mod.common.api.spawning.position.SpawnablePosition
import com.cobblemon.mod.common.block.habitat.HabitatBlockEntity
import com.cobblemon.mod.common.util.DataKeys
import com.cobblemon.mod.common.util.asIdentifierDefaultingNamespace
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.nbt.Tag
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.resources.ResourceLocation

/**
 * A pool of [HabitatSpawn]s. This is filled with [HabitatSpawn]s. The pool is responsible for converting
 * its spawns into [PokemonSpawnDetail]s.
 *
 * @author Hiroku
 * @since February 13th, 2026
 */
class HabitatPool {
    companion object {
        fun default() = HabitatPool().apply {
            id = ResourceLocation("cobblemon", "custom__default_pool")
            name = "Custom"
        }

        fun writePoolToNBT(pool: HabitatPool, nbt: CompoundTag) {
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

        fun readPoolFromNBT(nbt: CompoundTag): HabitatPool {
            val poolId = nbt.getString(DataKeys.HABITAT_POOL_ID).asIdentifierDefaultingNamespace()
            if (nbt.contains(DataKeys.HABITAT_POOL_NAME)) {
                // It's one that was stored on the block
                val pool = HabitatPool()
                pool.id = poolId
                pool.name = nbt.getString(DataKeys.HABITAT_POOL_NAME)
                val spawnsListNBT = nbt.getList(DataKeys.HABITAT_POOL_SPAWNS, Tag.TAG_COMPOUND.toInt())
                val spawns = mutableListOf<HabitatSpawn>()
                spawnsListNBT.forEach { spawnTag ->
                    try {
                        val spawn = HabitatSpawn().apply { readFromNBT(spawnTag as CompoundTag) }
                        spawns.add(spawn)
                    } catch (_: Exception) {
                        // If there's an error reading a spawn, skip it and log the error.
                        Cobblemon.LOGGER.error("Error reading activated habitat spawn from NBT for habitat block, skipping.")
                    }
                }
                pool.spawns = spawns
                return pool
            } else {
                return HabitatPools.habitatPoolsById[poolId] ?: HabitatPool.default()
            }
        }
    }

    lateinit var id: ResourceLocation
    var name: String = ""
    var spawns: List<HabitatSpawn> = listOf()

    /** Whether a pool is editable is just down to whether it's not from a datapack. */
    @Transient
    var editable: Boolean = false

    fun validate() {
        for (spawn in spawns) {
            if (SpawnablePosition.getByName(spawn.spawnablePositionType) == null) {
                throw IllegalArgumentException("$id mentions non-existent spawnable position type ${spawn.spawnablePositionType} on spawn for ${spawn.species}")
            }
        }
    }

    fun createSpawnDetails(habitatBlockEntity: HabitatBlockEntity): List<PokemonSpawnDetail> {
        return spawns.mapNotNull { it.createSpawnDetail(habitatBlockEntity) }
    }

    fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeResourceLocation(id)
        buffer.writeUtf(name)
        buffer.writeCollection(spawns) { _, it -> it.encode(buffer) }
    }

    fun decode(buffer: RegistryFriendlyByteBuf) {
        id = buffer.readResourceLocation()
        name = buffer.readUtf()
        spawns = buffer.readList { HabitatSpawn().apply { decode(buffer) } }
    }
}