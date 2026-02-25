/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.command

import com.cobblemon.mod.common.api.gui.scripted.ScriptedGuiRegistry
import com.cobblemon.mod.common.api.gui.scripted.ScriptedGuiSessionManager
import com.mojang.brigadier.Command
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.context.CommandContext
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.commands.arguments.ResourceLocationArgument
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation

/**
 * Test command for scripted GUIs.
 *
 * Usage:
 * /scriptedgui open <guiId> [args...] - Open a scripted GUI for the player
 * /scriptedgui close <sessionId> - Close a specific GUI session
 * /scriptedgui list - List all registered scripted GUIs
 */
object ScriptedGuiCommand {

    private const val GUI_ID_ARG = "guiId"

    fun register(dispatcher: CommandDispatcher<CommandSourceStack>) {
        dispatcher.register(
            Commands.literal("scriptedgui")
                .requires { it.hasPermission(2) }
                .then(
                    Commands.literal("open")
                        .then(
                            Commands.argument(GUI_ID_ARG, ResourceLocationArgument.id())
                                .suggests { _, builder ->
                                    ScriptedGuiRegistry.getAllDefinitions().forEach {
                                        builder.suggest(it.guiId.toString())
                                    }
                                    builder.buildFuture()
                                }
                                .executes { context ->
                                    executeOpen(context, ResourceLocationArgument.getId(context, GUI_ID_ARG))
                                }
                        )
                )
                .then(
                    Commands.literal("list")
                        .executes { executeList(it) }
                )
        )
    }

    private fun executeOpen(context: CommandContext<CommandSourceStack>, guiId: ResourceLocation): Int {
        val player = context.source.playerOrException

        val session = ScriptedGuiSessionManager.openGui(
            player = player,
            guiId = guiId,
            args = mapOf("test_arg" to "hello", "count" to "3")
        ) { action, data ->
            context.source.sendSuccess(
                { Component.literal("GUI action received: $action, data: $data") },
                false
            )
        }

        if (session != null) {
            context.source.sendSuccess(
                { Component.literal("Opened scripted GUI '$guiId' (session=${session.sessionId})") },
                false
            )
            return Command.SINGLE_SUCCESS
        } else {
            context.source.sendFailure(Component.literal("Failed to open scripted GUI '$guiId'"))
            return 0
        }
    }

    private fun executeList(context: CommandContext<CommandSourceStack>): Int {
        val definitions = ScriptedGuiRegistry.getAllDefinitions()

        if (definitions.isEmpty()) {
            context.source.sendSuccess(
                { Component.literal("No scripted GUIs registered.") },
                false
            )
        } else {
            context.source.sendSuccess(
                { Component.literal("Registered scripted GUIs (${definitions.size}):") },
                false
            )
            for (def in definitions) {
                context.source.sendSuccess(
                    { Component.literal("  - ${def.guiId} (mode=${def.mode}, params=${def.parameters.size})") },
                    false
                )
            }
        }

        return Command.SINGLE_SUCCESS
    }
}
