/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.pokedex.photo

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.api.pokedex.PokedexEntryProgress
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import com.cobblemon.mod.common.item.components.PrintedPhotoComponent
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer

object PrintedPhotoPokedexScanner {
    fun grantSeenEntries(player: ServerPlayer, component: PrintedPhotoComponent): Int {
        val pokedex = Cobblemon.playerDataManager.getPokedexData(player)
        var newlySeen = 0

        component.detectedSpecies
            .asSequence()
            .mapNotNull(ResourceLocation::tryParse)
            .distinct()
            .forEach { speciesId ->
                if (pokedex.getKnowledgeForSpecies(speciesId) >= PokedexEntryProgress.SEEN) {
                    return@forEach
                }
                val species = PokemonSpecies.getByIdentifier(speciesId) ?: return@forEach
                pokedex.encounter(species.create(level = 1))
                newlySeen++
            }

        return newlySeen
    }

    fun sendResultMessage(player: ServerPlayer, newlySeen: Int, totalSpecies: Int) {
        val message = if (totalSpecies <= 0) {
            Component.translatable("cobblemon.ui.pokedex.photo_scan.empty")
        } else if (newlySeen > 0) {
            Component.translatable("cobblemon.ui.pokedex.photo_scan.success", newlySeen, totalSpecies)
        } else {
            Component.translatable("cobblemon.ui.pokedex.photo_scan.already_seen", totalSpecies)
        }
        player.displayClientMessage(message, true)
    }
}
