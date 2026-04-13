/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.net.serverhandling.stadium

import com.cobblemon.mod.common.CobblemonNetwork.sendPacket
import com.cobblemon.mod.common.api.net.ServerNetworkPacketHandler
import com.cobblemon.mod.common.battles.stadium.StadiumBlockLayout
import com.cobblemon.mod.common.battles.stadium.StadiumBlockPlacement
import com.cobblemon.mod.common.block.entity.StadiumBlockEntity
import com.cobblemon.mod.common.net.messages.client.stadium.OpenStadiumConfigPacket
import com.cobblemon.mod.common.net.messages.server.stadium.StadiumPlacementSetPacket
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer

object StadiumPlacementSetHandler : ServerNetworkPacketHandler<StadiumPlacementSetPacket> {
    override fun handle(packet: StadiumPlacementSetPacket, server: MinecraftServer, player: ServerPlayer) {
        val blockEntity = player.level().getBlockEntity(packet.stadiumBlockPos) as? StadiumBlockEntity ?: return

        // Validate distance
        if (player.blockPosition().distSqr(packet.stadiumBlockPos) > 10000) return

        // Validate permission: must be owner or OP
        val isOwner = blockEntity.data.ownerUUID == player.uuid
        val isOp = player.hasPermissions(2)
        if (!isOwner && !isOp) return

        val data = blockEntity.data
        val layout = when (packet.layoutType) {
            "singles" -> {
                if (data.singlesLayout == null) data.singlesLayout = StadiumBlockLayout.createDefault()
                data.singlesLayout!!
            }
            "doubles" -> {
                if (data.doublesLayout == null) data.doublesLayout = StadiumBlockLayout.createDefault()
                data.doublesLayout!!
            }
            else -> return
        }

        val side = when (packet.sideIndex) {
            1 -> layout.side1
            2 -> layout.side2
            else -> return
        }

        val placementList = when (packet.slotType) {
            "actors" -> side.actors
            "pokemon" -> side.pokemon
            else -> return
        }

        val placement = StadiumBlockPlacement(position = packet.placedPosition)

        if (packet.slotIndex >= 0 && packet.slotIndex < placementList.size) {
            placementList[packet.slotIndex] = placement
        } else {
            placementList.add(placement)
        }

        blockEntity.markUpdated()

        if (blockEntity.data.isConfigured) {
            blockEntity.registerDynamic()
        }

        val updatedData = blockEntity.data.toNbt()
        player.sendPacket(OpenStadiumConfigPacket(packet.stadiumBlockPos, updatedData, initialTab = 1, initialSubTab = 0))
    }
}
