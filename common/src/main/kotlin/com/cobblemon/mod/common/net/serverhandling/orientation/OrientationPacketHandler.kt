/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.net.serverhandling.orientation

import com.cobblemon.mod.common.Rollable
import com.cobblemon.mod.common.api.net.ServerNetworkPacketHandler
import com.cobblemon.mod.common.net.messages.server.orientation.C2SUpdateOrientationPacket
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer

object OrientationPacketHandler : ServerNetworkPacketHandler<C2SUpdateOrientationPacket> {
    override fun handle(packet: C2SUpdateOrientationPacket, server: MinecraftServer, player: ServerPlayer) {
        if (player is Rollable) {
            player.updateOrientation { _ -> packet.orientation }
        }
    }
}
