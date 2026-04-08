/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.net.serverhandling.breeding

import com.cobblemon.mod.common.api.net.ServerNetworkPacketHandler
import com.cobblemon.mod.common.block.entity.IncubatorBlockEntity
import com.cobblemon.mod.common.block.entity.NestBlockEntity
import com.cobblemon.mod.common.net.messages.server.breeding.TriggerEggHatchPacket
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer

object TriggerEggHatchHandler : ServerNetworkPacketHandler<TriggerEggHatchPacket> {
    override fun handle(packet: TriggerEggHatchPacket, server: MinecraftServer, player: ServerPlayer) {
        val level = player.serverLevel()
        val be = level.getBlockEntity(packet.pos)
        if (be is NestBlockEntity) {
            be.hatchPokemon(be.blockState, level, packet.pos, player)
        } else if (be is IncubatorBlockEntity) {
            be.hatchPokemon(be.blockState, level, packet.pos, player)
        }
    }
}

