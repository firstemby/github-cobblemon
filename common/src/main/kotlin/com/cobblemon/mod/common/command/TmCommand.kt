/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.command

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.api.permission.CobblemonPermissions
import com.cobblemon.mod.common.api.tms.TechnicalMachines
import com.cobblemon.mod.common.command.argument.TmArgumentType
import com.cobblemon.mod.common.util.permission
import com.mojang.brigadier.Command
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.context.CommandContext
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.commands.arguments.EntityArgument
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer

object TmCommand {

    private const val NAME = "technicalmachine"
    private const val UNLOCK = "unlock"
    private const val LOCK = "lock"
    private const val CHECK = "check"
    private const val PLAYER = "player"
    private const val ONLY = "only"
    private const val ALL = "all"
    private const val TM = "TM"

    fun register(dispatcher: CommandDispatcher<CommandSourceStack>) {
        val command =
            Commands.literal(NAME)
                .permission(CobblemonPermissions.TECHNICAL_MACHINE)
                .then(Commands.literal(UNLOCK)
                    .then(Commands.argument(PLAYER, EntityArgument.players())
                        .then(Commands.literal(ONLY)
                            .then(Commands.argument(TM, TmArgumentType.tm())
                                .executes { ctx ->
                                    executeUnlockOnly(ctx, EntityArgument.getPlayers(ctx, PLAYER))
                                }
                            )
                        )
                        .then(Commands.literal(ALL)
                            .executes { ctx ->
                                executeUnlockAll(ctx, EntityArgument.getPlayers(ctx, PLAYER))
                            }
                        )
                    )
                )
                .then(Commands.literal(LOCK)
                    .then(Commands.argument(PLAYER, EntityArgument.players())
                        .then(Commands.literal(ONLY)
                            .then(Commands.argument(TM, TmArgumentType.tm())
                                .executes { ctx ->
                                    executeLockOnly(ctx, EntityArgument.getPlayers(ctx, PLAYER))
                                }
                            )
                        )
                        .then(Commands.literal(ALL)
                            .executes { ctx ->
                                executeLockAll(ctx, EntityArgument.getPlayers(ctx, PLAYER))
                            }
                        )
                    )
                )
                .then(Commands.literal(CHECK)
                    .then(Commands.argument(PLAYER, EntityArgument.players())
                        .executes { ctx ->
                            executeCheck(ctx, EntityArgument.getPlayer(ctx, PLAYER))
                        }
                    )
                )

        dispatcher.register(command)
    }

    private fun executeUnlockOnly(context: CommandContext<CommandSourceStack>, players: Collection<ServerPlayer>) : Int {
        val tm = TmArgumentType.getTm(context, TM)

        players.forEach { player ->
            val playerTMData = Cobblemon.playerDataManager.getTMData(player.uuid)
            playerTMData.learn(listOf(tm.id))
        }

        val selectorStr =
            if (players.size == 1) players.first().name.string
            else "${players.size} players"

        context.source.sendSystemMessage(
            Component.literal("Unlocked TM ${tm.id} for $selectorStr")
        )

        return Command.SINGLE_SUCCESS
    }

    private fun executeLockOnly(context: CommandContext<CommandSourceStack>, players: Collection<ServerPlayer>) : Int {
        val tm = TmArgumentType.getTm(context, TM)

        players.forEach { player ->
            val playerTMData = Cobblemon.playerDataManager.getTMData(player.uuid)
            playerTMData.unlearn(listOf(tm.id))
        }

        val selectorStr =
            if (players.size == 1) players.first().name.string
            else "${players.size} players"

        context.source.sendSystemMessage(
            Component.literal("Locked TM ${tm.id} for $selectorStr")
        )

        return Command.SINGLE_SUCCESS
    }

    private fun executeUnlockAll(context: CommandContext<CommandSourceStack>, players: Collection<ServerPlayer>) : Int {
        val allTms = TechnicalMachines.getAllResourceLocations()

        players.forEach { player ->
            val playerTMData = Cobblemon.playerDataManager.getTMData(player.uuid)
            playerTMData.learn(allTms)
        }

        val selectorStr =
            if (players.size == 1) players.first().name.string
            else "${players.size} players"

        context.source.sendSystemMessage(
            Component.literal("Unlocked ${allTms.size} TMs for $selectorStr")
        )

        return Command.SINGLE_SUCCESS
    }

    private fun executeLockAll(context: CommandContext<CommandSourceStack>, players: Collection<ServerPlayer>) : Int {
        val allTms = TechnicalMachines.getAllResourceLocations()

        players.forEach { player ->
            val playerTMData = Cobblemon.playerDataManager.getTMData(player.uuid)
            playerTMData.unlearn(allTms)
        }

        val selectorStr =
            if (players.size == 1) players.first().name.string
            else "${players.size} players"

        context.source.sendSystemMessage(
            Component.literal("Locked ${allTms.size} TMs for $selectorStr")
        )

        return Command.SINGLE_SUCCESS
    }

    private fun executeCheck(context: CommandContext<CommandSourceStack>, player: ServerPlayer) : Int {
        val playerTMData = Cobblemon.playerDataManager.getTMData(player.uuid)
        val playerPc = Cobblemon.storage.getPC(player)
        val playerParty = Cobblemon.storage.getParty(player)
        val allPlayerPokemon = playerPc.toList() + playerParty.toList()

        val movesToLearn = mutableSetOf<ResourceLocation>()
        for (pokemon in allPlayerPokemon) {
            val learnableMoves = playerTMData.getLearnableTMsFromPokemon(pokemon)
            movesToLearn.addAll(learnableMoves)
        }

        playerTMData.learn(movesToLearn)

        context.source.sendSystemMessage(
            Component.literal("Unlocked ${movesToLearn.size} TMs for ${player.name.string}")
        )

        return Command.SINGLE_SUCCESS
    }
}