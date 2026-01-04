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
import com.cobblemon.mod.common.net.messages.client.camera.CameraEndPacket
import net.minecraft.client.Minecraft

/** Ends a camera session on the client. */
object CameraEndHandler : ClientNetworkPacketHandler<CameraEndPacket> {
    override fun handle(packet: CameraEndPacket, client: Minecraft) {
        ClientCameraController.stop(packet.cameraId)
    }
}








