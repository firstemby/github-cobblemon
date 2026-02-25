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
import com.cobblemon.mod.common.net.messages.client.camera.CameraStatePacket
import net.minecraft.client.Minecraft

object CameraStateHandler : ClientNetworkPacketHandler<CameraStatePacket> {
    override fun handle(packet: CameraStatePacket, client: Minecraft) {
        ClientCameraController.updateState(
            packet.cameraId,
            packet.instantTransition,
            packet.renderPlayerBody,
            packet.hiddenHudElements
        )
    }
}
