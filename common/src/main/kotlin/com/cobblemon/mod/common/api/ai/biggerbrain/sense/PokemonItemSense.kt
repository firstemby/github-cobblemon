/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.ai.biggerbrain.sense

import com.bedrockk.molang.Expression
import com.cobblemon.mod.common.CobblemonMemories
import com.cobblemon.mod.common.api.ai.BehaviourConfigurationContext
import com.cobblemon.mod.common.api.ai.ExpressionOrEntityVariable
import com.cobblemon.mod.common.api.ai.asSimplifiedExpression
import com.cobblemon.mod.common.api.ai.asVariables
import com.cobblemon.mod.common.api.ai.biggerbrain.BiggerBrain
import com.cobblemon.mod.common.api.ai.config.sense.SenseConfig
import com.cobblemon.mod.common.api.ai.resolveDouble
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.pokemon.ai.ObtainableItem
import com.cobblemon.mod.common.util.asExpression
import com.cobblemon.mod.common.util.findMatchingEntry
import com.cobblemon.mod.common.util.getMemorySafely
import com.cobblemon.mod.common.util.getObjectList
import com.cobblemon.mod.common.util.resolveBoolean
import com.mojang.datafixers.util.Either
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.ai.memory.MemoryModuleType
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.level.GameRules
import java.util.Optional

/**
 * Senses nearby item entities. Returns the closest item that the Pokemon would desire to pickup.
 *
 * @author JazzMcNade
 * @since February 15th, 2026
 */
class PokemonItemSense(
    var condition: Expression,
    var radiusXZ: Double = 16.0,
    var radiusY: Double = 16.0,
    var cooldownTicks: Int = 20 * 5
) : Sense {
    override var ticksUntilNextExecution: Int = 50
    override val memories =
        setOf(MemoryModuleType.NEAREST_VISIBLE_WANTED_ITEM, CobblemonMemories.DISABLE_WALK_TO_WANTED_ITEM)

    override fun merge(other: Sense): Boolean {
        if (other is PokemonItemSense) {
            this.condition = other.condition
            this.cooldownTicks = minOf(this.cooldownTicks, other.cooldownTicks)
            this.radiusXZ = other.radiusXZ
            this.radiusY = other.radiusY
            return true
        } else {
            return false
        }
    }

    override fun tick(entity: LivingEntity, brain: BiggerBrain) {
        ticksUntilNextExecution = cooldownTicks
        if (entity !is PokemonEntity || !brain.runtime.resolveBoolean(condition)) {
            return
        }
        val world = entity.level()
        if (!world.gameRules.getBoolean(GameRules.RULE_MOBGRIEFING) || !entity.pokemon.canDropHeldItem || entity.brain.getMemorySafely(
                CobblemonMemories.DISABLE_WALK_TO_WANTED_ITEM
            ).orElse(false)
        ) {
            // Mob griefing is disabled, the Pokemon cannot swap out its item, or it's exhausted from attempting to go to an unreachable item
            // so don't bother to search
            return
        }

        val pickupItems = entity.config.getObjectList<ObtainableItem>(PICKUP_ITEMS)
        val heldItemValue =
            pickupItems.findMatchingEntry(entity.registryAccess(), entity.pokemon.heldItem())?.pickupPriority ?: 0
        if (heldItemValue >= (pickupItems.maxOfOrNull { it.pickupPriority } ?: 0)) {
            // It's already holding the highest value item it can have, no need to look for better.
            return
        }

        val list = world.getEntitiesOfClass(
            ItemEntity::class.java,
            entity.boundingBox.inflate(radiusXZ, radiusY, radiusXZ)
        ) {
            (pickupItems.findMatchingEntry(entity.registryAccess(), it.item)?.pickupPriority ?: 0) > heldItemValue
                    && entity.hasLineOfSight(it)
        }

        // Find the closest item to the entity
        val nearestItemEntity = list.minByOrNull { it.distanceTo(entity) }
        entity.getBrain()
            .setMemory(MemoryModuleType.NEAREST_VISIBLE_WANTED_ITEM, Optional.ofNullable(nearestItemEntity))
    }


    class Config : SenseConfig {
        var condition: ExpressionOrEntityVariable = Either.left("true".asExpression())
        var radiusXZ: ExpressionOrEntityVariable = Either.left("16.0".asExpression())
        var radiusY: ExpressionOrEntityVariable = Either.left("16.0".asExpression())
        var cooldownTicks: ExpressionOrEntityVariable = Either.left("60".asExpression())

        override fun getVariables(
            entity: LivingEntity,
            behaviourConfigurationContext: BehaviourConfigurationContext
        ) = listOf(
            condition,
            radiusXZ,
            radiusY,
            cooldownTicks
        ).asVariables()

        override fun create(entity: LivingEntity, behaviourConfigurationContext: BehaviourConfigurationContext): Sense {
            return PokemonItemSense(
                condition = condition.asSimplifiedExpression(entity),
                radiusXZ = radiusXZ.resolveDouble(behaviourConfigurationContext.runtime),
                radiusY = radiusY.resolveDouble(behaviourConfigurationContext.runtime),
                cooldownTicks = cooldownTicks.resolveDouble(behaviourConfigurationContext.runtime).toInt()
            )
        }
    }

    companion object {
        const val PICKUP_ITEMS = "pickup_items"
    }

}