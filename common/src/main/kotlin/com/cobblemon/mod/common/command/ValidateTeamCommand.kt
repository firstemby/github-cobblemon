package com.cobblemon.mod.common.command

import com.cobblemon.mod.common.api.permission.CobblemonPermissions
import com.cobblemon.mod.common.battles.BattleFormat
import com.cobblemon.mod.common.battles.BattleRegistry.packTeam
import com.cobblemon.mod.common.battles.runner.ShowdownService
import com.cobblemon.mod.common.util.party
import com.cobblemon.mod.common.util.permission
import com.mojang.brigadier.Command
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.context.CommandContext
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands

object ValidateTeamCommand {

    fun register(dispatcher: CommandDispatcher<CommandSourceStack>) {
        val command = Commands.literal("validateteam")
            .permission(CobblemonPermissions.VALIDATE_TEAM)
            // TODO format argument
            .executes(::execute)
        dispatcher.register(command)
    }

    private fun execute(context: CommandContext<CommandSourceStack>): Int {
        try {
            val packedTeam = context.source.playerOrException.party().toBattleTeam().packTeam()
            ShowdownService.service.validateTeam(packedTeam, BattleFormat.GEN_9_SINGLES.toFormatJSON())
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return Command.SINGLE_SUCCESS
    }
}