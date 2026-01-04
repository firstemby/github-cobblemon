/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.net.camera

import com.cobblemon.mod.common.api.net.ClientNetworkPacketHandler
import com.cobblemon.mod.common.client.camera.CameraClientChunkCacheExtension
import com.cobblemon.mod.common.client.camera.ClientCameraController
import com.cobblemon.mod.common.net.messages.client.camera.CameraUpdatePacket
import net.minecraft.client.Minecraft
import net.minecraft.core.SectionPos
import net.minecraft.world.level.ChunkPos

/** Handles camera update packets from the server. */
object CameraUpdateHandler : ClientNetworkPacketHandler<CameraUpdatePacket> {
    override fun handle(packet: CameraUpdatePacket, client: Minecraft) {
        ClientCameraController.updateCamera(
            packet.cameraId,
            packet.position,
            packet.focusPosition,
            packet.manualRotation,
            packet.renderPlayerBody
        )

        if (packet.updateCenterChunk && packet.position != null) {
            val chunkX = SectionPos.posToSectionCoord(packet.position.x)
            val chunkZ = SectionPos.posToSectionCoord(packet.position.z)
            CameraClientChunkCacheExtension.setCameraCenter(ChunkPos(chunkX, chunkZ))
        }
    }
}

