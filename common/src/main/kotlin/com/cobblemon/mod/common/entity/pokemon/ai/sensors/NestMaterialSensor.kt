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
import net.minecraft.world.entity.ai.memory.MemoryModuleType
import net.minecraft.world.entity.ai.sensing.Sensor
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.item.ItemStack
import net.minecraft.world.phys.AABB
import kotlin.collections.firstOrNull

/**
 * Sensor used to detect nearby nest-building materials if no nest is found and the Pokémon is wild.
 *
 * @since February 6th, 2025
 */
class NestMaterialSensor : Sensor<PokemonEntity>(100) {
    companion object {
        val OUTPUT_MEMORY_MODULES = setOf(
                CobblemonMemories.NESTING_MATERIAL_LOCATION,
                CobblemonMemories.SENSED_NEST_MATERIAL)

        private const val DETECTION_RADIUS = 10.0 // Radius to search for items
    }

    override fun requires(): Set<MemoryModuleType<*>> = OUTPUT_MEMORY_MODULES

    override fun doTick(world: ServerLevel, entity: PokemonEntity) {
        if (entity !is PokemonEntity) return

        val isWild = entity.pokemon.isWild()
        val hasNestLocation = entity.brain.hasMemoryValue(CobblemonMemories.NEST_LOCATION)
        val playersNearby = entity.brain.hasMemoryValue(MemoryModuleType.NEAREST_VISIBLE_PLAYER)
        val hasMate = entity.brain.hasMemoryValue(CobblemonMemories.POTENTIAL_MATE)

        // Only run if the Pokémon is wild and has no nest location and there is no players or mates visibly nearby
        if (!isWild || hasNestLocation || playersNearby || !hasMate) return

        val mate = entity.brain.getMemory(CobblemonMemories.POTENTIAL_MATE).orElse(null) ?: return

        // Get the mate's sensed material, if they have one
        val mateSensedMaterial = mate.brain.getMemory(CobblemonMemories.SENSED_NEST_MATERIAL).orElse(null)

        // todo make it so that the pokemon senses not the same item as their mate (so they can pick up two different items of the same type at once instead of going after the same item entity)

        // Find nearby item entities + check for ideal nesting material per the biome the mon is and sync with the mate's desired material
        val nearbyItem = world.getEntitiesOfClass(
                ItemEntity::class.java,
                AABB(entity.blockPosition()).inflate(DETECTION_RADIUS)
        ).firstOrNull { itemEntity ->
            isValidNestMaterial(itemEntity.item, world, entity.blockPosition()) &&
                    (mateSensedMaterial == null || mateSensedMaterial.item.item == itemEntity.item.item)
        }

        if (nearbyItem != null) {
            entity.brain.setMemory(CobblemonMemories.NESTING_MATERIAL_LOCATION, nearbyItem.blockPosition())
            entity.brain.setMemory(CobblemonMemories.SENSED_NEST_MATERIAL, nearbyItem)
        } else {
            entity.brain.eraseMemory(CobblemonMemories.NESTING_MATERIAL_LOCATION)
            entity.brain.eraseMemory(CobblemonMemories.SENSED_NEST_MATERIAL)
        }
    }

    /**
     * Determines if an item entity should be detected as a valid nest-building material.
     */
    private fun isValidNestMaterial(itemStack: ItemStack, world: ServerLevel, pos: BlockPos): Boolean {
        // Find the Nest type associated with this material
        val matchingNest = Nest.entries.firstOrNull { it.materials() == itemStack.item } ?: return false

        // Check if this nest type is valid for the current biome
        return matchingNest.isValidBiome(world, pos)
    }
}
