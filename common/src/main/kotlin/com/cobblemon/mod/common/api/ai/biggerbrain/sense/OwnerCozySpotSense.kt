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
import com.cobblemon.mod.common.api.tags.CobblemonBlockTags
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.util.asExpression
import com.mojang.datafixers.util.Either
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.ai.memory.MemoryModuleType

/**
 * Populates [CobblemonMemories.NEARBY_OWNER_COZY_SPOT] with the nearest `#cobblemon:cozy_spots`
 * block to the Pokémon's owner, and counts ticks in [CobblemonMemories.OWNER_NEAR_COZY_SPOT_TICKS]
 * while the owner continuously lingers near that spot.
 *
 * @author gisketch
 * @since April 24th, 2026
 */
class OwnerCozySpotSense(
    var cooldownTicks: Int = DEFAULT_COOLDOWN,
    var ownerRadius: Int = DEFAULT_OWNER_RADIUS,
    var verticalRadius: Int = DEFAULT_VERTICAL_RADIUS
) : Sense {
    override var ticksUntilNextExecution: Int = cooldownTicks
    override val memories = setOf<MemoryModuleType<*>>(
        CobblemonMemories.NEARBY_OWNER_COZY_SPOT,
        CobblemonMemories.OWNER_NEAR_COZY_SPOT_TICKS
    )

    override fun merge(other: Sense): Boolean {
        if (other !is OwnerCozySpotSense) return false
        this.cooldownTicks = minOf(this.cooldownTicks, other.cooldownTicks)
        this.ownerRadius = maxOf(this.ownerRadius, other.ownerRadius)
        this.verticalRadius = maxOf(this.verticalRadius, other.verticalRadius)
        return true
    }

    override fun tick(entity: LivingEntity, brain: BiggerBrain) {
        ticksUntilNextExecution = cooldownTicks
        if (entity !is PokemonEntity) return
        val level = entity.level() as? ServerLevel ?: return clear(brain)
        val owner = entity.owner ?: return clear(brain)
        if (owner.level() != level) return clear(brain)

        val currentSpot = brain.getMemory(CobblemonMemories.NEARBY_OWNER_COZY_SPOT)
        val ownerPos = owner.blockPosition()
        val isSleeping = brain.getMemory(CobblemonMemories.POKEMON_SLEEPING) == true

        // If we already have a tracked spot and the owner is still near it, accumulate dwell.
        if (currentSpot != null && isOwnerNear(ownerPos, currentSpot) && isCozy(level, currentSpot)) {
            if (isSleeping) {
                brain.setMemoryIfChanged(CobblemonMemories.OWNER_NEAR_COZY_SPOT_TICKS, 0)
            } else {
                val prev = brain.getMemory(CobblemonMemories.OWNER_NEAR_COZY_SPOT_TICKS) ?: 0
                brain.setMemory(CobblemonMemories.OWNER_NEAR_COZY_SPOT_TICKS, prev + cooldownTicks)
            }
            return
        }

        val nearest = findNearestCozy(level, ownerPos)
        if (nearest == null) {
            clear(brain)
            return
        }

        // New or different spot — reset the dwell window.
        brain.setMemory(CobblemonMemories.NEARBY_OWNER_COZY_SPOT, nearest)
        brain.setMemoryIfChanged(CobblemonMemories.OWNER_NEAR_COZY_SPOT_TICKS, 0)
    }

    private fun clear(brain: BiggerBrain) {
        brain.eraseMemoryIfPresent(CobblemonMemories.NEARBY_OWNER_COZY_SPOT)
        brain.setMemory(CobblemonMemories.OWNER_NEAR_COZY_SPOT_TICKS, 0)
    }

    private fun isOwnerNear(ownerPos: BlockPos, spot: BlockPos): Boolean {
        val dx = ownerPos.x - spot.x
        val dy = ownerPos.y - spot.y
        val dz = ownerPos.z - spot.z
        if (Math.abs(dy) > verticalRadius) return false
        return (dx * dx + dz * dz) <= (ownerRadius * ownerRadius)
    }

    private fun isCozy(level: ServerLevel, pos: BlockPos) =
        level.getBlockState(pos).`is`(CobblemonBlockTags.COZY_SPOTS)

    private fun findNearestCozy(level: ServerLevel, ownerPos: BlockPos): BlockPos? {
        var closest: BlockPos? = null
        var closestDistSq = Long.MAX_VALUE
        val min = ownerPos.offset(-ownerRadius, -verticalRadius, -ownerRadius)
        val max = ownerPos.offset(ownerRadius, verticalRadius, ownerRadius)
        for (pos in BlockPos.betweenClosed(min, max)) {
            val dx = (pos.x - ownerPos.x).toLong()
            val dz = (pos.z - ownerPos.z).toLong()
            val horizDistSq = dx * dx + dz * dz
            if (horizDistSq > ownerRadius.toLong() * ownerRadius) continue
            if (!isCozy(level, pos)) continue
            val distSq = horizDistSq + (pos.y - ownerPos.y).toLong() * (pos.y - ownerPos.y)
            if (distSq < closestDistSq) {
                closest = pos.immutable()
                closestDistSq = distSq
            }
        }
        return closest
    }

    class Config : SenseConfig {
        var cooldownTicks: ExpressionOrEntityVariable = Either.left("$DEFAULT_COOLDOWN".asExpression())
        var ownerRadius: ExpressionOrEntityVariable = Either.left("$DEFAULT_OWNER_RADIUS".asExpression())
        var verticalRadius: ExpressionOrEntityVariable = Either.left("$DEFAULT_VERTICAL_RADIUS".asExpression())

        override fun getVariables(
            entity: LivingEntity,
            behaviourConfigurationContext: BehaviourConfigurationContext
        ) = listOf(cooldownTicks, ownerRadius, verticalRadius).asVariables()

        override fun create(entity: LivingEntity, behaviourConfigurationContext: BehaviourConfigurationContext): Sense {
            return OwnerCozySpotSense(
                cooldownTicks = cooldownTicks.resolveInt(behaviourConfigurationContext.runtime)
                    .coerceAtLeast(1),
                ownerRadius = ownerRadius.resolveInt(behaviourConfigurationContext.runtime)
                    .coerceAtLeast(1),
                verticalRadius = verticalRadius.resolveInt(behaviourConfigurationContext.runtime)
                    .coerceAtLeast(1)
            )
        }
    }

    companion object {
        const val DEFAULT_COOLDOWN = 10
        const val DEFAULT_OWNER_RADIUS = 10
        const val DEFAULT_VERTICAL_RADIUS = 4
    }
}
