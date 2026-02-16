/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.entity.pokemon.ai.sensors

import com.cobblemon.mod.common.CobblemonMemories
import com.cobblemon.mod.common.api.nest.WildNestType
import com.cobblemon.mod.common.api.nest.WildNestTypes
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.PathfinderMob
import net.minecraft.world.entity.ai.memory.MemoryModuleType
import net.minecraft.world.entity.ai.sensing.Sensor
import net.minecraft.world.level.block.Blocks
import kotlin.collections.firstOrNull
import kotlin.jvm.optionals.getOrNull

/**
 * Sensor used to detect an ideal place for a nest to be built
 *
 * @since February 6th, 2025
 */
class IdealNestPlacementSensor : Sensor<PokemonEntity>(100) { // todo mess with the sensor rate to make sure it works nice and is performant
    companion object {
        val OUTPUT_MEMORY_MODULES = setOf(
            CobblemonMemories.IDEAL_NEST_LOCATION,
            CobblemonMemories.READY_TO_BUILD_NEST
        )

        private const val DETECTION_RADIUS = 10
        private const val MIN_NEST_DISTANCE = 10
    }

    override fun requires(): Set<MemoryModuleType<*>> = OUTPUT_MEMORY_MODULES

    override fun doTick(world: ServerLevel, entity: PokemonEntity) {
        val mate = entity.brain.getMemory(CobblemonMemories.POTENTIAL_MATE).getOrNull() as? PokemonEntity
        val combinedTypes = mate?.let { WildNestTypes.combinedTypes(entity, it) } ?: emptyList()
        if (mate == null || combinedTypes.isEmpty()) {
            entity.brain.eraseMemory(CobblemonMemories.IDEAL_NEST_LOCATION)
            entity.brain.eraseMemory(CobblemonMemories.READY_TO_BUILD_NEST)
            mate?.brain?.eraseMemory(CobblemonMemories.IDEAL_NEST_LOCATION)
            mate?.brain?.eraseMemory(CobblemonMemories.READY_TO_BUILD_NEST)
            return
        }

        if (entity.brain.getMemory(CobblemonMemories.READY_TO_BUILD_NEST).getOrNull() != true) {
            entity.brain.setMemory(CobblemonMemories.READY_TO_BUILD_NEST, true)
            mate.brain.setMemory(CobblemonMemories.READY_TO_BUILD_NEST, true)
        }

        // Check if an ideal nest location is already stored and if it is still valid to save some time possibly
        val storedNestPos = entity.brain.getMemory(CobblemonMemories.IDEAL_NEST_LOCATION).getOrNull()

        if (storedNestPos != null) {
            if (isValidNestPlacement(world, entity, mate, storedNestPos, combinedTypes)) {
                mate.brain.setMemory(CobblemonMemories.IDEAL_NEST_LOCATION, storedNestPos)
                return // Keep using the stored location, no need to find a new one
            } else {
                entity.brain.eraseMemory(CobblemonMemories.IDEAL_NEST_LOCATION) // Remove invalid location
                mate.brain.eraseMemory(CobblemonMemories.IDEAL_NEST_LOCATION)
            }
        }

        val entityPos = entity.blockPosition()

        // Scan for a valid nest placement position
        val validNestPosition = findValidNestPlacement(world, entity, mate, entityPos, combinedTypes)

        if (validNestPosition != null) {
            // Set memory for the chosen placement location
            entity.brain.setMemory(CobblemonMemories.IDEAL_NEST_LOCATION, validNestPosition)
            mate.brain.setMemory(CobblemonMemories.IDEAL_NEST_LOCATION, validNestPosition)
        } else {
            entity.brain.eraseMemory(CobblemonMemories.IDEAL_NEST_LOCATION)
            mate.brain.eraseMemory(CobblemonMemories.IDEAL_NEST_LOCATION)
        }
    }

    private fun findValidNestPlacement(world: ServerLevel, entity: PokemonEntity, mate: PokemonEntity?, entityPos: BlockPos, sharedTypes: List<WildNestType>): BlockPos? {
        return BlockPos.betweenClosed(
            entityPos.offset(-DETECTION_RADIUS, -5, -DETECTION_RADIUS),
            entityPos.offset(DETECTION_RADIUS, 5, DETECTION_RADIUS)
        ).firstOrNull { pos ->
            val blockBelowState = world.getBlockState(pos)
            val nestPos = pos.above() // nest placing position
            val blockAbove = world.getBlockState(nestPos).block
            val blockAbove2 = world.getBlockState(nestPos.above()).block
            val placement = WildNestTypes.resolvePlacement(sharedTypes, blockBelowState, world.getFluidState(nestPos)) ?: return@firstOrNull false

            // Ensure the two blocks above are valid
            if (!isWaterGroupCompatible(entity, mate, placement.waterlogged)) return@firstOrNull false

            if (placement.waterlogged) {
                if (!(blockAbove == Blocks.WATER && blockAbove2 == Blocks.WATER)) return@firstOrNull false
            } else {
                if (!(blockAbove == Blocks.AIR && blockAbove2 == Blocks.AIR)) return@firstOrNull false
            }

            // make sure no nests nearby intended location
            if (isNearExistingNest(world, nestPos)) return@firstOrNull false

            // make sure mons can pathfind here
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

        // Ensure no existing nests nearby
        if (isNearExistingNest(world, pos)) return false

        // the Pokemon can pathfind to this location
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
            val blockEntity = world.getBlockEntity(checkPos)
            blockEntity is com.cobblemon.mod.common.block.entity.NestBlockEntity
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

}
