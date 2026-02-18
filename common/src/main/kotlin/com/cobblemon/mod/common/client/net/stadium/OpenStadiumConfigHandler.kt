/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.net.stadium

import com.cobblemon.mod.common.api.net.ClientNetworkPacketHandler
import com.cobblemon.mod.common.battles.stadium.StadiumBlockData
import com.cobblemon.mod.common.client.gui.stadium.StadiumConfigScreen
import com.cobblemon.mod.common.net.messages.client.stadium.OpenStadiumConfigPacket
import net.minecraft.client.Minecraft

object OpenStadiumConfigHandler : ClientNetworkPacketHandler<OpenStadiumConfigPacket> {
    override fun handle(packet: OpenStadiumConfigPacket, client: Minecraft) {
        val data = StadiumBlockData.fromNbt(packet.data)
        client.setScreen(StadiumConfigScreen(packet.blockPos, data))
    }
}
