/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.net.debug

import com.cobblemon.mod.common.api.net.ClientNetworkPacketHandler
import com.cobblemon.mod.common.client.CobblemonClient
import com.cobblemon.mod.common.net.messages.client.debug.UpdateTrackedBrainDataPacket
import net.minecraft.client.Minecraft

object UpdateTrackedBrainDataHandler : ClientNetworkPacketHandler<UpdateTrackedBrainDataPacket> {
    override fun handle(packet: UpdateTrackedBrainDataPacket, client: Minecraft) {
        CobblemonClient.biggerBrainDebugData = packet.state
    }
}