/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.net.serverhandling.stadium

import com.cobblemon.mod.common.CobblemonItems
import com.cobblemon.mod.common.api.net.ServerNetworkPacketHandler
import com.cobblemon.mod.common.block.entity.StadiumBlockEntity
import com.cobblemon.mod.common.item.stadium.StadiumPlacementWandItem
import com.cobblemon.mod.common.net.messages.server.stadium.RequestStadiumPlacementWandPacket
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer

object RequestStadiumPlacementWandHandler : ServerNetworkPacketHandler<RequestStadiumPlacementWandPacket> {
    override fun handle(packet: RequestStadiumPlacementWandPacket, server: MinecraftServer, player: ServerPlayer) {
        val blockEntity = player.level().getBlockEntity(packet.blockPos) as? StadiumBlockEntity ?: return

        // Validate distance
        if (player.blockPosition().distSqr(packet.blockPos) > 10000) return

        // Validate permission: must be owner or OP
        val isOwner = blockEntity.data.ownerUUID == player.uuid
        val isOp = player.hasPermissions(2)
        if (!isOwner && !isOp) return

        val wandItem = CobblemonItems.STADIUM_PLACEMENT_WAND as? StadiumPlacementWandItem ?: return
        val wandStack = StadiumPlacementWandItem.createWandStack(
            wandItem,
            packet.blockPos,
            packet.layoutType,
            packet.sideIndex,
            packet.slotType,
            packet.slotIndex
        )

        if (!player.inventory.add(wandStack)) {
            player.drop(wandStack, false)
        }
    }
}
