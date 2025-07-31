/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.entity.pokemon.ai.sensors

import com.cobblemon.mod.common.CobblemonMemories
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.tags.BlockTags
import net.minecraft.world.entity.ai.sensing.Sensor
import net.minecraft.world.level.block.state.BlockState

class FlowerSensor : Sensor<PokemonEntity>(120) {
    override fun requires() = setOf(CobblemonMemories.NEARBY_FLOWERS)

    override fun doTick(world: ServerLevel, entity: PokemonEntity) {
        val brain = entity.brain

        val searchRadius = 32
        val centerPos = entity.blockPosition()
        val flowers = mutableListOf<BlockPos>()

        BlockPos.betweenClosedStream(
            centerPos.offset(-searchRadius, -2, -searchRadius),
            centerPos.offset(searchRadius, 2, searchRadius)
        ).forEach { pos ->
            val state = world.getBlockState(pos)
            if (isFlower(state) && isPathfindableTo(entity, pos)) {
                flowers += pos.immutable()
            }
        }

        if (!flowers.isEmpty()) {
            brain.setMemory(CobblemonMemories.NEARBY_FLOWERS, flowers)
        } else {
            brain.eraseMemory(CobblemonMemories.NEARBY_FLOWERS)
        }
    }

    private fun isFlower(state: BlockState): Boolean {
        return state.`is`(BlockTags.FLOWERS)
    }

    private fun isPathfindableTo(entity: PokemonEntity, pos: BlockPos): Boolean {
        val nav = entity.navigation
        val path = nav.createPath(pos, 0)
        return path != null && path.canReach()
    }
}
