/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.item.interactive

import com.cobblemon.mod.common.CobblemonSounds
import com.cobblemon.mod.common.api.item.PokemonSelectingItem
import com.cobblemon.mod.common.item.CobblemonItem
import com.cobblemon.mod.common.pokemon.Nature
import com.cobblemon.mod.common.pokemon.Pokemon
import com.cobblemon.mod.common.util.lang
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.level.Level

class MintItem(val nature: Nature, properties: Properties) : CobblemonItem(properties), PokemonSelectingItem {

    override val bagItem = null
    override fun canUseOnPokemon(stack: ItemStack, pokemon: Pokemon) = pokemon.effectiveNature != nature
    override fun applyToPokemon(
        player: ServerPlayer,
        stack: ItemStack,
        pokemon: Pokemon
    ): InteractionResult {
        return if (pokemon.effectiveNature != nature) {
            stack.consume(1, player)
            pokemon.entity?.playSound(CobblemonSounds.MEDICINE_HERB_USE, 1F, 1F)
            pokemon.mintedNature = nature
            player.sendSystemMessage(lang("mint.interact", pokemon.getDisplayName(), stack.hoverName), true)
            InteractionResult.SUCCESS
        } else {
            player.sendSystemMessage(lang("mint.same_nature", pokemon.getDisplayName(), stack.hoverName), true)
            InteractionResult.FAIL
        }
    }

    override fun use(world: Level, user: Player, hand: InteractionHand): InteractionResult {
        if (user is ServerPlayer) {
            return use(user, user.getItemInHand(hand))
        }
        return InteractionResult.SUCCESS
    }
}
