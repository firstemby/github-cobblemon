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
import com.cobblemon.mod.common.block.habitat.HabitatBlockEntity
import com.cobblemon.mod.common.util.cobblemonResource

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
}