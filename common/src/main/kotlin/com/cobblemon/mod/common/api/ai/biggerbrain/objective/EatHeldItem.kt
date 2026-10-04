/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.ai.biggerbrain.objective

import com.cobblemon.mod.common.api.ai.BehaviourConfigurationContext
import com.cobblemon.mod.common.api.ai.ExpressionOrEntityVariable
import com.cobblemon.mod.common.api.ai.asVariables
import com.cobblemon.mod.common.api.ai.biggerbrain.BiggerBrain
import com.cobblemon.mod.common.api.ai.biggerbrain.distraction.Distraction
import com.cobblemon.mod.common.api.ai.biggerbrain.fixation.EntityFixation
import com.cobblemon.mod.common.api.ai.biggerbrain.objective.PickupItem.Companion.dropItem
import com.cobblemon.mod.common.api.ai.biggerbrain.sense.PokemonItemSense.Companion.PICKUP_ITEMS
import com.cobblemon.mod.common.api.ai.config.objective.ObjectiveConfig
import com.cobblemon.mod.common.api.ai.resolveInt
import com.cobblemon.mod.common.api.molang.MoLangFunctions.asMostSpecificMoLangValue
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.pokemon.ai.ObtainableItem
import com.cobblemon.mod.common.util.asExpression
import com.cobblemon.mod.common.util.findMatchingEntry
import com.cobblemon.mod.common.util.getObjectList
import com.cobblemon.mod.common.util.mainThreadRuntime
import com.cobblemon.mod.common.util.resolve
import com.cobblemon.mod.common.util.withQueryValue
import com.mojang.datafixers.util.Either
import net.minecraft.core.component.DataComponents
import net.minecraft.core.particles.ItemParticleOption
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.server.level.ServerLevel
import net.minecraft.sounds.SoundEvents
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.item.ItemStack

/**
 * Eat the currently held item, apply the effect, and drop if it's an undesired item
 *
 * @author JazzMcNade
 * @since February 16th, 2026
 */
class EatHeldItem(
    val cooldownTicks: Int,
    val pickupItems: List<ObtainableItem>
) : Objective {
    var fixation: EntityFixation? = null
    var remainingCooldownTicks = cooldownTicks

    override fun getDescription(entity: LivingEntity) = "Eat Held Item"

    override fun perform(
        entity: LivingEntity,
        brain: BiggerBrain,
        activeObjectiveTags: MutableSet<String>
    ): ObjectiveStatus {
        if (brain.isFixation(fixation)) {
            return ObjectiveStatus.FIXATED
        } else if (entity !is PokemonEntity) {
            return ObjectiveStatus.INVALID
        } else if (remainingCooldownTicks > 0) {
            remainingCooldownTicks--
            return ObjectiveStatus.INACTIVE
        }
        val itemStack: ItemStack = entity.pokemon.heldItem()
        // FORCE so it still takes over nearly anything, as before, but respects fixations that refuse everything.
        if (canEat(itemStack, entity) && brain.distract(Distraction.FORCE)) {
            fixation = brain.fixate(EatHeldItemFixation(this))
            remainingCooldownTicks = 120
            return ObjectiveStatus.FIXATED
        }
        return ObjectiveStatus.INACTIVE
    }

    fun canEat(item: ItemStack, entity: PokemonEntity): Boolean {
        return item.has(DataComponents.FOOD) || pickupItems.findMatchingEntry(entity.registryAccess(), item)?.onUseEffect != null
    }

    class EatHeldItemFixation(val objective: EatHeldItem) : EntityFixation {
        var ticksRemaining = 40
        override fun getDescription(entity: LivingEntity) = "Eating held item"
        override fun canDistract(distraction: Distraction, entity: LivingEntity) = distraction.isComfort || distraction.isDamage || distraction.isForce
        override fun tick(entity: LivingEntity, brain: BiggerBrain): Boolean {
            val world = entity.level()
            if (entity is PokemonEntity && entity.isAlive) {
                val itemStack: ItemStack = entity.pokemon.heldItem()
                val itemConfig = objective.pickupItems.findMatchingEntry(entity.registryAccess(), itemStack)
                if (objective.canEat(itemStack, entity)) {
                    if (ticksRemaining < 0) {
                        var resultItemStack = itemStack.finishUsingItem(world, entity)
                        val configuredReturnItem = itemConfig?.returnItem
                        resultItemStack = if (configuredReturnItem != ItemStack.EMPTY) ItemStack(BuiltInRegistries.ITEM.get(configuredReturnItem))
                        else if (resultItemStack.item == itemStack.item)
                            ItemStack.EMPTY // Items that aren't normally edible return themselves
                        else
                            resultItemStack
                        entity.pokemon.swapHeldItem(resultItemStack)
                        val onUseEffect = itemConfig?.onUseEffect
                        if (onUseEffect != null) {
                            mainThreadRuntime.withQueryValue("entity", entity.asMostSpecificMoLangValue())
                            mainThreadRuntime.resolve(onUseEffect)
                        }
                        if (itemConfig != null && itemConfig.fullnessValue > 0) {
                            entity.pokemon.feedPokemon(itemConfig.fullnessValue, false)
                        }
                        // Check if the result item is something that should be dropped
                        resultItemStack = entity.pokemon.heldItem()
                        if ((objective.pickupItems.findMatchingEntry(entity.registryAccess(), resultItemStack)?.pickupPriority ?: 0) < 0) {
                            // Drop item
                            resultItemStack = entity.pokemon.swapHeldItem(resultItemStack)
                            if (!resultItemStack.isEmpty && !entity.level().isClientSide) {
                                dropItem(entity, resultItemStack)
                            }
                        }
                        return false
                    } else if ((itemConfig?.fullnessValue ?: 0) > 0 && entity.random.nextFloat() < 0.4f) {
                        ticksRemaining--
                        entity.playSound(entity.getEatingSound(itemStack), 1.0f, 1.0f)
                        world.broadcastEntityEvent(entity, 45.toByte())
                        spawnFoodParticles(entity, itemStack)
                    }
                }
                return true
            }
            return false
        }

        private fun spawnFoodParticles(entity: LivingEntity, itemStack: ItemStack) {
            val serverLevel = entity.level() as ServerLevel
            //TODO: Figure out how to this with snowstorm so we can use mouth/face/head locators
            // The issue as of this writing is we don't have a clean way to utilize the item texture for the particle in snowstorm
            serverLevel.sendParticles(
                ItemParticleOption(ParticleTypes.ITEM, itemStack),
                entity.x, entity.y + 0.5, entity.z, // particle position (centered on entity)
                5, // count
                0.1, 0.1, 0.1, // x, y, z offset spread
                0.05 // speed
            )
        }
    }

    class Config : ObjectiveConfig {
        var cooldownTicks: ExpressionOrEntityVariable = Either.left("120".asExpression())
        override fun getVariables(
            entity: LivingEntity,
            behaviourConfigurationContext: BehaviourConfigurationContext
        ) = listOf(
            cooldownTicks
        ).asVariables()

        override fun create(
            entity: LivingEntity,
            behaviourConfigurationContext: BehaviourConfigurationContext
        ): Objective? {
            var pickupItems = emptyList<ObtainableItem>()
            if (entity is PokemonEntity) {
                 pickupItems = entity.config.getObjectList<ObtainableItem>(PICKUP_ITEMS)
            }
            val cooldownTicks = cooldownTicks.resolveInt(behaviourConfigurationContext.runtime)
            return EatHeldItem(
                    cooldownTicks = cooldownTicks,
                    pickupItems = pickupItems
                )
        }
    }
}