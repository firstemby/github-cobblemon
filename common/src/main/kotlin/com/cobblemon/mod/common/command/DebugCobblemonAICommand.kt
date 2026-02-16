/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.command

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.CobblemonNetwork.sendPacket
import com.cobblemon.mod.common.api.permission.CobblemonPermissions
import com.cobblemon.mod.common.api.text.gray
import com.cobblemon.mod.common.api.text.red
import com.cobblemon.mod.common.api.text.white
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.net.messages.client.debug.UpdateTrackedBrainDataPacket
import com.cobblemon.mod.common.util.commandLang
import com.cobblemon.mod.common.util.requiresWithPermission
import com.cobblemon.mod.common.util.traceFirstEntityCollision
import com.mojang.brigadier.Command
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.context.CommandContext
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.server.level.ServerPlayer

object DebugCobblemonAICommand {
    fun register(dispatcher : CommandDispatcher<CommandSourceStack>) {
        dispatcher.register(
            Commands.literal("debugcobblemonai")
                .requiresWithPermission(CobblemonPermissions.DEBUG_COBBLEMON_AI) { it.player != null }
                .executes { execute(it, it.source.playerOrException) })
    }

    private fun execute(context: CommandContext<CommandSourceStack>, player: ServerPlayer) : Int {
        val targetEntity = player.traceFirstEntityCollision(entityClass = PokemonEntity::class.java)
        if (targetEntity == null) {
            if (Cobblemon.serverPlayerDebuggingPokemonBrainEntities.containsKey(player.uuid)) {
                Cobblemon.serverPlayerDebuggingPokemonBrainEntities.remove(player.uuid)
                player.sendPacket(UpdateTrackedBrainDataPacket(-1, null))
                player.sendSystemMessage("Turned off debugging".gray())
            } else {
                player.sendSystemMessage(commandLang("freezepokemon.non_pokemon").red())
            }
            return 0
        }

        Cobblemon.serverPlayerDebuggingPokemonBrainEntities[player.uuid] = targetEntity.uuid
        targetEntity.biggerBrain.debuggingPlayers?.add(player.uuid)
        player.sendSystemMessage("Debugging ${targetEntity.pokemon.species.name}".white())
        return Command.SINGLE_SUCCESS
    }
}