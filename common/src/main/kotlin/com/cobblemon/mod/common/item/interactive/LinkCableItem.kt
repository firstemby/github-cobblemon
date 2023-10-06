/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.item.interactive

import com.cobblemon.mod.common.api.interaction.PokemonEntityInteraction
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.item.CobblemonItem
import com.cobblemon.mod.common.pokemon.transformation.triggers.TradeTrigger
import net.minecraft.world.item.ItemStack
import net.minecraft.server.level.ServerPlayer

class LinkCableItem : CobblemonItem(Properties()), PokemonEntityInteraction {
    override val accepted = setOf(PokemonEntityInteraction.Ownership.OWNER)
    override fun processInteraction(player: ServerPlayer, entity: PokemonEntity, stack: ItemStack): Boolean {
        val pokemon = entity.pokemon
        val transformed = pokemon.transformationTriggers<TradeTrigger>().map { (_, transformation) -> transformation.start(pokemon) }
        if (transformed.any()) {
            this.consumeItem(player, stack)
            return true
        }
        return false
    }

}