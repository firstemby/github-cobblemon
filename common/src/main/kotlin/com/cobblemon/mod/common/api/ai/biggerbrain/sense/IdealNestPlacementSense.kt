/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.ai.biggerbrain.sense

import com.cobblemon.mod.common.CobblemonMemories
import com.cobblemon.mod.common.api.ai.biggerbrain.BiggerBrain
import com.cobblemon.mod.common.api.ai.config.sense.SenseConfig
import com.cobblemon.mod.common.api.nest.WildNestType
import com.cobblemon.mod.common.api.nest.WildNestTypes
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.PathfinderMob
import net.minecraft.world.entity.ai.memory.MemoryModuleType
import net.minecraft.world.level.block.Blocks
import kotlin.jvm.optionals.getOrNull

class IdealNestPlacementSense(
    private var cooldownTicks: Int = 100
) : Sense {
    override var ticksUntilNextExecution: Int = 100
    override val memories = setOf(
        CobblemonMemories.IDEAL_NEST_LOCATION,
        CobblemonMemories.READY_TO_BUILD_NEST,
        CobblemonMemories.POTENTIAL_MATE
    )

    override fun merge(other: Sense): Boolean {
        if (other is IdealNestPlacementSense) {
            this.cooldownTicks = minOf(this.cooldownTicks, other.cooldownTicks)
            return true
        }
        return false
    }

    override fun tick(entity: LivingEntity, brain: BiggerBrain) {
        ticksUntilNextExecution = cooldownTicks
        if (entity !is PokemonEntity) return
        val world = entity.level() as? ServerLevel ?: return

        val mate = brain.getMemory(CobblemonMemories.POTENTIAL_MATE) as? PokemonEntity
        val combinedTypes = mate?.let { WildNestTypes.combinedTypes(entity, it) } ?: emptyList()
        if (mate == null || combinedTypes.isEmpty()) {
            debug(brain, "No mate or no combined nest types; clearing ideal nest memories.")
            brain.eraseMemory(CobblemonMemories.IDEAL_NEST_LOCATION)
            brain.eraseMemory(CobblemonMemories.READY_TO_BUILD_NEST)
            mate?.biggerBrain?.eraseMemory(CobblemonMemories.IDEAL_NEST_LOCATION)
            mate?.biggerBrain?.eraseMemory(CobblemonMemories.READY_TO_BUILD_NEST)
            return
        }

        if (brain.getMemory(CobblemonMemories.READY_TO_BUILD_NEST) != true) {
            debug(brain, "Ready to build set true for both parents.")
            brain.setMemory(CobblemonMemories.READY_TO_BUILD_NEST, true)
            mate.biggerBrain.setMemory(CobblemonMemories.READY_TO_BUILD_NEST, true)
        }

        val storedNestPos = brain.getMemory(CobblemonMemories.IDEAL_NEST_LOCATION)
        if (storedNestPos != null) {
            if (isValidNestPlacement(world, entity, mate, storedNestPos, combinedTypes)) {
                debug(brain, "Stored ideal nest still valid at $storedNestPos.")
                mate.biggerBrain.setMemory(CobblemonMemories.IDEAL_NEST_LOCATION, storedNestPos)
                return
            } else {
                debug(brain, "Stored ideal nest invalid at $storedNestPos; clearing.")
                brain.eraseMemory(CobblemonMemories.IDEAL_NEST_LOCATION)
                mate.biggerBrain.eraseMemory(CobblemonMemories.IDEAL_NEST_LOCATION)
            }
        }

        val entityPos = entity.blockPosition()
        val validNestPosition = findValidNestPlacement(world, entity, mate, entityPos, combinedTypes)

        if (validNestPosition != null) {
            debug(brain, "Found ideal nest position at $validNestPosition.")
            brain.setMemory(CobblemonMemories.IDEAL_NEST_LOCATION, validNestPosition)
            mate.biggerBrain.setMemory(CobblemonMemories.IDEAL_NEST_LOCATION, validNestPosition)
        } else {
            debug(brain, "No valid ideal nest position found.")
            brain.eraseMemory(CobblemonMemories.IDEAL_NEST_LOCATION)
            mate.biggerBrain.eraseMemory(CobblemonMemories.IDEAL_NEST_LOCATION)
        }
    }

    private fun findValidNestPlacement(world: ServerLevel, entity: PokemonEntity, mate: PokemonEntity?, entityPos: BlockPos, sharedTypes: List<WildNestType>): BlockPos? {
        return BlockPos.betweenClosed(
            entityPos.offset(-DETECTION_RADIUS, -5, -DETECTION_RADIUS),
            entityPos.offset(DETECTION_RADIUS, 5, DETECTION_RADIUS)
        ).firstOrNull { pos ->
            val blockBelowState = world.getBlockState(pos)
            val nestPos = pos.above()
            val blockAbove = world.getBlockState(nestPos).block
            val blockAbove2 = world.getBlockState(nestPos.above()).block
            val placement = WildNestTypes.resolvePlacement(sharedTypes, blockBelowState, world.getFluidState(nestPos)) ?: return@firstOrNull false

            if (!isWaterGroupCompatible(entity, mate, placement.waterlogged)) return@firstOrNull false

            if (placement.waterlogged) {
                if (!(blockAbove == Blocks.WATER && blockAbove2 == Blocks.WATER)) return@firstOrNull false
            } else {
                if (!(blockAbove == Blocks.AIR && blockAbove2 == Blocks.AIR)) return@firstOrNull false
            }

            if (isNearExistingNest(world, nestPos)) return@firstOrNull false
            if (!canPathfindToTarget(entity, nestPos)) return@firstOrNull false

            return@firstOrNull true
        }?.above()
    }

    private fun isValidNestPlacement(world: ServerLevel, entity: PokemonEntity, mate: PokemonEntity?, pos: BlockPos, sharedTypes: List<WildNestType>): Boolean {
        val blockBelowState = world.getBlockState(pos.below())
        val blockAbove = world.getBlockState(pos.above()).block
        val blockAbove2 = world.getBlockState(pos.above(2)).block
        val placement = WildNestTypes.resolvePlacement(sharedTypes, blockBelowState, world.getFluidState(pos)) ?: return false

        if (!isWaterGroupCompatible(entity, mate, placement.waterlogged)) return false

        if (placement.waterlogged) {
            if (!(blockAbove == Blocks.WATER && blockAbove2 == Blocks.WATER)) return false
        } else {
            if (!(blockAbove == Blocks.AIR && blockAbove2 == Blocks.AIR)) return false
        }

        if (isNearExistingNest(world, pos)) return false
        if (!canPathfindToTarget(entity, pos)) return false

        return true
    }

    private fun canPathfindToTarget(entity: PathfinderMob, targetPos: BlockPos): Boolean {
        val path = entity.navigation.createPath(targetPos, 0)
        return path != null && path.distToTarget < 1.75f
    }

    private fun isNearExistingNest(world: ServerLevel, pos: BlockPos): Boolean {
        return BlockPos.betweenClosed(
            pos.offset(-MIN_NEST_DISTANCE, -MIN_NEST_DISTANCE, -MIN_NEST_DISTANCE),
            pos.offset(MIN_NEST_DISTANCE, MIN_NEST_DISTANCE, MIN_NEST_DISTANCE)
        ).any { checkPos ->
            world.getBlockEntity(checkPos) is com.cobblemon.mod.common.block.entity.NestBlockEntity
        }
    }

    private fun isWaterGroupCompatible(
        entity: PokemonEntity,
        mate: PokemonEntity?,
        isNestWaterlogged: Boolean
    ): Boolean {
        val waterEggGroups = setOf("water_1", "water_2", "water_3")
        val entityEggGroups = entity.pokemon.species.eggGroups.map { it.name.lowercase() }
        val entityHasWater = entityEggGroups.any { it in waterEggGroups }
        val entityOnlyWater = entityEggGroups.all { it in waterEggGroups }

        val mateEggGroups = mate?.pokemon?.species?.eggGroups?.map { it.name.lowercase() } ?: emptyList()
        val mateHasWater = mateEggGroups.any { it in waterEggGroups }
        val mateOnlyWater = mateEggGroups.isNotEmpty() && mateEggGroups.all { it in waterEggGroups }

        return if (isNestWaterlogged) {
            entityHasWater && mateHasWater
        } else {
            !(entityOnlyWater || mateOnlyWater)
        }
    }

    private fun debug(brain: BiggerBrain, message: String) {
        brain.sendDebugMessage("[IdealNestPlacementSense] $message")
    }

    class Config : SenseConfig {
        override fun create(entity: LivingEntity, behaviourConfigurationContext: com.cobblemon.mod.common.api.ai.BehaviourConfigurationContext): Sense? {
            return IdealNestPlacementSense()
        }
    }

    companion object {
        private const val DETECTION_RADIUS = 10
        private const val MIN_NEST_DISTANCE = 10
    }
}
