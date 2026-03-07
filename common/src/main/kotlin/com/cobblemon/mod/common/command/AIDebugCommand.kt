/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.command

import com.cobblemon.mod.common.api.text.green
import com.cobblemon.mod.common.api.text.red
import com.cobblemon.mod.common.api.text.yellow
import com.cobblemon.mod.common.api.text.aqua
import com.cobblemon.mod.common.api.text.text
import com.cobblemon.mod.common.battles.ai.debug.AIDebugServer
import com.mojang.brigadier.Command
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.context.CommandContext
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.network.chat.ClickEvent
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.HoverEvent

/**
 * Command to manage the AI Debug Server for visualizing ConfigurableBattleAI decisions.
 *
 * Usage:
 * - /aidebug start [port] - Start the debug server (default port 8765)
 * - /aidebug stop - Stop the debug server
 * - /aidebug status - Check server status
 * - /aidebug clear - Clear decision history
 *
 * @author Amo Aster
 * @since December 2025
 */
object AIDebugCommand {

    private const val DEFAULT_PORT = 8765

    fun register(dispatcher: CommandDispatcher<CommandSourceStack>) {
        val command = Commands.literal("aidebug")
            .requires { it.hasPermission(2) }
            .then(
                Commands.literal("start")
                    .executes { startServer(it, DEFAULT_PORT) }
                    .then(
                        Commands.argument("port", IntegerArgumentType.integer(1024, 65535))
                            .executes { startServer(it, IntegerArgumentType.getInteger(it, "port")) }
                    )
            )
            .then(
                Commands.literal("stop")
                    .executes(::stopServer)
            )
            .then(
                Commands.literal("status")
                    .executes(::checkStatus)
            )
            .then(
                Commands.literal("clear")
                    .executes(::clearHistory)
            )
            .executes(::showHelp)

        dispatcher.register(command)
    }

    private fun startServer(context: CommandContext<CommandSourceStack>, port: Int): Int {
        if (AIDebugServer.isRunning) {
            context.source.sendFailure(
                "AI Debug Server is already running!".text().red()
            )
            return 0
        }

        AIDebugServer.start(port)

        val url = "http://localhost:$port"
        val clickableUrl = Component.literal(url)
            .aqua()
            .withStyle { style ->
                style.withClickEvent(ClickEvent(ClickEvent.Action.OPEN_URL, url))
                    .withHoverEvent(HoverEvent(HoverEvent.Action.SHOW_TEXT, "Click to open in browser".text()))
            }

        context.source.sendSuccess({
            Component.literal("")
                .append("AI Debug Server started! ".text().green())
                .append("Open ".text())
                .append(clickableUrl)
                .append(" in your browser.".text())
        }, true)

        context.source.sendSuccess({
            "Start a battle with an NPC to see AI decision visualization.".text().yellow()
        }, false)

        return Command.SINGLE_SUCCESS
    }

    private fun stopServer(context: CommandContext<CommandSourceStack>): Int {
        if (!AIDebugServer.isRunning) {
            context.source.sendFailure(
                "AI Debug Server is not running!".text().red()
            )
            return 0
        }

        AIDebugServer.stop()

        context.source.sendSuccess({
            "AI Debug Server stopped.".text().green()
        }, true)

        return Command.SINGLE_SUCCESS
    }

    private fun checkStatus(context: CommandContext<CommandSourceStack>): Int {
        val status = if (AIDebugServer.isRunning) {
            val count = AIDebugServer.getAllDecisions().size
            Component.literal("")
                .append("AI Debug Server: ".text())
                .append("Running".text().green())
                .append(" ($count decisions recorded)".text())
        } else {
            Component.literal("")
                .append("AI Debug Server: ".text())
                .append("Stopped".text().red())
        }

        context.source.sendSuccess({ status }, false)
        return Command.SINGLE_SUCCESS
    }

    private fun clearHistory(context: CommandContext<CommandSourceStack>): Int {
        val count = AIDebugServer.getAllDecisions().size
        AIDebugServer.clearHistory()

        context.source.sendSuccess({
            "Cleared $count AI decisions from history.".text().green()
        }, true)

        return Command.SINGLE_SUCCESS
    }

    private fun showHelp(context: CommandContext<CommandSourceStack>): Int {
        val help = Component.literal("")
            .append("═══ AI Debug Commands ═══\n".text().aqua())
            .append("/aidebug start [port]".text().yellow())
            .append(" - Start the debug web server\n".text())
            .append("/aidebug stop".text().yellow())
            .append(" - Stop the debug server\n".text())
            .append("/aidebug status".text().yellow())
            .append(" - Check server status\n".text())
            .append("/aidebug clear".text().yellow())
            .append(" - Clear decision history\n".text())
            .append("═══════════════════════".text().aqua())

        context.source.sendSuccess({ help }, false)
        return Command.SINGLE_SUCCESS
    }
}
