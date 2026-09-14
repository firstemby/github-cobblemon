/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.command

import com.cobblemon.mod.common.api.cinematic.registry.SceneRegistry
import com.cobblemon.mod.common.api.cinematic.runtime.CinematicManager
import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.util.lang
import com.mojang.brigadier.Command
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.suggestion.Suggestions
import com.mojang.brigadier.suggestion.SuggestionsBuilder
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.world.phys.Vec3
import java.util.concurrent.CompletableFuture

object CinematicCommand {

    fun register(dispatcher: CommandDispatcher<CommandSourceStack>) {
        dispatcher.register(
            Commands.literal("cinematic")
                .requires { it.hasPermission(2) }
                .then(
                    Commands.literal("start")
                        .then(
                            Commands.argument("sceneId", StringArgumentType.string())
                                .suggests(::suggestSceneIds)
                                .executes(::executeStart)
                        )
                )
                .then(Commands.literal("stop").executes(::executeStop))
                .then(Commands.literal("list").executes(::executeList))
        )
    }

    private fun executeStart(ctx: CommandContext<CommandSourceStack>): Int {
        val source = ctx.source
        val player = source.playerOrException
        val sceneId = StringArgumentType.getString(ctx, "sceneId")
        SceneRegistry.getScene(sceneId) ?: run {
            source.sendFailure(lang("cinematic.command.not_found", sceneId))
            return 0
        }
        val origin = Vec3(player.x, player.y, player.z)
        val runtimeId = CinematicManager.startSceneById(sceneId, origin) ?: run {
            source.sendFailure(lang("cinematic.command.start_failed", sceneId))
            return 0
        }
        try {
            val instance = checkNotNull(CinematicManager.getScene(runtimeId))
            instance.addViewerPlayer(player)
        } catch (error: Exception) {
            CinematicManager.stopScene(runtimeId)
            Cobblemon.LOGGER.error("Failed to join cinematic scene '{}'", sceneId, error)
            source.sendFailure(lang("cinematic.command.start_failed", sceneId))
            return 0
        }
        source.sendSuccess({
            lang("cinematic.command.started", sceneId, runtimeId.toString(), origin.toString())
        }, false)
        return Command.SINGLE_SUCCESS
    }

    private fun executeStop(ctx: CommandContext<CommandSourceStack>): Int {
        val source = ctx.source
        val player = source.playerOrException
        val activeScene = CinematicManager.getActiveScenes().firstOrNull { scene ->
            scene.networkSync.viewerIdsSnapshot().contains(player.uuid)
        }
        if (activeScene == null) {
            source.sendFailure(lang("cinematic.command.not_viewing"))
            return 0
        }
        CinematicManager.stopScene(activeScene.id)
        source.sendSuccess({ lang("cinematic.command.stopped", activeScene.id.toString()) }, false)
        return Command.SINGLE_SUCCESS
    }

    private fun executeList(ctx: CommandContext<CommandSourceStack>): Int {
        val source = ctx.source
        val scenes = SceneRegistry.getAllScenes()
        if (scenes.isEmpty()) {
            source.sendSuccess({ lang("cinematic.command.none_loaded") }, false)
            return 0
        }
        source.sendSuccess({ lang("cinematic.command.loaded", scenes.size) }, false)
        scenes.forEach { scene ->
            source.sendSuccess({ lang("cinematic.command.entry", scene.id) }, false)
        }
        return Command.SINGLE_SUCCESS
    }

    private fun suggestSceneIds(
        ctx: CommandContext<CommandSourceStack>,
        builder: SuggestionsBuilder
    ): CompletableFuture<Suggestions> {
        SceneRegistry.getAllScenes().forEach { scene ->
            builder.suggest(scene.id)
        }
        return builder.buildFuture()
    }
}
