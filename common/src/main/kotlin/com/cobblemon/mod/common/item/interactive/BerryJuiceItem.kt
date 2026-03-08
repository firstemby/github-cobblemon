/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.item.interactive

import com.cobblemon.mod.common.CobblemonSounds
import com.cobblemon.mod.common.api.battles.model.PokemonBattle
import com.cobblemon.mod.common.api.battles.model.actor.BattleActor
import com.cobblemon.mod.common.api.events.CobblemonEvents
import com.cobblemon.mod.common.api.events.pokemon.healing.PokemonHealedEvent
import com.cobblemon.mod.common.api.item.HealingSource
import com.cobblemon.mod.common.api.item.PokemonSelectingItem
import com.cobblemon.mod.common.battles.pokemon.BattlePokemon
import com.cobblemon.mod.common.item.CobblemonItem
import com.cobblemon.mod.common.item.battle.BagItem
import com.cobblemon.mod.common.pokemon.Pokemon
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.level.Level
import net.minecraft.world.item.Items

class BerryJuiceItem : CobblemonItem(Properties()), PokemonSelectingItem, HealingSource {
    override val bagItem = object : BagItem {
        override val itemName = "item.cobblemon.berry_juice"
        override val returnItem = Items.BOWL
        override fun getShowdownInput(actor: BattleActor, battlePokemon: BattlePokemon, data: String?) = "potion 20"
        override fun canUse(stack: ItemStack, battle: PokemonBattle, target: BattlePokemon) =  target.health < target.maxHealth && target.health > 0
    }

    override fun canUseOnPokemon(stack: ItemStack, pokemon: Pokemon) = !pokemon.isFullHealth() && pokemon.currentHealth > 0
            && super.canUseOnPokemon(stack, pokemon)

    override fun use(world: Level, user: Player, hand: InteractionHand): InteractionResult {
        if (user is ServerPlayer) {
            return use(user, user.getItemInHand(hand))
        }
        return InteractionResult.SUCCESS
    }

    override fun applyToPokemon(
        player: ServerPlayer,
        stack: ItemStack,
        pokemon: Pokemon
    ): InteractionResult? {
        if (!canUseOnPokemon(stack, pokemon)) {
            return InteractionResult.FAIL
        }
        pokemon.feedPokemon(1)

        var amount = Integer.min(pokemon.currentHealth + 20, pokemon.maxHealth)
        CobblemonEvents.POKEMON_HEALED.postThen(PokemonHealedEvent(pokemon, amount, this), { cancelledEvent -> return InteractionResult.FAIL}) { event ->
            amount = event.amount
        }
        pokemon.currentHealth = amount
        player.playSound(CobblemonSounds.BERRY_EAT, 1F, 1F)
        if (!player.hasInfiniteMaterials())  {
            stack.shrink(1)
            val woodenBowlItemStack = ItemStack(Items.BOWL)
            if (!player.inventory.add(woodenBowlItemStack)) {
                // Drop the item into the world if the inventory is full
                player.drop(woodenBowlItemStack, false)
            }
        }
        return InteractionResult.SUCCESS
    }

    override fun applyToBattlePokemon(player: ServerPlayer, stack: ItemStack, battlePokemon: BattlePokemon) {
        super.applyToBattlePokemon(player, stack, battlePokemon)
        battlePokemon.originalPokemon.feedPokemon(1)
        if (!player.hasInfiniteMaterials())  {
            val woodenBowlItemStack = ItemStack(Items.BOWL)
            if (!player.inventory.add(woodenBowlItemStack)) {
                // Drop the item into the world if the inventory is full
                player.drop(woodenBowlItemStack, false)
            }
        }
    }
}
