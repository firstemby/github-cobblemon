/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.habitats.spawningstyle

import com.cobblemon.mod.common.api.habitats.NaturalHabitatPool
import com.cobblemon.mod.common.api.habitats.NaturalHabitatSpawn
import com.cobblemon.mod.common.api.habitats.spawningstyle.HabitatSpawningStyle.Companion.readPoolFromNBT
import com.cobblemon.mod.common.api.spawning.detail.SpawnDetail
import com.cobblemon.mod.common.block.habitat.HabitatBlockEntity
import com.cobblemon.mod.common.util.DataKeys
import net.minecraft.nbt.CompoundTag
import net.minecraft.resources.ResourceLocation

/**
 * A habitat spawning style defined by tapping into regular spawning methods. This works by adding to the spawn list
 * for spawning that's occurring nearby. When [replaceSpawns] is set to true, the original spawns will be
 * removed completely in favour of the spawns provided in this habitat's [pool].
 *
 * The spawn details are generated from the [pool] every time there is some change to the habitat block's settings,
 * then those spawn details are used for spawning Pokémon when world spawning is occurring nearby.
 *
 * Whether or not this interactions with overworld or fishing spawns is determined by whether the habitat pool has
 * overworld or fishing spawns respectively.
 *
 * @author Hiroku
 * @since February 22nd, 2026
 */
class NaturalHabitatSpawning(val habitatBlockEntity: HabitatBlockEntity) : HabitatSpawningStyle {
    @Transient
    override val type: ResourceLocation = NaturalHabitatSpawn.TYPE

    var pool: NaturalHabitatPool = NaturalHabitatPool.default()
    var replaceSpawns = false
    var rangeOfInfluence = 16

    @Transient
    var affectsFishing = false
    @Transient
    var affectsOverworld = false
    @Transient
    var spawnDetails: List<SpawnDetail> = listOf()

    fun generateSpawnDetails() {
        spawnDetails = pool.createSpawnDetails(habitatBlockEntity = habitatBlockEntity)
        affectsFishing = spawnDetails.any { it.spawnablePositionType.name == "fishing" }
        affectsOverworld = spawnDetails.any { it.spawnablePositionType.name != "fishing" }
    }

    override fun writeToNBT(nbt: CompoundTag) {
        nbt.putBoolean(DataKeys.HABITAT_NATURAL_REPLACE_SPAWNS, replaceSpawns)
        nbt.putInt(DataKeys.HABITAT_NATURAL_RANGE_OF_INFLUENCE, rangeOfInfluence)

        val poolNBT = CompoundTag()
        writePoolToNBT(pool, poolNBT)
        nbt.put(DataKeys.HABITAT_POOL, poolNBT)
    }

    override fun readFromNBT(nbt: CompoundTag) {
        replaceSpawns = nbt.getBoolean(DataKeys.HABITAT_NATURAL_REPLACE_SPAWNS)
        rangeOfInfluence = nbt.getInt(DataKeys.HABITAT_NATURAL_RANGE_OF_INFLUENCE)

        pool = readPoolFromNBT(
            nbt = nbt.getCompound(DataKeys.HABITAT_POOL),
            poolInitializer = ::NaturalHabitatPool,
            spawnInitializer = ::NaturalHabitatSpawn,
            defaultPool = NaturalHabitatPool::default
        )
    }
}