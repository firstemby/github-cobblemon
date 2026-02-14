/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.habitats

import com.cobblemon.mod.common.api.spawning.detail.PokemonSpawnDetail
import com.cobblemon.mod.common.api.spawning.detail.SpawnDetail
import com.cobblemon.mod.common.block.habitat.HabitatBlockEntity
import com.cobblemon.mod.common.util.cobblemonResource
import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.network.codec.ByteBufCodecs
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
            cobblemonResource("activated") to ActivatedHabitatPool::class.java,
            cobblemonResource("natural") to NaturalHabitatPool::class.java
        )
    }

    lateinit var id: ResourceLocation
    var name: String = ""
    abstract val type: ResourceLocation
    abstract val spawns: List<T>

    /** Whether a pool is editable is just down to whether it's not from a datapack. */
    @Transient
    var editable: Boolean = false

    fun createSpawnDetails(habitatBlockEntity: HabitatBlockEntity): List<PokemonSpawnDetail> {
        return spawns.mapNotNull { it.createSpawnDetail(habitatBlockEntity) }
    }

//    companion object {
//        val CODEC: Codec<HabitatPool> = RecordCodecBuilder.create { instance ->
//            instance.group(
//                ResourceLocation.CODEC.fieldOf("structure").forGetter { it.structure },
//                WeightedGroup.CODEC.listOf().fieldOf("spawnPool").forGetter { it.spawnPool }
//            ).apply(instance, ::HabitatPool)
//        }
//
//        val STREAM_CODEC = ByteBufCodecs.fromCodec(CODEC)
//
//        val BLANK_HABITATPool = HabitatPool(
//            structure = cobblemonResource("blank"),
//            spawnPool = emptyList()
//        )
//    }
}