package com.cobblemon.mod.common.entity.pokemon.ai.sensors

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.pokemon.Pokemon
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.ai.memory.MemoryModuleType
import net.minecraft.world.entity.ai.sensing.Sensor
import net.minecraft.world.entity.item.ItemEntity

class ItemEntitySensor: Sensor<PokemonEntity>(100) {
    override fun requires(): Set<MemoryModuleType<*>> {
        return setOf(MemoryModuleType.NEAREST_VISIBLE_WANTED_ITEM)
    }

    override fun doTick(level: ServerLevel, entity: PokemonEntity) {
        val nearbyItems = level.getEntitiesOfClass(ItemEntity::class.java, entity.boundingBox.inflate(10.0)) { true }
        if (nearbyItems.isNotEmpty()) {
            entity.brain.setMemory(MemoryModuleType.NEAREST_VISIBLE_WANTED_ITEM, nearbyItems[0])
        } else {
            entity.brain.eraseMemory(MemoryModuleType.NEAREST_VISIBLE_WANTED_ITEM)
        }
    }
}