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
import com.cobblemon.mod.common.pokemon.helditem.CobblemonHeldItemManager
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack

object MegaImprinting {

    fun increaseBond(pokemon: Pokemon, amount: Int) {
        // exit early if it is on the client, friendship is not a positive numbah or if the item held isn't a dormant megga stone
        if (pokemon.isClient || amount <= 0 || !pokemon.heldItemNoCopy().`is`(CobblemonItems.DORMANT_MEGA_STONE)) {
            return
        }

        val speciesId = pokemon.species.showdownId()
        if (!HeldItems.megaStones.containsValue(speciesId)) {
            return
        }
        // keep the existing bond only if the stone is already tied to the current pokemon and if not start back at no bondd
        val existingBond = pokemon.heldItemNoCopy().get(CobblemonItemComponents.MEGA_BOND)?.takeIf { it.owner == pokemon.uuid }?.bond ?: 0
        val bond = (existingBond + amount).coerceAtMost(Cobblemon.config.maxPokemonFriendship)
        pokemon.heldItemNoCopy().set(CobblemonItemComponents.MEGA_BOND, MegaBondComponent(pokemon.uuid, bond))
        pokemon.onChange(HeldItemUpdatePacket({ pokemon }, pokemon.heldItemNoCopy()))
        convertToMegaStone(pokemon)
    }

    fun convertToMegaStone(pokemon: Pokemon): Item? {
        // putting this check here again because we may want to keep the command version of setting the mega bond
        // we may want to keep the client check too?
        if (!pokemon.heldItemNoCopy().`is`(CobblemonItems.DORMANT_MEGA_STONE)) {
            return null
        }
        val stoneMegaBond = pokemon.heldItemNoCopy().get(CobblemonItemComponents.MEGA_BOND) ?: return null
        // is the dormant stone already bonded with the current pokemon? Maybe it came from another pokemon and needs reset
        if (stoneMegaBond.owner != pokemon.uuid || stoneMegaBond.bond < Cobblemon.config.maxPokemonFriendship) {
            return null
        }
        val speciesId = pokemon.species.showdownId()
        // we gotta get the mega stone that it should turn into which is kind of a task
        val megaStone = HeldItems.megaStones.filterValues { it == speciesId }.keys.mapNotNull(::getCobblemonItemFromShowdownID).randomOrNull() ?: return null
        pokemon.swapHeldItem(ItemStack(megaStone), decrement = false)
        return megaStone
    }

    // The mega stones ids come from showdown I think so we cant look them up by a resource location directly
    fun getCobblemonItemFromShowdownID(showdownId: String): Item? = BuiltInRegistries.ITEM.firstOrNull { CobblemonHeldItemManager.showdownIdOf(it) == showdownId }
}