/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.net.serverhandling.stadium

import com.cobblemon.mod.common.CobblemonItems
import com.cobblemon.mod.common.util.lang
import com.cobblemon.mod.common.api.net.ServerNetworkPacketHandler
import com.cobblemon.mod.common.block.entity.StadiumBlockEntity
import com.cobblemon.mod.common.battles.stadium.StadiumConfiguration
import com.cobblemon.mod.common.battles.stadium.StadiumSetup
import com.cobblemon.mod.common.battles.stadium.StadiumSpectatorArea
import net.minecraft.core.component.DataComponents
import net.minecraft.world.item.component.CustomData
import com.cobblemon.mod.common.item.stadium.StadiumPlacementWandItem
import com.cobblemon.mod.common.net.messages.server.stadium.RequestStadiumPlacementWandPacket
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.network.protocol.game.ClientboundSetCarriedItemPacket
import net.minecraft.world.item.ItemStack

object RequestStadiumPlacementWandHandler : ServerNetworkPacketHandler<RequestStadiumPlacementWandPacket> {
    override fun handle(packet: RequestStadiumPlacementWandPacket, server: MinecraftServer, player: ServerPlayer) {
        if (!player.level().hasChunk(packet.blockPos.x shr 4, packet.blockPos.z shr 4)) return
        val blockEntity = player.level().getBlockEntity(packet.blockPos) as? StadiumBlockEntity ?: return

        if (player.blockPosition().distSqr(packet.blockPos) > 10000) return

        val isOwner = blockEntity.data.ownerUUID == player.uuid
        val isOp = player.hasPermissions(2)
        if (!isOwner && !isOp) return
        if (packet.layoutType !in StadiumConfiguration.layoutIds || packet.slotType !in setOf("arena", "setup", "actors", "pokemon", "bleachers")) return
        val bleachers = packet.slotType == "bleachers"
        if (bleachers && (packet.slotIndex !in 0..blockEntity.data.spectatorAreas.size || packet.slotIndex >= StadiumSpectatorArea.MAX_AREAS)) return
        val guided = packet.slotType == "setup"
        val area = packet.slotType == "arena" || (guided && blockEntity.data.arena == null)
        if (!area && blockEntity.data.arena == null) { player.sendSystemMessage(lang("stadium.wand.define_area")); return }
        val target = if (bleachers) StadiumSetup.Slot(1, "bleachers", packet.slotIndex)
            else if (area) StadiumSetup.Slot(1, "arena", 0)
            else if (guided) StadiumSetup.firstMissing(blockEntity.data, packet.layoutType) ?: run {
                player.sendSystemMessage(lang("stadium.wand.all_placed")); return
            }
            else StadiumSetup.Slot(packet.sideIndex, packet.slotType, packet.slotIndex)
        if (!area && !bleachers && target !in StadiumSetup.slots(blockEntity.data, packet.layoutType)) return

        val wandItem = CobblemonItems.STADIUM_PLACEMENT_WAND as? StadiumPlacementWandItem ?: return
        val wandStack = StadiumPlacementWandItem.createWandStack(
            wandItem,
            packet.blockPos,
            packet.layoutType,
            target.side,
            target.type,
            target.index
        )
        val tag = StadiumPlacementWandItem.getWandData(wandStack)!!
        tag.putUUID("StadiumId", blockEntity.data.stadiumId)
        tag.putString("Dimension", player.level().dimension().location().toString())
        tag.putBoolean("Guided", guided)
        wandStack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag))

        val requestedWand = wandStack.copy()
        val existing = (0..35).firstOrNull { player.inventory.getItem(it).item is StadiumPlacementWandItem }
        if (existing != null) player.inventory.setItem(existing, wandStack)
        if (existing == null && !player.inventory.add(wandStack)) {
            player.sendSystemMessage(lang("stadium.wand.inventory_full"))
            return
        } else {
            val inventorySlot = (0..35).firstOrNull { ItemStack.isSameItemSameComponents(player.inventory.getItem(it), requestedWand) }
            if (inventorySlot != null && inventorySlot > 8) {
                val held = player.inventory.getItem(player.inventory.selected)
                player.inventory.setItem(player.inventory.selected, player.inventory.getItem(inventorySlot))
                player.inventory.setItem(inventorySlot, held)
            }
            val slot = (0..8).firstOrNull { ItemStack.isSameItemSameComponents(player.inventory.getItem(it), requestedWand) }
            if (slot != null) {
                player.inventory.selected = slot
                player.connection.send(ClientboundSetCarriedItemPacket(slot))
            }
        }
        player.sendSystemMessage(StadiumPlacementWandItem.hint(tag))
    }
}
