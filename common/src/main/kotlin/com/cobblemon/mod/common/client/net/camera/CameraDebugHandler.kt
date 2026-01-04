/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.net.camera

import com.cobblemon.mod.common.api.net.ClientNetworkPacketHandler
import com.cobblemon.mod.common.client.camera.debug.CameraTrackDebugRenderer
import com.cobblemon.mod.common.net.messages.client.camera.CameraDebugPacket
import net.minecraft.client.Minecraft

/**
 * Handles camera debug packets from the server.
 * Sets the timeline for debug visualization when testing cameras.
 *
 * @author Terrainwax
 * @since October 15th, 2025
 */
object CameraDebugHandler : ClientNetworkPacketHandler<CameraDebugPacket> {
    override fun handle(packet: CameraDebugPacket, client: Minecraft) {
        // Set the timeline and origin for debug visualization
        // Empty timeline ID clears the visualization
        if (packet.timelineId.isEmpty()) {
            CameraTrackDebugRenderer.setDebugTimeline(null, packet.origin)
        } else {
            CameraTrackDebugRenderer.setDebugTimeline(packet.timelineId, packet.origin)
        }
    }
}

