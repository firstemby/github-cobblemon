/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.net.serverhandling.orientation

import com.cobblemon.mod.common.OrientationControllable
import com.cobblemon.mod.common.api.net.ServerNetworkPacketHandler
import com.cobblemon.mod.common.net.messages.server.orientation.ServerboundUpdateOrientationPacket
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import kotlin.math.atan2

object OrientationPacketHandler : ServerNetworkPacketHandler<ServerboundUpdateOrientationPacket> {
    override fun handle(packet: ServerboundUpdateOrientationPacket, server: MinecraftServer, player: ServerPlayer) {
        if (player is OrientationControllable) {
            player.orientationController.updateOrientation { _ -> packet.orientation }
        }
    }
}
