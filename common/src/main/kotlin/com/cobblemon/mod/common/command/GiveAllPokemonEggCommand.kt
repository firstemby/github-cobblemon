/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.command

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.CobblemonItemComponents
import com.cobblemon.mod.common.CobblemonItems.POKEMON_EGG
import com.cobblemon.mod.common.api.permission.CobblemonPermissions
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import com.cobblemon.mod.common.item.components.EggComponent
import com.cobblemon.mod.common.util.permission
import com.mojang.brigadier.Command
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.context.CommandContext
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands.literal
import net.minecraft.server.level.ServerPlayer

object GiveAllPokemonEggCommand {
    private const val NAME = "giveallpokemonegg"

    fun register(dispatcher : CommandDispatcher<CommandSourceStack>) {
        dispatcher.register(
            literal(NAME)
                .permission(CobblemonPermissions.GIVE_POKEMON_EGG)
                .executes { execute(it, it.source.playerOrException) }
        )
    }

    private fun execute(context: CommandContext<CommandSourceStack>, player: ServerPlayer): Int {
        val orderedSpecies = PokemonSpecies.implemented.sortedBy { it.nationalPokedexNumber }
        orderedSpecies.forEach { species ->
            if (!species.implemented || species.preEvolution != null) return@forEach

            val offspring = species.create()
            val egg = EggComponent(
                offspring = offspring,
                parent1 = null,
                parent2 = null,
                ticksToHatch = 0F
            )

            Cobblemon.eggStoreManager.add(egg.eggId, offspring)

            val eggItem = POKEMON_EGG.defaultInstance.apply {
                set(CobblemonItemComponents.EGG_DATA, egg)
            }

            if (!player.inventory.add(eggItem)) {
                player.drop(eggItem, false);
            }
        }

        return Command.SINGLE_SUCCESS
    }
}