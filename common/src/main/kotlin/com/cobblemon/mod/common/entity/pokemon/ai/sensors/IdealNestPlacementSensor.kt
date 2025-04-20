/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.entity.pokemon.ai.sensors

import com.cobblemon.mod.common.CobblemonMemories
import com.cobblemon.mod.common.api.nest.Nest
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.PathfinderMob
import net.minecraft.world.entity.ai.memory.MemoryModuleType
import net.minecraft.world.entity.ai.sensing.Sensor
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.AABB
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
            CobblemonMemories.NESTING_MATERIAL_LOCATION,
            CobblemonMemories.SENSED_NEST_MATERIAL,
            CobblemonMemories.IDEAL_NEST_LOCATION
        )

        private const val DETECTION_RADIUS = 10
        private const val MIN_NEST_DISTANCE = 10
    }

    override fun requires(): Set<MemoryModuleType<*>> = OUTPUT_MEMORY_MODULES

    override fun doTick(world: ServerLevel, entity: PokemonEntity) {
        if (entity !is PokemonEntity) return

        val mate = entity.brain.getMemory(CobblemonMemories.POTENTIAL_MATE).getOrNull() ?: return

        // cancel sensor if entity is not ready to build a nest
        val readyToBuildNest = entity.brain.getMemory(CobblemonMemories.READY_TO_BUILD_NEST).getOrNull()
        if (readyToBuildNest != true) {
            entity.brain.eraseMemory(CobblemonMemories.IDEAL_NEST_LOCATION)
            mate.brain.eraseMemory(CobblemonMemories.IDEAL_NEST_LOCATION)
            return
        }

        // TODO make new PathToIdealNestLocationTask that goes before the BuildNestTask that uses a NEW memory to make sure that path goes before build


        // todo make it so nests can only be made on top of certain blocks
        // todo make it so that nests cannot be placed within a certain radius of other nests
        // todo only waterloggable nests can be placed underwater
        // todo is pathfindable (maybe with a certain area of space around it)
        // todo can pathfind to it too

        // todo IDEALLY birds could maybe see leaf nest materials on the ground, check if there is an ideal place nearby to make a leaf nest and then decide to gather the leaf materials. When they have enough they would pathfind up to the trees to place their nest.

        // TODO also check to see if a pokemon can even breed in a nest at that location too (check breeding logic and Nest Sensor)

        // Get held item to determine which nest type they want to build
        val heldItemStack = entity.getItemInHand(InteractionHand.MAIN_HAND)
        val heldItem = heldItemStack.item
        val nestType = Nest.entries.firstOrNull { it.materials() == heldItem } ?: return

        // Check if an ideal nest location is already stored and if it is still valid to save some time possibly
        val storedNestPos = entity.brain.getMemory(CobblemonMemories.IDEAL_NEST_LOCATION).getOrNull()

        if (storedNestPos != null) {
            if (isValidNestPlacement(world, entity, nestType, storedNestPos)) {
                mate.brain.setMemory(CobblemonMemories.IDEAL_NEST_LOCATION, storedNestPos)
                return // Keep using the stored location, no need to find a new one
            } else {
                entity.brain.eraseMemory(CobblemonMemories.IDEAL_NEST_LOCATION) // Remove invalid location
                mate.brain.eraseMemory(CobblemonMemories.IDEAL_NEST_LOCATION)
            }
        }

        val entityPos = entity.blockPosition()

        // Scan for a valid nest placement position
        val validNestPosition = findValidNestPlacement(world, entity, nestType, entityPos)

        if (validNestPosition != null) {
            // Set memory for the chosen placement location
            entity.brain.setMemory(CobblemonMemories.IDEAL_NEST_LOCATION, validNestPosition)
            mate.brain.setMemory(CobblemonMemories.IDEAL_NEST_LOCATION, validNestPosition)
        } else {
            entity.brain.eraseMemory(CobblemonMemories.IDEAL_NEST_LOCATION)
            mate.brain.eraseMemory(CobblemonMemories.IDEAL_NEST_LOCATION)
        }
    }

    private fun findValidNestPlacement(world: ServerLevel, entity: PokemonEntity, nestType: Nest, entityPos: BlockPos): BlockPos? {

        return BlockPos.betweenClosed(
            entityPos.offset(-DETECTION_RADIUS, -5, -DETECTION_RADIUS),
            entityPos.offset(DETECTION_RADIUS, 5, DETECTION_RADIUS)
        ).firstOrNull { pos ->
            val blockBelow = world.getBlockState(pos).block
            val nestPos = pos.above() // nest placing position
            val blockAbove = world.getBlockState(nestPos).block
            val blockAbove2 = world.getBlockState(nestPos.above()).block
            val biomeHolder = world.getBiome(pos)
            val validSnowSpot = (nestType == Nest.SNOW && (blockAbove != Blocks.SNOW || blockBelow != Blocks.SNOW_BLOCK))

            // Block is in the valid placement list for this nest type
            if (!nestType.placementBlocks().contains(blockBelow)) return@firstOrNull false

            // waterloggable or non-waterloggable air, water, snow check
            if (nestType.isWaterloggable()) {
                if (blockAbove != Blocks.WATER || blockAbove2 != Blocks.WATER) return@firstOrNull false
            } else {
                if (nestType == Nest.SNOW) {
                    // Snow nests can be placed directly on SNOW_BLOCK
                    if (blockBelow != Blocks.SNOW_BLOCK) {
                        // Snow nests can also be placed on other allowed blocks ONLY if a snow layer is present on top
                        if (blockAbove != Blocks.SNOW) return@firstOrNull false
                    }
                } else {
                    // For all other nests, ensure the two blocks above are air
                    if (!(blockAbove == Blocks.AIR && blockAbove2 == Blocks.AIR)) return@firstOrNull false
                }
            }

            // biome check for valid nest per biome
            if (!nestType.isValidBiome(world, pos)) return@firstOrNull false

            // make sure no nests nearby intended location
            if (isNearExistingNest(world, pos)) return@firstOrNull false

            // make sure mons can pathfind here
            if (!canPathfindToTarget(entity, nestPos)) return@firstOrNull false

            return@firstOrNull true
        }?.above()
    }

    private fun isValidNestPlacement(world: ServerLevel, entity: PokemonEntity, nestType: Nest, pos: BlockPos): Boolean {
        val blockBelow = world.getBlockState(pos.below()).block
        val blockAbove = world.getBlockState(pos.above()).block
        val blockAbove2 = world.getBlockState(pos.above(2)).block


        // The block is in the valid placement list
        if (!nestType.placementBlocks().contains(blockBelow)) return false

        // waterloggable and snow, water, air check
        if (nestType.isWaterloggable()) {
            if (blockAbove != Blocks.WATER || blockAbove2 != Blocks.WATER) return false
        } else {
            if (!(blockAbove == Blocks.AIR && blockAbove2 == Blocks.AIR)) {
                // Snow Nests can have a snow layer on top IF the ground is not a snow block
                if (!(nestType == Nest.SNOW && blockAbove == Blocks.SNOW && blockBelow != Blocks.SNOW_BLOCK)) {
                    return false
                }
            }
        }

        // Biome Check
        if (!nestType.isValidBiome(world, pos)) return false

        // Ensure no existing nests nearby
        if (isNearExistingNest(world, pos)) return false

        // the Pokémon can pathfind to this location
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

}
