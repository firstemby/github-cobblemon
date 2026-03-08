/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.item

import com.cobblemon.mod.common.api.item.PokemonSelectingItem
import com.cobblemon.mod.common.pokemon.Pokemon
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvent
import net.minecraft.sounds.SoundEvents
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.ItemUseAnimation
import net.minecraft.world.level.Level

class RegionalFoodItem(properties: Properties) : Item(properties), PokemonSelectingItem {
    override val bagItem = null

    override fun use(world: Level, player: Player, hand: InteractionHand): InteractionResult {
        val stack = player.getItemInHand(hand)

        if (player !is ServerPlayer) {
            return InteractionResult.PASS
        }

        // Prioritizes healing pokémon with the item
        val superInteractionResult = super<PokemonSelectingItem>.use(player, stack)
        if (superInteractionResult != InteractionResult.PASS) {
            return superInteractionResult
        }

        // Otherwise allow eating normally if player needs food OR in creative
        if (player.foodData.needsFood() || player.isCreative) {
            player.startUsingItem(hand)
            return InteractionResult.CONSUME
        }

        return InteractionResult.PASS
    }

    override fun applyToPokemon(
        player: ServerPlayer,
        stack: ItemStack,
        pokemon: Pokemon
    ): InteractionResult {
        if (pokemon.status != null) {
            pokemon.status = null
            pokemon.entity?.playSound(SoundEvents.GENERIC_EAT.value(), 1F, 1F)
            stack.consume(1, player)
            return InteractionResult.SUCCESS
        }

        return InteractionResult.FAIL
    }

    override fun getUseAnimation(stack: ItemStack): ItemUseAnimation = ItemUseAnimation.EAT

    override fun getUseDuration(stack: ItemStack, entity: LivingEntity): Int = 32

    override fun canUseOnPokemon(stack: ItemStack, pokemon: Pokemon): Boolean {
        return pokemon.status != null && pokemon.currentHealth > 0
    }
}
