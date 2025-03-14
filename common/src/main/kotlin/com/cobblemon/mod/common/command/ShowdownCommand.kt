package com.cobblemon.mod.common.command

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.showdown.runner.remote.RemoteShowdownBattleServer
import com.cobblemon.mod.common.showdown.runner.remote.ShowdownGameServerLoginManager
import com.cobblemon.mod.common.showdown.runner.remote.ShowdownGameServerLoginState
import com.mojang.brigadier.Command
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.context.CommandContext
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands.argument
import net.minecraft.commands.Commands.literal
import net.minecraft.network.chat.Component

object ShowdownCommand {
    private const val NAME = "showdown"

    fun register(dispatcher : CommandDispatcher<CommandSourceStack>) {
        dispatcher.register(
            literal(NAME)
            .then(
                literal("setnickname").then(
                    argument("name", StringArgumentType.string()).executes(::executeSetNick)
                )

            )
        )
    }

    private fun executeSetNick(context: CommandContext<CommandSourceStack>): Int {
        val name = StringArgumentType.getString(context, "name")
        val showdownServer = Cobblemon.showdownServer
        if (showdownServer !is RemoteShowdownBattleServer) {
            context.source.sendFailure(Component.literal("Configured showdown server is not a showdown game server!!"))
            return Command.SINGLE_SUCCESS
        }
        val assertion = ShowdownGameServerLoginManager.loginGuest(name, showdownServer.getLoginState())
        showdownServer.setLoginState(ShowdownGameServerLoginState(assertion = assertion, username = name))
        showdownServer.sendServerMessage("|/trn $name,0,$assertion")
        return Command.SINGLE_SUCCESS
    }
}