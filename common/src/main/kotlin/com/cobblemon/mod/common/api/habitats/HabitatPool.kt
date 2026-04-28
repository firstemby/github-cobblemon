/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.habitats

import com.cobblemon.mod.common.api.spawning.SpawnBucket
import com.cobblemon.mod.common.api.spawning.detail.PokemonSpawnDetail
import com.cobblemon.mod.common.api.spawning.position.SpawnablePosition
import com.cobblemon.mod.common.block.habitat.HabitatBlockEntity
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.resources.ResourceLocation

/**
 * A pool of [HabitatSpawn]s. This is either filled with [NaturalHabitatSpawn]s or [ActivatedHabitatSpawn]s depending
 * on whether the pool is a [NaturalHabitatPool] or an [ActivatedHabitatPool]. The pool is responsible for converting
 * its spawns into [PokemonSpawnDetail]s.
 *
 * @author Hiroku
 * @since February 13th, 2026
 */
abstract class HabitatPool<T : HabitatSpawn> {
    companion object {
        val types = mutableMapOf(
            ActivatedHabitatSpawn.TYPE to ActivatedHabitatPool::class.java,
            NaturalHabitatSpawn.TYPE to NaturalHabitatPool::class.java
        )
    }

    lateinit var id: ResourceLocation
    var name: String = ""
    abstract val type: ResourceLocation
    abstract var spawns: List<T>

    /** Whether a pool is editable is just down to whether it's not from a datapack. */
    @Transient
    var editable: Boolean = false

    open fun validate() {
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

    open fun decode(buffer: RegistryFriendlyByteBuf, buckets: List<SpawnBucket>) {
        id = buffer.readResourceLocation()
        name = buffer.readUtf()
    }
}