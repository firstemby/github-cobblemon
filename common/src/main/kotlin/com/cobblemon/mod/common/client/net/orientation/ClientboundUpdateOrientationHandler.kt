/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.net.orientation

import com.cobblemon.mod.common.OrientationControllable
import com.cobblemon.mod.common.api.net.ClientNetworkPacketHandler
import com.cobblemon.mod.common.net.messages.client.orientation.ClientboundUpdateOrientationPacket
import net.minecraft.client.Minecraft

object S2CUpdateOrientationHandler : ClientNetworkPacketHandler<ClientboundUpdateOrientationPacket> {
    override fun handle(packet: ClientboundUpdateOrientationPacket, client: Minecraft) {
        client.executeIfPossible {
            val level = client.level ?: return@executeIfPossible
            val entity = level.getEntity(packet.entityId)
            if (entity is OrientationControllable) {
                packet.active?.let {
                    entity.orientationController.active = it
                    if (!it) {
                        entity.orientationController.reset()
                    }
                }
                entity.orientationController.updateOrientation { _ -> packet.orientation }
            }
        }
    }
}
