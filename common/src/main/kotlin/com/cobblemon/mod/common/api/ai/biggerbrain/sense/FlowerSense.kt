/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.ai.biggerbrain.sense

import com.cobblemon.mod.common.CobblemonMemories
import com.cobblemon.mod.common.api.ai.BehaviourConfigurationContext
import com.cobblemon.mod.common.api.ai.ExpressionOrEntityVariable
import com.cobblemon.mod.common.api.ai.asVariables
import com.cobblemon.mod.common.api.ai.biggerbrain.BiggerBrain
import com.cobblemon.mod.common.api.ai.config.sense.SenseConfig
import com.cobblemon.mod.common.api.ai.resolveInt
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.util.asExpression
import com.cobblemon.mod.common.util.distanceTo
import com.cobblemon.mod.common.util.getMemorySafely
import com.mojang.datafixers.util.Either
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.Vec3i
import net.minecraft.server.level.ServerLevel
import net.minecraft.tags.BlockTags
import net.minecraft.tags.PoiTypeTags
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.ai.village.poi.PoiManager
import net.minecraft.world.entity.ai.village.poi.PoiRecord
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.DoublePlantBlock
import net.minecraft.world.level.block.entity.BeehiveBlockEntity
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf

class FlowerSense(
    private var cooldownTicks: Int = SCAN_INTERVAL
) : Sense {
    override var ticksUntilNextExecution: Int = cooldownTicks
    override val memories = setOf(
        CobblemonMemories.NEARBY_FLOWER,
        CobblemonMemories.HAS_NECTAR,
        )

    override fun merge(other: Sense): Boolean {
        if (other is FlowerSense) {
            this.cooldownTicks = minOf(this.cooldownTicks, other.cooldownTicks)
            return true
        }
        return false
    }

    override fun tick(entity: LivingEntity, brain: BiggerBrain) {
        ticksUntilNextExecution = cooldownTicks
        val brain = entity.brain
        val world = entity.level()
        // Check curr memory
        val currPos = brain.getMemorySafely(CobblemonMemories.NEARBY_FLOWER).orElse(null)
        if (currPos != null && isFlower(entity.level().getBlockState(currPos)) && entity.distanceTo(currPos) <= 32) {
            return
        }

        val searchRadius = 5
        val centerPos = entity.blockPosition()
        var flowerPos: BlockPos? = null
        var shortestDist = Double.MAX_VALUE
        BlockPos.betweenClosedStream(
            centerPos.offset(-searchRadius, -2, -searchRadius),
            centerPos.offset(searchRadius, 2, searchRadius)
        ).forEach { pos ->
            val state = world.getBlockState(pos)
            if (isFlower(state)) {
                val distance = pos.distSqr(Vec3i(centerPos.x, centerPos.y, centerPos.z))
                if (distance < shortestDist) {
                    flowerPos = BlockPos(pos)
                    shortestDist = distance
                }
            }
        }


        if (flowerPos != null) {
            brain.setMemory(CobblemonMemories.NEARBY_FLOWER, flowerPos)
        } else {
            brain.eraseMemory(CobblemonMemories.NEARBY_FLOWER)
        }
    }

    private fun isFlower(state: BlockState): Boolean {
        // Borrowed from Bee.class
        if (state.hasProperty(BlockStateProperties.WATERLOGGED) && state.getValue(
                BlockStateProperties.WATERLOGGED
            ) as Boolean
        ) {
            return false
        } else if (state.`is`(BlockTags.FLOWERS)) {
            if (state.`is`(Blocks.SUNFLOWER)) {
                return state.getValue(DoublePlantBlock.HALF) == DoubleBlockHalf.UPPER
            } else {
                return true
            }
        } else {
            return false
        }
    }

    class Config : SenseConfig {
        var condition: ExpressionOrEntityVariable = Either.left("true".asExpression())
        var cooldownTicks: ExpressionOrEntityVariable = Either.left("40".asExpression())

        override fun getVariables(
            entity: LivingEntity,
            behaviourConfigurationContext: BehaviourConfigurationContext
        ) = listOf(cooldownTicks).asVariables()

        override fun create(entity: LivingEntity, behaviourConfigurationContext: BehaviourConfigurationContext): Sense {
            val cooldownTicks = cooldownTicks.resolveInt(behaviourConfigurationContext.runtime)
            return FlowerSense(
                cooldownTicks = cooldownTicks
            )
        }
    }

    companion object {
        const val SCAN_INTERVAL = 40
    }


}
