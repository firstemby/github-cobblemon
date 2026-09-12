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
import com.cobblemon.mod.common.CobblemonItems
import com.cobblemon.mod.common.api.item.HeldItems
import com.cobblemon.mod.common.api.permission.CobblemonPermissions
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import com.cobblemon.mod.common.api.text.green
import com.cobblemon.mod.common.command.argument.PartySlotArgumentType
import com.cobblemon.mod.common.command.argument.SpeciesArgumentType
import com.cobblemon.mod.common.item.components.MegaBondComponent
import com.cobblemon.mod.common.net.messages.client.pokemon.update.HeldItemUpdatePacket
import com.cobblemon.mod.common.pokemon.Species
import com.cobblemon.mod.common.pokemon.megastone.MegaImprinting
import com.cobblemon.mod.common.util.commandLang
import com.cobblemon.mod.common.util.permission
import com.mojang.brigadier.Command
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.commands.Commands.literal
import net.minecraft.commands.SharedSuggestionProvider

object SetMegaBondCommand {
    const val NAME = "setmegabond"

    private val NOT_HOLDING = SimpleCommandExceptionType(commandLang("setmegabond.not_holding"))
    private val CANNOT_MEGA = DynamicCommandExceptionType { species -> commandLang("setmegabond.cannot_mega", species) }

    fun register(dispatcher: CommandDispatcher<CommandSourceStack>) {
        dispatcher.register(literal(NAME)
            .permission(CobblemonPermissions.SET_MEGA_BOND)
            .then(Commands.argument("slot", PartySlotArgumentType.partySlot())
                .then(Commands.argument("bond", IntegerArgumentType.integer(0))
                    .then(Commands.argument("species", SpeciesArgumentType.species())
                        .suggests { _, builder ->
                            val megaSpecies = HeldItems.megaStones.values.toSet()
                            SharedSuggestionProvider.suggest(
                                PokemonSpecies.species
                                    .filter { it.resourceIdentifier.path.replace("_", "") in megaSpecies }
                                    .map { if (it.resourceIdentifier.namespace == Cobblemon.MODID) it.resourceIdentifier.path else it.resourceIdentifier.toString() },
                                builder
                            )
                        }
                        .executes { context ->
                            execute(context, IntegerArgumentType.getInteger(context, "bond"), SpeciesArgumentType.getPokemon(context, "species"))
                        }
                    )
                    .executes { context -> execute(context, IntegerArgumentType.getInteger(context, "bond"), null) }
                )
            )
        )
    }

    private fun execute(context: CommandContext<CommandSourceStack>, bond: Int, species: Species?): Int {
        val pokemon = PartySlotArgumentType.getPokemon(context, "slot")
        val stack = pokemon.heldItemNoCopy()
        if (!stack.`is`(CobblemonItems.DORMANT_MEGA_STONE)) {
            throw NOT_HOLDING.create()
        }
        val speciesId = species?.resourceIdentifier?.path?.replace("_", "")
        if (speciesId != null && speciesId !in HeldItems.megaStones.values) {
            throw CANNOT_MEGA.create(species.translatedName)
        }
        val owner = if (speciesId == null) pokemon.uuid else null
        stack.set(CobblemonItemComponents.MEGA_BOND, MegaBondComponent(owner, speciesId, bond))
        pokemon.onChange(HeldItemUpdatePacket({ pokemon }, stack))
        val converted = MegaImprinting.tryConvert(pokemon)
        if (converted != null) {
            context.source.sendSystemMessage(commandLang("$NAME.converted", pokemon.getDisplayName(), converted.description).green())
        } else {
            context.source.sendSystemMessage(
                commandLang(NAME, pokemon.getDisplayName(), bond, speciesId ?: pokemon.getDisplayName().string).green()
            )
        }
        return Command.SINGLE_SUCCESS
    }
}
