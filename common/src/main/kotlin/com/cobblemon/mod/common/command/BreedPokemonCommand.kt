/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.command

import com.cobblemon.mod.common.api.breeding.CobblemonBreeding
import com.cobblemon.mod.common.api.permission.CobblemonPermissions
import com.cobblemon.mod.common.api.text.red
import com.cobblemon.mod.common.command.argument.PartySlotArgumentType
import com.cobblemon.mod.common.pokemon.Pokemon
import com.cobblemon.mod.common.util.commandLang
import com.cobblemon.mod.common.util.giveOrDropItemStack
import com.cobblemon.mod.common.util.permission
import com.cobblemon.mod.common.util.player
import com.mojang.brigadier.Command
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.exceptions.Dynamic2CommandExceptionType
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.commands.arguments.EntityArgument
import net.minecraft.server.level.ServerPlayer

object BreedPokemonCommand {

    private const val NAME = "breedpokemon"
    private const val PLAYER = "player"
    private const val SLOT_1 = "slot_1"
    private const val SLOT_2 = "slot_2"
    private val INCOMPATIBLE_EXCEPTION = Dynamic2CommandExceptionType { a, b -> commandLang("$NAME.cannot_breed", a, b).red() }
    private val MUST_SPECIFY_PLAYER_EXCEPTION = SimpleCommandExceptionType(commandLang("$NAME.must_specify_player").red())

    fun register(dispatcher : CommandDispatcher<CommandSourceStack>) {
        val command = Commands.literal(NAME)
            .permission(CobblemonPermissions.BREED)
            .then(Commands.argument(PLAYER, EntityArgument.player())
                .then(Commands.argument(SLOT_1, PartySlotArgumentType.partySlot())
                    .then(Commands.argument(SLOT_2, PartySlotArgumentType.partySlot())
                        .executes { execute(it, it.player()) }
                    ))
            )
            .then(Commands.argument(SLOT_1, PartySlotArgumentType.partySlot())
                .then(Commands.argument(SLOT_2, PartySlotArgumentType.partySlot())
                    .executes { execute(it, null) }
                ))
        dispatcher.register(command)
    }

    private fun execute(context: CommandContext<CommandSourceStack>, player: ServerPlayer?) : Int {
        val player = player ?: context.source.player ?: throw MUST_SPECIFY_PLAYER_EXCEPTION.create()
        val parent1 = PartySlotArgumentType.getPokemonOf(context, SLOT_1, player)
        val parent2 = PartySlotArgumentType.getPokemonOf(context, SLOT_2, player)

        val offspring: Pokemon
        try {
            offspring = CobblemonBreeding.getOffspring(parent1, parent2, isSimulated = false)
                ?: throw INCOMPATIBLE_EXCEPTION.create(parent1.getDisplayName(), parent2.getDisplayName())
        } catch (e: Exception) {
            e.printStackTrace()
            return 0
        }
        val eggItem = offspring.createEgg(parent1, parent2)

        player.giveOrDropItemStack(eggItem)

        val bredMessage = commandLang("$NAME.success", parent1.getDisplayName(), parent2.getDisplayName())

        context.source.sendSuccess({ bredMessage }, true)

        if (context.source.player?.equals(player) != true) {
            player.sendSystemMessage(bredMessage)
        }

        return Command.SINGLE_SUCCESS
    }

}