/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.net.camera

import com.cobblemon.mod.common.api.net.ClientNetworkPacketHandler
import com.cobblemon.mod.common.client.render.camera.compositor.CompositorManager
import com.cobblemon.mod.common.client.render.camera.compositor.layout.DiagonalSplitLayout
import com.cobblemon.mod.common.net.messages.client.camera.SplitScreenPacket
import net.minecraft.client.Minecraft
import java.util.UUID

/**
 * Handles split-screen enable/disable packets from the server.
 *
 * Bridges the legacy [SplitScreenPacket] to the new [CompositorManager] by
 * creating/removing a fullscreen compositor with a [DiagonalSplitLayout].
 */
object SplitScreenHandler : ClientNetworkPacketHandler<SplitScreenPacket> {

    /** Stable compositor ID used for the legacy split-screen bridge. */
    private val SPLIT_SCREEN_COMPOSITOR_ID = UUID.fromString("00000000-0000-0000-0000-000000000001")

    override fun handle(packet: SplitScreenPacket, client: Minecraft) {
        if (packet.enabled) {
            val window = client.window
            CompositorManager.createCompositor(
                id = SPLIT_SCREEN_COMPOSITOR_ID,
                layout = DiagonalSplitLayout(
                    primaryCameraId = packet.primaryCameraId,
                    secondaryCameraId = packet.secondaryCameraId,
                    splitAngleDegrees = packet.splitAngleDegrees,
                    gapWidth = packet.gapWidth
                ),
                width = window.width,
                height = window.height,
                fullscreen = true
            )
        } else {
            CompositorManager.removeCompositor(SPLIT_SCREEN_COMPOSITOR_ID)
        }
    }
}
