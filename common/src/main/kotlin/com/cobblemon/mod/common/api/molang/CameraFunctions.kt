/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.molang

import com.bedrockk.molang.runtime.MoParams
import com.bedrockk.molang.runtime.value.DoubleValue
import com.bedrockk.molang.runtime.value.StringValue
import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.api.camera.CameraManager
import net.minecraft.server.level.ServerPlayer

private typealias MoLangFunction = java.util.function.Function<MoParams, Any>

/**
 * Molang functions for controlling cameras.
 * These functions can be called from Molang scripts to start/stop camera timelines.
 *
 * Example usage:
 * ```
 * query.camera.start('arena_intro');  // Start camera timeline
 * query.camera.stop();  // Stop current camera for this player
 * query.camera.is_active();  // Check if player has active camera
 * ```
 *
 * @author Terrainwax
 * @since October 15th, 2025
 */
object CameraFunctions {

    /**
     * Adds camera functions to general Molang functions.
     * Should be called during mod initialization.
     */
    fun addCameraFunctions(): Map<String, MoLangFunction> {
        return mapOf(
            // Start a camera timeline for the executing player
            "camera_start" to MoLangFunction { params ->
                val timelineId = params.getString(0)
                val player = getPlayerFromParams(params)

                if (player != null) {
                    val cameraId = CameraManager.startCamera(
                        timelineId = timelineId,
                        context = emptyMap(),
                        viewers = listOf(player)
                    )

                    if (cameraId != null) {
                        DoubleValue(1.0) // Success
                    } else {
                        Cobblemon.LOGGER.warn("[CobblemonCamera] Failed to start timeline '$timelineId' for player ${player.name.string}")
                        DoubleValue(0.0) // Failure
                    }
                } else {
                    Cobblemon.LOGGER.warn("[CobblemonCamera] camera_start called without valid player context")
                    DoubleValue(0.0)
                }
            },

            // Stop the current camera for the executing player
            "camera_stop" to MoLangFunction { params ->
                val player = getPlayerFromParams(params)

                if (player != null) {
                    CameraManager.stopCameraForPlayer(player)
                    DoubleValue(1.0)
                } else {
                    DoubleValue(0.0)
                }
            },

            // Check if the executing player has an active camera
            "camera_is_active" to MoLangFunction { params ->
                val player = getPlayerFromParams(params)

                if (player != null) {
                    val hasCamera = CameraManager.getPlayerCamera(player) != null
                    DoubleValue(if (hasCamera) 1.0 else 0.0)
                } else {
                    DoubleValue(0.0)
                }
            },

            // Get the ID of the player's active camera (as string)
            "camera_get_id" to MoLangFunction { params ->
                val player = getPlayerFromParams(params)

                if (player != null) {
                    val camera = CameraManager.getPlayerCamera(player)
                    StringValue(camera?.id?.toString() ?: "")
                } else {
                    StringValue("")
                }
            }
        )
    }

    /**
     * Attempts to extract a ServerPlayer from MoParams.
     * Looks for common patterns like "player", "entity", or direct ObjectValue.
     */
    private fun getPlayerFromParams(params: MoParams): ServerPlayer? {
        try {
            // Try to get player from environment query struct
            val query = params.environment.query
            val playerValue = query.functions["player"]?.apply(params)
            if (playerValue is ObjectValue<*> && playerValue.obj is ServerPlayer) {
                return playerValue.obj as ServerPlayer
            }

            // Try to get from entity context
            val entityValue = query.functions["entity"]?.apply(params)
            if (entityValue is ObjectValue<*> && entityValue.obj is ServerPlayer) {
                return entityValue.obj as ServerPlayer
            }
        } catch (e: Exception) {
            // Silent fail, return null
        }

        return null
    }
}
