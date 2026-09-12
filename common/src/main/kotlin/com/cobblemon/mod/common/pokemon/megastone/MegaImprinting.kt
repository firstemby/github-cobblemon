/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.pokemon.megastone

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.CobblemonItemComponents
import com.cobblemon.mod.common.CobblemonItems
import com.cobblemon.mod.common.api.item.HeldItems
import com.cobblemon.mod.common.item.components.MegaBondComponent
import com.cobblemon.mod.common.net.messages.client.pokemon.update.HeldItemUpdatePacket
import com.cobblemon.mod.common.pokemon.Pokemon
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack

object MegaImprinting {

    fun onFriendshipGained(pokemon: Pokemon, amount: Int) {
        if (pokemon.isClient || amount <= 0) {
            return
        }
        val stack = pokemon.heldItemNoCopy()
        if (!stack.`is`(CobblemonItems.DORMANT_MEGA_STONE)) {
            return
        }
        val speciesId = normalizedSpeciesId(pokemon)
        if (!HeldItems.megaStones.containsValue(speciesId)) {
            return
        }
        val component = stack.get(CobblemonItemComponents.MEGA_BOND)
        val matched = matches(component, pokemon, speciesId)
        val bond = (if (matched) component!!.bond + amount else amount).coerceAtMost(Cobblemon.config.maxPokemonFriendship)
        val species = if (matched) component!!.species else null
        val owner = if (species == null) pokemon.uuid else null
        stack.set(CobblemonItemComponents.MEGA_BOND, MegaBondComponent(owner, species, bond))
        pokemon.onChange(HeldItemUpdatePacket({ pokemon }, stack))
        tryConvert(pokemon)
    }

    fun tryConvert(pokemon: Pokemon): Item? {
        if (pokemon.isClient) {
            return null
        }
        val stack = pokemon.heldItemNoCopy()
        if (!stack.`is`(CobblemonItems.DORMANT_MEGA_STONE)) {
            return null
        }
        val component = stack.get(CobblemonItemComponents.MEGA_BOND) ?: return null
        val speciesId = normalizedSpeciesId(pokemon)
        if (!matches(component, pokemon, speciesId) || component.bond < Cobblemon.config.maxPokemonFriendship) {
            return null
        }
        val stone = HeldItems.megaStones.filterValues { it == speciesId }.keys.mapNotNull(::stoneItem).randomOrNull() ?: return null
        pokemon.swapHeldItem(ItemStack(stone), decrement = false)
        return stone
    }

    fun normalizedSpeciesId(pokemon: Pokemon) = pokemon.species.resourceIdentifier.path.replace("_", "")

    private fun matches(component: MegaBondComponent?, pokemon: Pokemon, speciesId: String): Boolean {
        if (component == null) {
            return false
        }
        return if (component.species != null) component.species == speciesId else component.owner == pokemon.uuid
    }

    private fun stoneItem(showdownId: String): Item? = BuiltInRegistries.ITEM.firstOrNull {
        val key = BuiltInRegistries.ITEM.getKey(it)
        key.namespace == Cobblemon.MODID && key.path.replace("_", "") == showdownId
    }
}
