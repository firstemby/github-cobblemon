/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.net.camera

import com.cobblemon.mod.common.api.net.ClientNetworkPacketHandler
import com.cobblemon.mod.common.client.camera.ClientCameraController
import com.cobblemon.mod.common.net.messages.client.camera.CameraFocusPacket
import net.minecraft.client.Minecraft

/**
 * Handles camera focus configuration packets from the server.
 *
 * Updates the camera's focus target (point, entity, or bone).
 */
object CameraFocusHandler : ClientNetworkPacketHandler<CameraFocusPacket> {
    override fun handle(packet: CameraFocusPacket, client: Minecraft) {
        ClientCameraController.updateFocus(
            packet.cameraId,
            packet.focus
        )
    }
}
