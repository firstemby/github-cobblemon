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
import com.cobblemon.mod.common.util.getMemorySafely
import com.mojang.datafixers.util.Either
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.CaveVines
import net.minecraft.world.level.block.SweetBerryBushBlock
import net.minecraft.world.level.block.state.BlockState
import java.util.Optional

class SweetBerryBushSense(
    private var cooldownTicks: Int = SCAN_INTERVAL
) : Sense {
    override var ticksUntilNextExecution: Int = SCAN_INTERVAL
    override val memories = setOf(
        CobblemonMemories.NEARBY_SWEET_BERRY_BUSH,
        CobblemonMemories.DISABLE_WALK_TO_BERRY_BUSH,
    )

    override fun merge(other: Sense): Boolean {
        if (other is SweetBerryBushSense) {
            this.cooldownTicks = minOf(this.cooldownTicks, other.cooldownTicks)
            return true
        }
        return false
    }

    override fun tick(entity: LivingEntity, brain: BiggerBrain) {
        ticksUntilNextExecution = cooldownTicks
        if (entity !is PokemonEntity) return
        val world = entity.level() as? ServerLevel ?: return

        entity.brain.getMemorySafely(CobblemonMemories.DISABLE_WALK_TO_BERRY_BUSH).orElse(false) ?: return

        val nearestBush = findNearestBush(world, entity.blockPosition())

        if (!nearestBush.isEmpty) {
            entity.brain.setMemory(CobblemonMemories.NEARBY_SWEET_BERRY_BUSH, nearestBush)
        } else {
            entity.brain.eraseMemory(CobblemonMemories.NEARBY_SWEET_BERRY_BUSH)
        }
    }

    private fun findNearestBush(level: ServerLevel, origin: BlockPos): Optional<BlockPos> {
        var closest: BlockPos? = null
        var closestDistSq = Double.MAX_VALUE

        for (pos in BlockPos.betweenClosed(origin.offset(-SCAN_RADIUS_HORIZONTAL, 1 - SCAN_RADIUS_VERTICAL, -SCAN_RADIUS_HORIZONTAL),
            origin.offset(SCAN_RADIUS_HORIZONTAL, SCAN_RADIUS_VERTICAL - 1, SCAN_RADIUS_HORIZONTAL))) {
            val state: BlockState = level.getBlockState(pos)
            if ((state.`is`(Blocks.SWEET_BERRY_BUSH) && state.getValue(SweetBerryBushBlock.AGE) >= 2)
                || (CaveVines.hasGlowBerries(state))) {
                val distSq = pos.distSqr(origin)
                if (distSq < closestDistSq) {
                    closest = pos.immutable()
                    closestDistSq = distSq
                }
            }
        }

        return Optional.ofNullable(closest)
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
            return SweetBerryBushSense(
//                condition = condition.asSimplifiedExpression(entity),
                cooldownTicks = cooldownTicks
            )
        }
    }

    companion object {
        const val SCAN_RADIUS_HORIZONTAL = 10
        const val SCAN_RADIUS_VERTICAL = 1
        const val SCAN_INTERVAL = 40
    }


}
