package com.cobblemon.mod.common.command

import com.cobblemon.mod.common.Cobblemon.config
import com.cobblemon.mod.common.api.permission.CobblemonPermissions
import com.cobblemon.mod.common.util.permission
import com.mojang.brigadier.Command
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.context.CommandContext
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.server.level.ServerPlayer
import net.minecraft.network.chat.Component

object AlterShinyChanceCommand {
    fun register(dispatcher: CommandDispatcher<CommandSourceStack>) {
        dispatcher.register(
                Commands.literal("altershinychance")
                        .permission(CobblemonPermissions.ALTER_SHINY_CHANCE)
                        .then(
                                Commands.argument("rate", IntegerArgumentType.integer(1))
                                        .requires { it.player != null }
                                        .executes { execute(it, it.source.playerOrException) }
                        )
        )
    }

    private fun execute(context: CommandContext<CommandSourceStack>, player: ServerPlayer): Int {
        val rate = IntegerArgumentType.getInteger(context, "rate")

        config.shinyRate = rate.toFloat()

        player.sendSystemMessage(Component.literal("Shiny rate has been set to 1 in $rate."))

        return Command.SINGLE_SUCCESS
    }
}
