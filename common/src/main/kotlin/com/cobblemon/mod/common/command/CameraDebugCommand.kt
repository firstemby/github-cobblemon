/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.command

import com.cobblemon.mod.common.CobblemonNetwork.sendPacket
import com.cobblemon.mod.common.api.camera.CameraRotation
import com.cobblemon.mod.common.api.camera.CameraManager
import com.cobblemon.mod.common.client.camera.CameraTimelineRegistry
import com.cobblemon.mod.common.client.camera.debug.CameraTrackDebugRenderer
import com.cobblemon.mod.common.net.messages.client.camera.CameraDebugPacket
import com.cobblemon.mod.common.net.messages.client.camera.CameraEndPacket
import com.cobblemon.mod.common.net.messages.client.camera.CameraStartPacket
import com.cobblemon.mod.common.net.messages.client.camera.CameraUpdatePacket
import com.cobblemon.mod.common.util.commandLang
import com.mojang.brigadier.Command
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.context.CommandContext
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.network.chat.Component
import net.minecraft.world.phys.Vec3
import java.util.UUID

/**
 * Command for testing and debugging camera timelines.
 * Legacy path system has been removed - only track-based timelines remain.
 *
 * Usage:
 * /camera debug <timelineId> [origin] - Visualize timeline without running it
 * /camera debug clear - Clear debug visualization
 * /camera test timeline <timelineId> [origin] - Run timeline and visualize
 * /camera test stop - Stop running timeline
 * /camera test handshake start/stop - Manual camera control testing
 *
 * @author Terrainwax
 * @since October 15th, 2025
 */
object CameraDebugCommand {

    private const val TIMELINE_ARG = "timelineId"
    private const val X_ARG = "x"
    private var debugCameraId: UUID? = null

    fun register(dispatcher: CommandDispatcher<CommandSourceStack>) {
        dispatcher.register(
            Commands.literal("camera")
                .requires { it.hasPermission(2) } // Requires OP level 2
                .then(
                    Commands.literal("debug")
                        .then(
                            Commands.argument(TIMELINE_ARG, StringArgumentType.string())
                                .suggests { _, builder ->
                                    CameraTimelineRegistry.timelines.keys.forEach { builder.suggest(it) }
                                    builder.buildFuture()
                                }
                                .executes { context ->
                                    executeDebugVisualize(context, StringArgumentType.getString(context, TIMELINE_ARG), null)
                                }
                                .then(
                                    Commands.argument(X_ARG, net.minecraft.commands.arguments.coordinates.Vec3Argument.vec3())
                                        .executes { context ->
                                            val pos = net.minecraft.commands.arguments.coordinates.Vec3Argument.getVec3(context, X_ARG)
                                            executeDebugVisualize(context, StringArgumentType.getString(context, TIMELINE_ARG), pos)
                                        }
                                )
                        )
                        .then(
                            Commands.literal("clear")
                                .executes { executeDebugClear(it) }
                        )
                )
                .then(
                    Commands.literal("test")
                        .then(
                            Commands.literal("timeline")
                                .then(
                                    Commands.argument(TIMELINE_ARG, StringArgumentType.string())
                                        .suggests { _, builder ->
                                            CameraTimelineRegistry.timelines.keys.forEach { builder.suggest(it) }
                                            builder.buildFuture()
                                        }
                                        .executes { context ->
                                            executeTestTimeline(context, StringArgumentType.getString(context, TIMELINE_ARG), null)
                                        }
                                        .then(
                                            Commands.argument(X_ARG, net.minecraft.commands.arguments.coordinates.Vec3Argument.vec3())
                                                .executes { context ->
                                                    val pos = net.minecraft.commands.arguments.coordinates.Vec3Argument.getVec3(context, X_ARG)
                                                    executeTestTimeline(context, StringArgumentType.getString(context, TIMELINE_ARG), pos)
                                                }
                                        )
                                )
                        )
                        .then(
                            Commands.literal("stop")
                                .executes { executeStopCamera(it) }
                        )
                        .then(
                            Commands.literal("handshake")
                                .then(
                                    Commands.literal("start")
                                        .executes { executeHandshakeStart(it) }
                                )
                                .then(
                                    Commands.literal("stop")
                                        .executes { executeHandshakeStop(it) }
                                )
                        )
                )
        )
    }

