/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.net.serverhandling.debug

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.api.net.ServerNetworkPacketHandler
import com.cobblemon.mod.common.block.entity.GrottoBlockEntity
import com.cobblemon.mod.common.net.messages.server.debug.UpdateGrottoBlockPacket
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer

object UpdateGrottoBlockHandler : ServerNetworkPacketHandler<UpdateGrottoBlockPacket> {
    override fun handle(packet: UpdateGrottoBlockPacket, server: MinecraftServer, player: ServerPlayer) {
        if (!Cobblemon.config.enableDebugKeys) return

        val block = player.level().getBlockEntity(packet.grottoPos) ?: return
        if (block !is GrottoBlockEntity) return

    }
}