/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.command

import com.cobblemon.mod.common.api.molangui.MolangUiContractRegistry
import com.cobblemon.mod.common.api.molangui.MolangUiOpenRequest
import com.cobblemon.mod.common.api.molangui.MolangUiOpenResult
import com.cobblemon.mod.common.api.molangui.MolangUiService
import com.cobblemon.mod.common.util.lang
import com.mojang.brigadier.Command
import com.mojang.brigadier.CommandDispatcher
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.commands.arguments.ResourceLocationArgument

object MolangUiCommand {
    private const val UI_ID = "ui"
    fun register(dispatcher: CommandDispatcher<CommandSourceStack>) {
        dispatcher.register(Commands.literal("molangui").requires { it.hasPermission(2) }
            .then(Commands.literal("open").then(Commands.argument(UI_ID, ResourceLocationArgument.id())
                .suggests { _, builder -> MolangUiContractRegistry.getAllContracts().keys.forEach { builder.suggest(it.toString()) }; builder.buildFuture() }
                .executes { context ->
                    val id = ResourceLocationArgument.getId(context, UI_ID)
                    when (val result = MolangUiService.open(context.source.playerOrException, MolangUiOpenRequest(id))) {
                        is MolangUiOpenResult.Opened -> {
                            context.source.sendSuccess({ lang("molang_ui.command.opened", id.toString(), result.session.sessionId.toString()) }, false)
                            Command.SINGLE_SUCCESS
                        }
                        is MolangUiOpenResult.Rejected -> {
                            context.source.sendFailure(lang("molang_ui.command.rejected", id.toString(),
                                lang("molang_ui.rejection.${result.reason.name.lowercase()}")))
                            0
                        }
                    }
                }))
            .then(Commands.literal("list").executes { context ->
                val contracts = MolangUiContractRegistry.getAllContracts()
                context.source.sendSuccess({ lang("molang_ui.command.list", contracts.size, contracts.keys.joinToString()) }, false)
                Command.SINGLE_SUCCESS
            }))
    }
}