    private fun executeTestTimeline(context: CommandContext<CommandSourceStack>, timelineId: String, origin: Vec3?): Int {
        val player = context.source.playerOrException

        if (!CameraTimelineRegistry.hasTimeline(timelineId)) {
            context.source.sendFailure(commandLang("camera.test.timeline_not_found", timelineId))
            return 0
        }

        // Use provided origin or player position
        val cameraOrigin = origin ?: player.position()

        // Send debug packet to client to set the timeline for visualization
        player.sendPacket(CameraDebugPacket(timelineId, cameraOrigin))

        // Start the camera for this player
        val cameraId = CameraManager.startCamera(
            timelineId = timelineId,
            context = emptyMap(),
            viewers = listOf(player),
            origin = cameraOrigin
        )

        if (cameraId != null) {
            context.source.sendSuccess(
                { commandLang("camera.test.started", timelineId) },
                false
            )
            return Command.SINGLE_SUCCESS
        } else {
            context.source.sendFailure(commandLang("camera.test.failed", timelineId))
            return 0
        }
    }

    private fun executeStopCamera(context: CommandContext<CommandSourceStack>): Int {
        val player = context.source.playerOrException

        // Stop the player's camera
        CameraManager.stopCameraForPlayer(player)

        context.source.sendSuccess(
            { commandLang("camera.test.stopped") },
            false
        )

        return Command.SINGLE_SUCCESS
    }

    private fun executeHandshakeStart(context: CommandContext<CommandSourceStack>): Int {
        val player = context.source.playerOrException
        val position = player.position()
        val cameraId = UUID.randomUUID()
        debugCameraId = cameraId

        player.sendPacket(CameraStartPacket(cameraId, position))
        player.sendPacket(
            CameraUpdatePacket(
                cameraId,
                position,
                null,
                CameraRotation(player.yHeadRot, player.xRot),
                true
            )
        )

        context.source.sendSuccess(
            { Component.literal("Started handshake camera.") },
            false
        )

        return Command.SINGLE_SUCCESS
    }

    private fun executeHandshakeStop(context: CommandContext<CommandSourceStack>): Int {
        val player = context.source.playerOrException
        val cameraId = debugCameraId

        if (cameraId == null) {
            context.source.sendFailure(Component.literal("No handshake camera is currently running."))
            return 0
        }

        player.sendPacket(CameraEndPacket(cameraId))
        debugCameraId = null

        context.source.sendSuccess(
            { Component.literal("Stopped handshake camera.") },
            false
        )

        return Command.SINGLE_SUCCESS
    }

    private fun executeDebugVisualize(context: CommandContext<CommandSourceStack>, timelineId: String, origin: Vec3?): Int {
        val player = context.source.playerOrException

        if (!CameraTimelineRegistry.hasTimeline(timelineId)) {
            context.source.sendFailure(commandLang("camera.test.timeline_not_found", timelineId))
            return 0
        }

        // Use provided origin or player position
        val debugOrigin = origin ?: player.position()

        // Send debug packet to client to visualize the timeline
        player.sendPacket(CameraDebugPacket(timelineId, debugOrigin))

        context.source.sendSuccess(
            { Component.literal("Visualizing camera timeline: $timelineId (Press F9 to toggle)") },
            false
        )

        return Command.SINGLE_SUCCESS
    }

    private fun executeDebugClear(context: CommandContext<CommandSourceStack>): Int {
        val player = context.source.playerOrException

        // Send packet with empty timeline to clear visualization
        player.sendPacket(CameraDebugPacket("", Vec3.ZERO))

        context.source.sendSuccess(
            { Component.literal("Cleared camera debug visualization") },
            false
        )

        return Command.SINGLE_SUCCESS
    }
}
