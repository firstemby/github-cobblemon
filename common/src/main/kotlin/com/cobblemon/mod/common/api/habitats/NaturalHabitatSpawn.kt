/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.habitats

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.api.spawning.SpawnBucket
import com.cobblemon.mod.common.api.spawning.detail.PokemonSpawnDetail
import com.cobblemon.mod.common.block.habitat.HabitatBlockEntity
import com.cobblemon.mod.common.util.DataKeys
import com.cobblemon.mod.common.util.cobblemonResource
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.RegistryFriendlyByteBuf

/**
 * A type of [HabitatSpawn] that spawns Pokémon as part of natural world spawning, and therefore
 * needs to indicate the [SpawnBucket] it will be part of.
 *
 * @author Hiroku
 * @since February 13th, 2026
 */
class NaturalHabitatSpawn : HabitatSpawn() {
    companion object {
        val TYPE = cobblemonResource("natural")
    }

    lateinit var bucket: SpawnBucket

    override fun createSpawnDetail(habitatBlockEntity: HabitatBlockEntity): PokemonSpawnDetail? {
        val spawnDetail = super.createSpawnDetail(habitatBlockEntity)
        spawnDetail?.bucket = bucket
        return spawnDetail
    }

    override fun writeToNBT(nbt: CompoundTag) {
        super.writeToNBT(nbt)
        nbt.putString(DataKeys.HABITAT_POOL_SPAWN_BUCKET, bucket.name)
    }

    override fun readFromNBT(nbt: CompoundTag) {
        super.readFromNBT(nbt)
        bucket = Cobblemon.bestSpawner.config.buckets.first { it.name == nbt.getString(DataKeys.HABITAT_POOL_SPAWN_BUCKET) }
    }

    override fun encode(buffer: RegistryFriendlyByteBuf) {
        super.encode(buffer)
        buffer.writeUtf(bucket.name)
    }

    override fun decode(
        buffer: RegistryFriendlyByteBuf,
        buckets: List<SpawnBucket>
    ) {
        super.decode(buffer, buckets)
        val bucketName = buffer.readUtf()
        bucket = buckets.first { it.name == bucketName }
    }
}