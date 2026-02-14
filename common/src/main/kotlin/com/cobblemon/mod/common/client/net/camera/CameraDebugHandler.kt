/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.net.camera

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.api.camera.model.Timeline
import com.cobblemon.mod.common.api.camera.registry.TimelineDeserializer
import com.cobblemon.mod.common.api.camera.registry.TimelineRegistry
import com.cobblemon.mod.common.api.net.ClientNetworkPacketHandler
import com.cobblemon.mod.common.client.camera.debug.CameraTrackDebugRenderer
import com.cobblemon.mod.common.net.messages.client.camera.CameraDebugPacket
import com.google.gson.GsonBuilder
import com.google.gson.JsonParser
import net.minecraft.client.Minecraft

/**
 * Handles camera debug packets from the server.
 * Sets the timeline for debug visualization when testing cameras.
 *
 * On dedicated servers, the packet includes the timeline JSON data which is
 * parsed and registered locally so the debug renderer can visualize it.
 *
 * @author Terrainwax
 * @since October 15th, 2025
 */
object CameraDebugHandler : ClientNetworkPacketHandler<CameraDebugPacket> {

    private val gson = GsonBuilder()
        .registerTypeAdapter(Timeline::class.java, TimelineDeserializer)
        .create()

    override fun handle(packet: CameraDebugPacket, client: Minecraft) {
        // Empty timeline ID clears the visualization
        if (packet.timelineId.isEmpty()) {
            CameraTrackDebugRenderer.setDebugTimeline(null, packet.origin)
            CameraTrackDebugRenderer.setEnabled(false)
            return
        }

        // If timeline JSON data is provided, parse and register it locally
        // This enables debug visualization on dedicated servers where the client
        // doesn't have access to SERVER_DATA
        packet.timelineJson?.let { jsonString ->
            try {
                val jsonElement = JsonParser.parseString(jsonString)
                val timeline = gson.fromJson(jsonElement, Timeline::class.java)

                // Register the timeline locally so the debug renderer can use it
                TimelineRegistry.registerTimeline(timeline)
                Cobblemon.LOGGER.debug("[CameraDebug] Registered timeline '${packet.timelineId}' from server data")
            } catch (e: Exception) {
                Cobblemon.LOGGER.error("[CameraDebug] Failed to parse timeline JSON from server: ${e.message}")
            }
        }

        // Set the timeline for debug visualization and auto-enable the renderer
        CameraTrackDebugRenderer.setDebugTimeline(packet.timelineId, packet.origin)
        CameraTrackDebugRenderer.setEnabled(true)
    }
}
