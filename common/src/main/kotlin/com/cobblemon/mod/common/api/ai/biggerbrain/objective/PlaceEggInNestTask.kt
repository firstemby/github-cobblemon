/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.ai.biggerbrain.objective

import com.cobblemon.mod.common.CobblemonItems
import com.cobblemon.mod.common.CobblemonMemories
import com.cobblemon.mod.common.api.ai.biggerbrain.BiggerBrain
import com.cobblemon.mod.common.api.ai.biggerbrain.objective.Objective
import com.cobblemon.mod.common.api.ai.biggerbrain.objective.ObjectiveStatus
import com.cobblemon.mod.common.api.text.green
import com.cobblemon.mod.common.block.entity.NestBlockEntity
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.PathfinderMob
import net.minecraft.world.item.ItemStack
import net.minecraft.world.phys.Vec3

class PlaceEggInNestTask : Objective {
    override fun getDescription(entity: LivingEntity) = "Place egg in nest"

    override fun perform(
        entity: LivingEntity,
        brain: BiggerBrain,
        activeObjectiveTags: MutableSet<String>
    ): ObjectiveStatus {
        if (entity !is PokemonEntity || entity !is PathfinderMob || !entity.isAlive) return ObjectiveStatus.INVALID

        if (brain.getMemory(CobblemonMemories.HOLDING_EGG) != true) return ObjectiveStatus.INVALID
        val eggInNest = brain.getMemory(CobblemonMemories.EGG_IN_NEST)
        if (eggInNest != null && eggInNest == true) {
            brain.sendDebugMessage("[PlaceEgg] Egg already in nest.")
            return ObjectiveStatus.INVALID
        }

        val nestLocation: BlockPos = brain.getMemory(CobblemonMemories.PATERNAL_NEST_LOCATION) ?: return ObjectiveStatus.INVALID
        val world = entity.level() as? ServerLevel ?: return ObjectiveStatus.INVALID
        val nestEntity = world.getBlockEntity(nestLocation) as? NestBlockEntity ?: return ObjectiveStatus.INVALID

        val targetVec = Vec3.atCenterOf(nestLocation)
        if (entity.distanceToSqr(targetVec) > 2.0) {
            brain.sendDebugMessage("[PlaceEgg] Too far from nest $nestLocation.")
            return ObjectiveStatus.INACTIVE
        }

        val heldItem = entity.getItemInHand(InteractionHand.MAIN_HAND)
        if (heldItem.isEmpty || heldItem.item.asItem() != CobblemonItems.POKEMON_EGG.asItem()) {
            brain.sendDebugMessage("[PlaceEgg] No egg in hand.")
            return ObjectiveStatus.INVALID
        }

        if (nestEntity.getItem(0) != ItemStack.EMPTY) {
            brain.sendDebugMessage("[PlaceEgg] Nest at $nestLocation already has an egg.")
            return ObjectiveStatus.INVALID
        }

        nestEntity.setItem(heldItem.copy())
        entity.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY)

        brain.eraseMemory(CobblemonMemories.HOLDING_EGG)
        brain.setMemory(CobblemonMemories.EGG_IN_NEST, true)
        brain.sendDebugMessage("[PlaceEgg] Egg placed in nest at $nestLocation.")

        world.players().forEach { player ->
            player.displayClientMessage("The Pokémon placed an egg in its nest!".green(), true)
        }

        return ObjectiveStatus.ACTIVE
    }
}
