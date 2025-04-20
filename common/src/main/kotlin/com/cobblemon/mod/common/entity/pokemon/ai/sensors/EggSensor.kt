/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.entity.pokemon.ai.sensors

import com.cobblemon.mod.common.CobblemonItemComponents
import com.cobblemon.mod.common.CobblemonItems
import com.cobblemon.mod.common.CobblemonMemories
import com.cobblemon.mod.common.api.text.green
import com.cobblemon.mod.common.block.entity.NestBlockEntity
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.PathfinderMob
import net.minecraft.world.entity.ai.memory.MemoryModuleType
import net.minecraft.world.entity.ai.sensing.Sensor
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.entity.player.Player
import kotlin.collections.firstOrNull
import com.cobblemon.mod.common.item.PokemonEggItem

/**
 * Sensor used for parents to detect nearby eggs
 *
 * @Plastered_Crab January 12th, 2025
 */

class EggSensor : Sensor<PokemonEntity>(100) {
    private val EGG_SEARCH_TIME = 2400L
    override fun requires(): Set<MemoryModuleType<*>> {
        return setOf(CobblemonMemories.PATERNAL_NEST_LOCATION )
    }

    override fun doTick(level: ServerLevel, entity: PokemonEntity) {
        // we want this sensor to only really run if the paternal nest exists and is empty
        val nestPos = entity.brain.getMemory(CobblemonMemories.PATERNAL_NEST_LOCATION).orElse(null) ?: return
        val blockEntity = nestPos.let { level.getBlockEntity(it) }

        if (blockEntity is NestBlockEntity) {
            val nestItem = blockEntity.getItem()

            // If the nest contains an egg, set EGG_LOCATION to the nest todo make sure pokemon is parent of egg
            if (!nestItem.isEmpty && nestItem.item.asItem() == CobblemonItems.POKEMON_EGG.asItem()) {
                entity.brain.setMemory(CobblemonMemories.EGG_LOCATION, nestPos)
                entity.brain.setMemory(CobblemonMemories.EGG_IN_NEST, true)
                entity.brain.eraseMemory(CobblemonMemories.EGG_SEARCH_COUNTDOWN)
                return
            }
        }

        // If no egg is in the nest, clear EGG_LOCATION before looking around
        entity.brain.setMemory(CobblemonMemories.EGG_IN_NEST, false)
        entity.brain.eraseMemory(CobblemonMemories.EGG_LOCATION) // todo should I be doing this?

        // Detect egg items on the ground
        val nearbyEggItems = level.getEntitiesOfClass(ItemEntity::class.java, entity.boundingBox.inflate(20.0)) { itemEntity ->
            val itemStack = itemEntity.item

            if (itemStack.item.asItem() != CobblemonItems.POKEMON_EGG.asItem()) return@getEntitiesOfClass false

            val eggComponent = itemStack.get(CobblemonItemComponents.EGG_DATA) ?: return@getEntitiesOfClass false

            // Check if the Pokémon is a parent of this egg
            return@getEntitiesOfClass (eggComponent.egg?.parentOne?.uuid == entity.pokemon.uuid ||
                    eggComponent.egg?.parentTwo?.uuid == entity.pokemon.uuid)
        }

        // Detect visible players and store them in a temp variable
        val nearbyVisiblePlayers = entity.brain.getMemory(MemoryModuleType.NEAREST_PLAYERS)
            .orElse(emptyList())
            .filter { player ->
                !player.isSpectator && !player.isCreative &&
                        isEntityTargetable(entity, player)
            }

        // Filter for the first visible player holding an egg that belongs to this Pokémon
        val eggThief = nearbyVisiblePlayers.firstOrNull { player ->
            player.inventory.items.any { itemStack ->
                itemStack.item.asItem() == CobblemonItems.POKEMON_EGG.asItem() &&
                        itemStack.get(CobblemonItemComponents.EGG_DATA)?.egg?.let { egg ->
                            egg.parentOne?.uuid == entity.pokemon.uuid || egg.parentTwo?.uuid == entity.pokemon.uuid
                        } == true
            }
        }

        // Prioritize the egg item on the ground over the Egg Thief
        if (nearbyEggItems.isNotEmpty()) {
            // If an egg is found on the ground, store its location and clear EggThief memory
            level.players().forEach { player ->
                player.displayClientMessage("An egg has been found on the ground!".green(), true)
            }
            // if there is an attack target prioritize getting the egg back to the nest
            if (entity.brain.getMemory(MemoryModuleType.ATTACK_TARGET).isPresent)
                entity.brain.eraseMemory(MemoryModuleType.ATTACK_TARGET)
            if (entity.brain.getMemory(MemoryModuleType.ANGRY_AT).isPresent)
                entity.brain.eraseMemory(MemoryModuleType.ANGRY_AT)

            entity.brain.eraseMemory(CobblemonMemories.EGG_SEARCH_COUNTDOWN)
            entity.brain.setMemory(CobblemonMemories.EGG_LOCATION, nearbyEggItems[0].blockPosition())
            entity.brain.eraseMemory(CobblemonMemories.EGG_THIEF)
        } else if (eggThief != null) {
            level.players().forEach { player ->
                player.displayClientMessage("An egg thief has been has been found!".green(), true)
            }
            // If no egg is on the ground but an Egg Thief is detected track the player
            entity.brain.setMemory(CobblemonMemories.EGG_THIEF, eggThief)
            entity.brain.eraseMemory(CobblemonMemories.EGG_LOCATION)
            entity.brain.eraseMemory(CobblemonMemories.EGG_SEARCH_COUNTDOWN) // todo add attack target

            // TODO remove this later once I get the dang ol' AttackEggThiefTask to work >:(
            //entity.brain.setMemory(MemoryModuleType.ATTACK_TARGET, eggThief)
        } else {
            // If no egg is found and no thief detected clear memories
            entity.brain.eraseMemory(CobblemonMemories.EGG_LOCATION)
            entity.brain.eraseMemory(CobblemonMemories.EGG_THIEF)

            // start search countdown
            if (!entity.brain.hasMemoryValue(CobblemonMemories.EGG_SEARCH_COUNTDOWN)) {
                entity.brain.setMemoryWithExpiry(CobblemonMemories.EGG_SEARCH_COUNTDOWN, true, EGG_SEARCH_TIME) // Start countdown if not already started
            }
        }
    }

    /**
     * Checks if the player is visible to the Pokémon.
     */
    private fun isEntityTargetable(entity: LivingEntity, player: Player): Boolean {
        return entity.hasLineOfSight(player) && !player.isInvisible
    }

    // unsure if we should check for this, but probably
    private fun canPathfindToTarget(entity: PathfinderMob, targetPos: BlockPos): Boolean {
        val path = entity.navigation.createPath(targetPos, 0)
        return path != null && path.distToTarget < 1.75f
    }
}