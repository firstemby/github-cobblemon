/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.command

import com.cobblemon.mod.common.api.permission.CobblemonPermissions
import com.cobblemon.mod.common.command.argument.MoveArgumentType
import com.cobblemon.mod.common.item.components.TMMoveComponent
import com.cobblemon.mod.common.util.alias
import com.cobblemon.mod.common.util.permission
import com.mojang.brigadier.Command
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.arguments.IntegerArgumentType
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.commands.arguments.EntityArgument
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer

object GiveTmCommand {

    private const val NAME = "givetm"
    private const val ALIAS = "giveTM"
    private const val PLAYER = "player"
    private const val MOVE = "move"
    private const val QUANTITY = "quantity"

    fun register(dispatcher: CommandDispatcher<CommandSourceStack>) {
        val command = Commands.literal(NAME)
            .permission(CobblemonPermissions.TECHNICAL_MACHINE)
            .then(
                Commands.argument(MOVE, MoveArgumentType.move())
                    .executes { ctx ->
                        execute(ctx, ctx.source.playerOrException, 1)
                    }
                    .then(
                        Commands.argument(QUANTITY, IntegerArgumentType.integer(1, 2304))
                            .executes { ctx ->
                                execute(ctx, ctx.source.playerOrException, IntegerArgumentType.getInteger(ctx, QUANTITY))
                            }
                    )
                    .then(
                        Commands.argument(PLAYER, EntityArgument.player())
                            .executes { ctx ->
                                execute(ctx, EntityArgument.getPlayer(ctx, PLAYER), 1)
                            }
                            .then(
                                Commands.argument(QUANTITY, IntegerArgumentType.integer(1, 2304))
                                    .executes { ctx ->
                                        execute(
                                            ctx,
                                            EntityArgument.getPlayer(ctx, PLAYER),
                                            IntegerArgumentType.getInteger(ctx, QUANTITY)
                                        )
                                    }
                            )
                    )
            )

        val node = dispatcher.register(command)
        dispatcher.register(node.alias(ALIAS))
    }

    private fun execute(context: CommandContext<CommandSourceStack>, player: ServerPlayer, quantity: Int): Int {
        val move = MoveArgumentType.getMove(context, MOVE)

        var remaining = quantity
        while (remaining > 0) {
            val stackSize = remaining.coerceAtMost(64)
            val stack = TMMoveComponent.createStack(move).copyWithCount(stackSize)
            if (!player.addItem(stack)) {
                player.drop(stack, false)
            }
            remaining -= stackSize
        }

        context.source.sendSuccess(
            { Component.literal("Gave $quantity TM ${move.displayName.string} to ${player.name.string}") },
            true
        )
        return Command.SINGLE_SUCCESS
    }
}
