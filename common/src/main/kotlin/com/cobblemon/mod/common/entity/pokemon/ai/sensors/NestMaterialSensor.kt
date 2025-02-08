package com.cobblemon.mod.common.entity.pokemon.ai.sensors

import com.cobblemon.mod.common.CobblemonMemories
import com.cobblemon.mod.common.api.nest.Nest
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
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

        // Find nearby item entities
        val nearbyItem = world.getEntitiesOfClass(
                ItemEntity::class.java,
                AABB(entity.blockPosition()).inflate(DETECTION_RADIUS)
        ).firstOrNull { itemEntity ->
            isValidNestMaterial(itemEntity.item)
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
    private fun isValidNestMaterial(itemStack: ItemStack): Boolean {
        return Nest.entries.any { it.materials() == itemStack.item }
    }
}
