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
import com.cobblemon.mod.common.api.ai.BehaviourConfigurationContext
import com.cobblemon.mod.common.api.ai.ExpressionOrEntityVariable
import com.cobblemon.mod.common.api.ai.asVariables
import com.cobblemon.mod.common.api.ai.biggerbrain.BiggerBrain
import com.cobblemon.mod.common.api.ai.config.sense.SenseConfig
import com.cobblemon.mod.common.api.ai.resolveDouble
import com.cobblemon.mod.common.api.ai.resolveInt
import com.cobblemon.mod.common.block.entity.NestBlockEntity
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.util.asExpression
import com.mojang.datafixers.util.Either
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.ai.memory.MemoryModuleType
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.entity.player.Player

class EggSense(
    private var cooldownTicks: Int = 100,
    private var scanRadius: Double = 20.0
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
            this.scanRadius = maxOf(this.scanRadius, other.scanRadius)
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
            // Egg is in nest
            if (!nestItem.isEmpty && nestItem.item.asItem() == CobblemonItems.POKEMON_EGG.asItem()) {
                brain.setMemory(CobblemonMemories.EGG_LOCATION, nestPos)
                brain.setMemory(CobblemonMemories.EGG_IN_NEST, true)
                brain.eraseMemory(CobblemonMemories.EGG_SEARCH_COUNTDOWN)
                return
            }
        }

        // Egg is gone from nest
        brain.setMemory(CobblemonMemories.EGG_IN_NEST, false)
        brain.eraseMemory(CobblemonMemories.EGG_LOCATION)

        // get item entities nearby that are Eggs
        val nearbyEggItems = level.getEntitiesOfClass(ItemEntity::class.java, entity.boundingBox.inflate(scanRadius)) { itemEntity ->
            val itemStack = itemEntity.item
            if (itemStack.item.asItem() != CobblemonItems.POKEMON_EGG.asItem()) return@getEntitiesOfClass false
            val eggComponent = itemStack.get(CobblemonItemComponents.EGG_DATA) ?: return@getEntitiesOfClass false
            eggComponent.isParent(entity.pokemon.uuid)
        }

        if (nearbyEggItems.isNotEmpty()) {
            // if the egg is on the ground lets stop being angry and grab it
            if (brain.hasMemoryValue(MemoryModuleType.ATTACK_TARGET))
                brain.eraseMemory(MemoryModuleType.ATTACK_TARGET)
            if (brain.hasMemoryValue(MemoryModuleType.ANGRY_AT))
                brain.eraseMemory(MemoryModuleType.ANGRY_AT)

            brain.eraseMemory(CobblemonMemories.EGG_SEARCH_COUNTDOWN)
            brain.setMemory(CobblemonMemories.EGG_LOCATION, nearbyEggItems[0].blockPosition())
            brain.eraseMemory(CobblemonMemories.EGG_THIEF)
            return
        }

        // see if we have any stored egg thieves in memory
        val existingEggThief = brain.getMemory(CobblemonMemories.EGG_THIEF) as? Player
        val lastSeenTick = brain.getMemory(CobblemonMemories.EGG_THIEF_LAST_SEEN_TICK)
        val currentTick = level.gameTime

        // we got a thief here!
        if (existingEggThief != null) {
            // there the thief is!
            if (isPlayerVisible(entity, existingEggThief)) {
                brain.setMemory(CobblemonMemories.EGG_THIEF_LAST_SEEN_TICK, currentTick)
                brain.eraseMemory(CobblemonMemories.EGG_LOCATION)
                brain.eraseMemory(CobblemonMemories.EGG_SEARCH_COUNTDOWN)
                return
            }

            val lastSeen = lastSeenTick ?: currentTick
            // lost track of thief for too long, giving up
            if (currentTick - lastSeen > THIEF_LOST_SIGHT_TICKS) {
                brain.eraseMemory(CobblemonMemories.EGG_THIEF)
                brain.eraseMemory(CobblemonMemories.EGG_THIEF_LAST_SEEN_TICK)
            } else {
                // dont see the thief
                brain.eraseMemory(CobblemonMemories.EGG_LOCATION)
                return
            }
        }

        // when looking for the egg and it is seen in the hand of a nearby player mark them as the thief
        val heldEggThief = findVisiblePlayerHoldingEgg(entity, brain)
        if (heldEggThief != null) {
            brain.setMemory(CobblemonMemories.EGG_THIEF, heldEggThief)
            brain.setMemory(CobblemonMemories.EGG_THIEF_LAST_SEEN_TICK, currentTick)
            brain.eraseMemory(CobblemonMemories.EGG_LOCATION)
            brain.eraseMemory(CobblemonMemories.EGG_SEARCH_COUNTDOWN)
        } else {
            // start search timer with no egg or player holding the egg in sight
            brain.eraseMemory(CobblemonMemories.EGG_LOCATION)
            brain.eraseMemory(CobblemonMemories.EGG_THIEF)
            brain.eraseMemory(CobblemonMemories.EGG_THIEF_LAST_SEEN_TICK)

            if (!brain.hasMemoryValue(CobblemonMemories.EGG_SEARCH_COUNTDOWN)) {
                brain.setMemory(CobblemonMemories.EGG_SEARCH_COUNTDOWN, true, EGG_SEARCH_TIME)
            }
        }
    }

    // todo we might want to have each mate report to the other if a thief is found
    private fun isPlayerVisible(entity: LivingEntity, player: Player): Boolean {
        return entity.hasLineOfSight(player) && !player.isInvisible
    }

    private fun findVisiblePlayerHoldingEgg(entity: PokemonEntity, brain: BiggerBrain): Player? {
        val visible = brain.getMemory(MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES) ?: return null
        return visible.findAll { it is Player && !it.isSpectator && !it.isCreative && isHoldingEggOf(it, entity) }
            .filterIsInstance<Player>()
            .firstOrNull()
    }

    // todo if we have pokemon that can steal eggs from nests we may want to make this work on PokemonEntity too not just players
    private fun isHoldingEggOf(player: Player, entity: PokemonEntity): Boolean {
        return listOf(InteractionHand.MAIN_HAND, InteractionHand.OFF_HAND).any { hand ->
            val stack = player.getItemInHand(hand)
            stack.item.asItem() == CobblemonItems.POKEMON_EGG.asItem() &&
                stack.get(CobblemonItemComponents.EGG_DATA)?.isParent(entity.pokemon.uuid) == true
        }
    }

    class Config : SenseConfig {
        var cooldownTicks: ExpressionOrEntityVariable = Either.left("100".asExpression())
        var scanRadius: ExpressionOrEntityVariable = Either.left("20.0".asExpression())

        override fun getVariables(
            entity: LivingEntity,
            behaviourConfigurationContext: BehaviourConfigurationContext
        ) = listOf(cooldownTicks, scanRadius).asVariables()

        override fun create(entity: LivingEntity, behaviourConfigurationContext: BehaviourConfigurationContext): Sense {
            return EggSense(
                cooldownTicks = cooldownTicks.resolveInt(behaviourConfigurationContext.runtime),
                scanRadius = scanRadius.resolveDouble(behaviourConfigurationContext.runtime)
            )
        }
    }

    companion object {
        private const val EGG_SEARCH_TIME = 2400L
        private const val THIEF_LOST_SIGHT_TICKS = 1200L
    }
}
