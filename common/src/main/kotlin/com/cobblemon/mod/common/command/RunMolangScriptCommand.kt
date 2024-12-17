/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.command

import com.bedrockk.molang.runtime.MoLangRuntime
import com.cobblemon.mod.common.api.molang.MoLangFunctions.asMoLangValue
import com.cobblemon.mod.common.api.molang.MoLangFunctions.setup
import com.cobblemon.mod.common.api.permission.CobblemonPermissions
import com.cobblemon.mod.common.api.scripting.CobblemonScripts
import com.cobblemon.mod.common.entity.npc.NPCEntity
import com.cobblemon.mod.common.util.permission
import com.cobblemon.mod.common.util.player
import com.cobblemon.mod.common.util.resourceLocation
import com.cobblemon.mod.common.util.uuid
import com.cobblemon.mod.common.util.withQueryValue
import com.mojang.brigadier.Command
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.context.CommandContext
import java.util.UUID
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands.argument
import net.minecraft.commands.Commands.literal
import net.minecraft.commands.arguments.EntityArgument
import net.minecraft.commands.arguments.ResourceLocationArgument
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer

object RunMolangScriptCommand {
    private const val NAME = "runmolangscript"
    private const val SCRIPT = "script"
    private const val PLAYER = "player"
    private const val NPC = "npc"
    private const val POKEMON = "pokemon"

    fun register(dispatcher : CommandDispatcher<CommandSourceStack>) {
        dispatcher.register(literal(NAME)
            .permission(CobblemonPermissions.RUN_MOLANG_SCRIPT)
            .then(
                argument(SCRIPT, ResourceLocationArgument.id())
                .executes { execute(it, it.resourceLocation(SCRIPT), null, null, null) }
                .then(argument(PLAYER, EntityArgument.player())
                    .executes { execute(it, it.resourceLocation(SCRIPT), it.player(PLAYER), null, null) }
                    .then(argument(NPC, StringArgumentType.string())
                        .executes { execute(it, it.resourceLocation(SCRIPT), it.player(PLAYER), it.uuid(NPC), null) }
                        .then(argument(POKEMON, StringArgumentType.string())
                            .executes { execute(it, it.resourceLocation(SCRIPT), it.player(PLAYER), it.uuid(NPC), it.uuid(POKEMON)) }
                        )
                    )
                    .then(argument(POKEMON, StringArgumentType.string())
                        .executes { execute(it, it.resourceLocation(SCRIPT), it.player(PLAYER), null, it.uuid(POKEMON)) }
                    )
                )
                .then(argument(NPC, StringArgumentType.string())
                    .executes { execute(it, it.resourceLocation(SCRIPT), null, it.uuid(NPC), null) }
                    .then(argument(POKEMON, StringArgumentType.string())
                        .executes { execute(it, it.resourceLocation(SCRIPT), null, it.uuid(NPC), it.uuid(POKEMON)) }
                    )
                )
                .then(argument(POKEMON, StringArgumentType.string())
                    .executes { execute(it, it.resourceLocation(SCRIPT), null, null, it.uuid(POKEMON)) }
                )
            )
        )
    }

    private fun execute(context: CommandContext<CommandSourceStack>, scriptId: ResourceLocation, player: ServerPlayer?, npcId: UUID?, pokemonId: UUID? = null): Int {
        try {
            val runtime = MoLangRuntime().setup()
            val npc = npcId?.let { context.source.level.getEntity(it) as? NPCEntity }
            val pokemon = pokemonId?.let { context.source.level.getEntity(it) as? NPCEntity }

            npc?.let { runtime.withQueryValue("npc", npc.struct) }
            pokemon?.let { runtime.withQueryValue("pokemon", pokemon.struct) }
            player?.let { runtime.withQueryValue("player", player.asMoLangValue()) }

            CobblemonScripts.run(scriptId, runtime)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return Command.SINGLE_SUCCESS
    }
}