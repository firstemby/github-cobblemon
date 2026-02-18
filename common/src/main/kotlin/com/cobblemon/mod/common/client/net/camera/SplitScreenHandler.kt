/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.net.camera

import com.cobblemon.mod.common.api.net.ClientNetworkPacketHandler
import com.cobblemon.mod.common.client.render.camera.SplitScreenCompositor
import com.cobblemon.mod.common.net.messages.client.camera.SplitScreenPacket
import net.minecraft.client.Minecraft

/** Handles split-screen enable/disable packets from the server. */
object SplitScreenHandler : ClientNetworkPacketHandler<SplitScreenPacket> {
    override fun handle(packet: SplitScreenPacket, client: Minecraft) {
        if (packet.enabled) {
            SplitScreenCompositor.enable(
                primaryCameraId = packet.primaryCameraId,
                secondaryCameraId = packet.secondaryCameraId,
                splitAngleDegrees = packet.splitAngleDegrees,
                gapWidth = packet.gapWidth
            )
        } else {
            SplitScreenCompositor.disable()
        }
    }
}
