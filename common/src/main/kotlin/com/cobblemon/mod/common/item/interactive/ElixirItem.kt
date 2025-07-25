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
import com.cobblemon.mod.common.api.item.PokemonSelectingItem
import com.cobblemon.mod.common.battles.pokemon.BattlePokemon
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.item.CobblemonItem
import com.cobblemon.mod.common.item.battle.BagItem
import com.cobblemon.mod.common.pokemon.Pokemon
import com.cobblemon.mod.common.util.getBattleState
import com.cobblemon.mod.common.util.giveOrDropItemStack
import kotlin.math.min
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.server.level.ServerPlayer
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.InteractionResultHolder
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.item.ItemNameBlockItem
import net.minecraft.world.item.Rarity
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Block

/**
 * Items for recovering PP in all moves at once.
 *
 * @author Hiroku
 * @since June 30th, 2023
 */
class ElixirItem(
    val max: Boolean, block: Block
) : ItemNameBlockItem(block, Properties().apply {
    if (max) rarity(Rarity.UNCOMMON)
}), PokemonSelectingItem {
    override val bagItem = object : BagItem {
        override val itemName = "item.cobblemon.${ if (max) "max_elixir" else "elixir" }"
        override val returnItem = Items.GLASS_BOTTLE
        override fun canUse(stack: ItemStack, battle: PokemonBattle, target: BattlePokemon) = target.health > 0 && target.moveSet.any { it.currentPp < it.maxPp }
        override fun getShowdownInput(actor: BattleActor, battlePokemon: BattlePokemon, data: String?) = "elixir".let { if (!max) "$it 10" else it }
    }

    override fun canUseOnPokemon(stack: ItemStack, pokemon: Pokemon) = pokemon.moveSet.any { it.currentPp < it.maxPp }
    override fun applyToPokemon(player: ServerPlayer, stack: ItemStack, pokemon: Pokemon): InteractionResultHolder<ItemStack> {
        var changed = false
        pokemon.moveSet.doWithoutEmitting {
            pokemon.moveSet.getMoves().forEach {
                if (it.currentPp < it.maxPp) {
                    if (max) {
                        it.currentPp = it.maxPp
                    } else {
                        it.currentPp = min(it.currentPp + 10, it.maxPp)
                    }
                    changed = true
                }
            }
        }

        return if (changed) {
            pokemon.moveSet.update()
            if (!player.hasInfiniteMaterials()) {
                stack.shrink(1)
                player.giveOrDropItemStack(ItemStack(bagItem.returnItem))
            }
            pokemon.entity?.playSound(CobblemonSounds.MEDICINE_LIQUID_USE, 1F, 1F)
            InteractionResultHolder.success(stack)
        } else {
            InteractionResultHolder.fail(stack)
        }
    }

    override fun applyToBattlePokemon(player: ServerPlayer, stack: ItemStack, battlePokemon: BattlePokemon) {
        super.applyToBattlePokemon(player, stack, battlePokemon)
        battlePokemon.entity?.playSound(CobblemonSounds.MEDICINE_LIQUID_USE, 1F, 1F)
    }

    override fun use(world: Level, user: Player, hand: InteractionHand): InteractionResultHolder<ItemStack> {
        val stack = user.getItemInHand(hand)

        if (user is ServerPlayer) {
            // Only invoke PokemonSelectingItem menu if we are in battle
            if (user.getBattleState() != null) {
                val result = use(user, stack)
                if (result.result != InteractionResult.PASS)
                    return result
            }
        }

        return super<ItemNameBlockItem>.use(world, user, hand)
    }

    override fun interactLivingEntity(
        stack: ItemStack,
        player: Player,
        target: LivingEntity,
        hand: InteractionHand
    ): InteractionResult {
        if (player.level().isClientSide) return InteractionResult.PASS

        if (player is ServerPlayer && target is PokemonEntity) {
            val pokemon = target.pokemon
            if (canUseOnPokemon(stack, pokemon)) {
                val result = applyToPokemon(player, stack, pokemon)
                if (result?.result == InteractionResult.SUCCESS) {
                    return InteractionResult.SUCCESS
                }
            }
            return InteractionResult.FAIL
        }

        return InteractionResult.PASS
    }
}