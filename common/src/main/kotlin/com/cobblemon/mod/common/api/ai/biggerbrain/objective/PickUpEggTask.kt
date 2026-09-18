/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.ai.biggerbrain.objective

import com.cobblemon.mod.common.CobblemonItemComponents
import com.cobblemon.mod.common.CobblemonItems
import com.cobblemon.mod.common.CobblemonMemories
import com.cobblemon.mod.common.api.ai.biggerbrain.BiggerBrain
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import net.minecraft.core.BlockPos
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.phys.Vec3

class PickUpEggTask : Objective {
    override fun getDescription(entity: LivingEntity) = "Pick up egg"

    override fun perform(
        entity: LivingEntity,
        brain: BiggerBrain,
        activeObjectiveTags: MutableSet<String>
    ): ObjectiveStatus {
        if (entity !is PokemonEntity || !entity.isAlive) return ObjectiveStatus.INVALID

        if (brain.getMemory(CobblemonMemories.READY_TO_GRAB_EGG) != true) return ObjectiveStatus.INVALID
        val eggInNest = brain.getMemory(CobblemonMemories.EGG_IN_NEST)
        if (eggInNest == true) {
            brain.sendDebugMessage("[PickUpEgg] Egg already in nest.")
            return ObjectiveStatus.INVALID
        }

        val eggPos: BlockPos = brain.getMemory(CobblemonMemories.EGG_LOCATION) ?: return ObjectiveStatus.INVALID
        val eggCenter = Vec3.atCenterOf(eggPos)

        if (entity.distanceToSqr(eggCenter) > 2.0) {
            brain.sendDebugMessage("[PickUpEgg] Too far from egg at $eggPos; clearing ready flag.")
            brain.eraseMemory(CobblemonMemories.READY_TO_GRAB_EGG)
            return ObjectiveStatus.INACTIVE
        }

        val nearbyEggs = entity.level().getEntitiesOfClass(ItemEntity::class.java, entity.boundingBox.inflate(1.5)) { itemEntity ->
            itemEntity.blockPosition() == eggPos && itemEntity.item.item == CobblemonItems.POKEMON_EGG
        }
        val eggItem = nearbyEggs.firstOrNull() ?: return ObjectiveStatus.INVALID

        val eggComponent = eggItem.item.get(CobblemonItemComponents.EGG_DATA) ?: return ObjectiveStatus.INVALID
        val isParent = eggComponent.isParent(entity.pokemon.uuid)
        if (!isParent) {
            brain.sendDebugMessage("[PickUpEgg] Egg at $eggPos does not belong to this Pokémon.")
            return ObjectiveStatus.INVALID
        }

        entity.setItemInHand(InteractionHand.MAIN_HAND, eggItem.item.copy())
        eggItem.remove(Entity.RemovalReason.DISCARDED)

        brain.eraseMemory(CobblemonMemories.EGG_LOCATION)
        brain.setMemory(CobblemonMemories.HOLDING_EGG, true)
        brain.eraseMemory(CobblemonMemories.EGG_SEARCH_COUNTDOWN)
        brain.eraseMemory(CobblemonMemories.READY_TO_GRAB_EGG)

        brain.sendDebugMessage("[PickUpEgg] Picked up egg from $eggPos.")
        return ObjectiveStatus.ACTIVE
    }
}
