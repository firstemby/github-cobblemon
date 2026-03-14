/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.ai.biggerbrain.sense

import com.cobblemon.mod.common.CobblemonItemComponents
import com.cobblemon.mod.common.CobblemonItems
import com.cobblemon.mod.common.CobblemonMemories
import com.cobblemon.mod.common.api.ai.biggerbrain.BiggerBrain
import com.cobblemon.mod.common.api.ai.config.sense.SenseConfig
import com.cobblemon.mod.common.api.text.green
import com.cobblemon.mod.common.block.entity.NestBlockEntity
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.ai.memory.MemoryModuleType
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.entity.player.Player

class EggSense(
    private var cooldownTicks: Int = 100
) : Sense {
    override var ticksUntilNextExecution: Int = 100
    override val memories = setOf(
        CobblemonMemories.PATERNAL_NEST_LOCATION,
        CobblemonMemories.EGG_IN_NEST,
        CobblemonMemories.EGG_LOCATION,
        CobblemonMemories.EGG_THIEF,
        CobblemonMemories.EGG_THIEF_LAST_SEEN_TICK,
        CobblemonMemories.EGG_SEARCH_COUNTDOWN,
        MemoryModuleType.ATTACK_TARGET,
        MemoryModuleType.ANGRY_AT,
        MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES
    )

    override fun merge(other: Sense): Boolean {
        if (other is EggSense) {
            this.cooldownTicks = minOf(this.cooldownTicks, other.cooldownTicks)
            return true
        }
        return false
    }

    override fun tick(entity: LivingEntity, brain: BiggerBrain) {
        ticksUntilNextExecution = cooldownTicks
        if (entity !is PokemonEntity) return

        val level = entity.level() as? ServerLevel ?: return
        val nestPos = brain.getMemory(CobblemonMemories.PATERNAL_NEST_LOCATION) ?: return

        val blockEntity = level.getBlockEntity(nestPos)
        if (blockEntity is NestBlockEntity) {
            val nestItem = blockEntity.getItem()
            if (!nestItem.isEmpty && nestItem.item.asItem() == CobblemonItems.POKEMON_EGG.asItem()) {
                brain.sendDebugMessage("[EggSense] Egg confirmed in nest at $nestPos.")
                brain.setMemory(CobblemonMemories.EGG_LOCATION, nestPos)
                brain.setMemory(CobblemonMemories.EGG_IN_NEST, true)
                brain.eraseMemory(CobblemonMemories.EGG_SEARCH_COUNTDOWN)
                return
            }
        }

        brain.sendDebugMessage("[EggSense] Egg missing from nest at $nestPos; searching.")
        brain.setMemory(CobblemonMemories.EGG_IN_NEST, false)
        brain.eraseMemory(CobblemonMemories.EGG_LOCATION)

        val nearbyEggItems = level.getEntitiesOfClass(ItemEntity::class.java, entity.boundingBox.inflate(20.0)) { itemEntity ->
            val itemStack = itemEntity.item
            if (itemStack.item.asItem() != CobblemonItems.POKEMON_EGG.asItem()) return@getEntitiesOfClass false
            val eggComponent = itemStack.get(CobblemonItemComponents.EGG_DATA) ?: return@getEntitiesOfClass false
            eggComponent.isParent(entity.pokemon.uuid)
        }

        val existingEggThief = brain.getMemory(CobblemonMemories.EGG_THIEF) as? Player
        val lastSeenTick = brain.getMemory(CobblemonMemories.EGG_THIEF_LAST_SEEN_TICK)
        val currentTick = level.gameTime

        if (existingEggThief != null) {
            if (isEntityTargetable(entity, existingEggThief)) {
                brain.setMemory(CobblemonMemories.EGG_THIEF_LAST_SEEN_TICK, currentTick)
                brain.sendDebugMessage("[EggSense] Egg thief still in sight: ${existingEggThief.name.string}.")
                brain.eraseMemory(CobblemonMemories.EGG_LOCATION)
                brain.eraseMemory(CobblemonMemories.EGG_SEARCH_COUNTDOWN)
                return
            }

            val lastSeen = lastSeenTick ?: currentTick
            if (currentTick - lastSeen > THIEF_LOST_SIGHT_TICKS) {
                brain.sendDebugMessage("[EggSense] Lost sight of egg thief for too long; clearing.")
                brain.eraseMemory(CobblemonMemories.EGG_THIEF)
                brain.eraseMemory(CobblemonMemories.EGG_THIEF_LAST_SEEN_TICK)
            } else {
                brain.sendDebugMessage("[EggSense] Egg thief out of sight; holding target.")
                brain.eraseMemory(CobblemonMemories.EGG_LOCATION)
                return
            }
        }

        if (nearbyEggItems.isNotEmpty()) {
            brain.sendDebugMessage("[EggSense] Egg item found on ground near ${nearbyEggItems[0].blockPosition()}.")
            level.players().forEach { player ->
                player.displayClientMessage("An egg has been found on the ground!".green(), true)
            }
            if (brain.hasMemoryValue(MemoryModuleType.ATTACK_TARGET)) brain.eraseMemory(MemoryModuleType.ATTACK_TARGET)
            if (brain.hasMemoryValue(MemoryModuleType.ANGRY_AT)) brain.eraseMemory(MemoryModuleType.ANGRY_AT)

            brain.eraseMemory(CobblemonMemories.EGG_SEARCH_COUNTDOWN)
            brain.setMemory(CobblemonMemories.EGG_LOCATION, nearbyEggItems[0].blockPosition())
            brain.eraseMemory(CobblemonMemories.EGG_THIEF)
        } else {
            brain.sendDebugMessage("[EggSense] No egg or thief found; starting search timer.")
            brain.eraseMemory(CobblemonMemories.EGG_LOCATION)
            brain.eraseMemory(CobblemonMemories.EGG_THIEF)
            brain.eraseMemory(CobblemonMemories.EGG_THIEF_LAST_SEEN_TICK)

            if (!brain.hasMemoryValue(CobblemonMemories.EGG_SEARCH_COUNTDOWN)) {
                brain.setMemory(CobblemonMemories.EGG_SEARCH_COUNTDOWN, true, EGG_SEARCH_TIME)
            }
        }
    }

    private fun isEntityTargetable(entity: LivingEntity, player: Player): Boolean {
        return entity.hasLineOfSight(player) && !player.isInvisible
    }

    class Config : SenseConfig {
        override fun create(entity: LivingEntity, behaviourConfigurationContext: com.cobblemon.mod.common.api.ai.BehaviourConfigurationContext): Sense? {
            return EggSense()
        }
    }

    companion object {
        private const val EGG_SEARCH_TIME = 2400L
        private const val THIEF_LOST_SIGHT_TICKS = 1200L
    }
}
