/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.spawning.prospecting

import com.cobblemon.mod.common.api.spawning.influence.WorldSlicedSpawningInfluence
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.block.state.BlockState
import com.cobblemon.mod.common.api.spawning.WorldSlice
import com.cobblemon.mod.common.api.spawning.spawner.Spawner
import com.cobblemon.mod.common.api.spawning.spawner.SpawningArea

/**
 * Prospects for [WorldSlicedSpawningInfluence] in a world at a given position. Occurs as part of a
 * [SpawningProspector] scanning a part of the world while formulating a [WorldSlice].
 *
 * @author Hiroku
 * @since March 9th, 2025
 */
interface SpawningInfluenceProspector {
    companion object {
        @JvmStatic
        val prospectors = mutableSetOf<SpawningInfluenceProspector>(
            LureCakeProspector,
            SaccharineHoneyLogProspector,
            IncenseSweetProspector
        )
    }

    fun prospect(spawner: Spawner, area: SpawningArea) : MutableList<WorldSlicedSpawningInfluence>

    fun prospectBlock(world: ServerLevel, pos: BlockPos, blockState: BlockState): WorldSlicedSpawningInfluence?
}