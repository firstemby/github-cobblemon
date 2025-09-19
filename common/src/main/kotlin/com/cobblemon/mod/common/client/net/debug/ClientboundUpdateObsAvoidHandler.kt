/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.net.debug

import com.cobblemon.mod.common.api.net.ClientNetworkPacketHandler
import com.cobblemon.mod.common.client.render.debug.LineState
import com.cobblemon.mod.common.client.render.debug.ObjectAvoidanceRenderer
import com.cobblemon.mod.common.net.messages.client.debug.ClientboundUpdateObsAvoidPacket
import net.minecraft.client.Minecraft

object S2CUpdateObsAvoidHandler : ClientNetworkPacketHandler<ClientboundUpdateObsAvoidPacket> {
    override fun handle(packet: ClientboundUpdateObsAvoidPacket, client: Minecraft) {
        client.executeIfPossible {
            //ObjectAvoidanceRenderer.avoidanceLines[packet.entityId] = packet.lines
            val lineState = ObjectAvoidanceRenderer.lineStates.getOrPut(packet.entityId) { LineState() }
            lineState.update(packet.lines)
        }
    }
}
