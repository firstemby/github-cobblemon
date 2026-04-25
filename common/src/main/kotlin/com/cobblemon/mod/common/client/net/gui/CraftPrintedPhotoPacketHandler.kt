/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.net.gui

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.api.net.ServerNetworkPacketHandler
import com.cobblemon.mod.common.block.entity.PhotoPrinterBlockEntity
import com.cobblemon.mod.common.block.photoprinter.PhotoPrinterMenu
import com.cobblemon.mod.common.net.messages.client.ui.CraftPrintedPhotoPacket
import com.cobblemon.mod.common.net.messages.client.ui.PokedexPhotoUploadResultPacket
import com.cobblemon.mod.common.pokedex.photo.PokedexPhotoRepository
import com.cobblemon.mod.common.pokedex.photo.PokedexPrintedPhotoFactory
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.Items

object CraftPrintedPhotoPacketHandler : ServerNetworkPacketHandler<CraftPrintedPhotoPacket> {
    override fun handle(packet: CraftPrintedPhotoPacket, server: MinecraftServer, player: ServerPlayer) {
        val printSize = packet.printSize.coerceIn(1, 3)
        val paperCost = printSize * printSize

        val menu = player.containerMenu as? PhotoPrinterMenu ?: return
        if (menu.blockPos() != packet.blockPos) return

        val blockEntity = player.level().getBlockEntity(packet.blockPos) as? PhotoPrinterBlockEntity ?: return
        val selectedId = (packet.photoId ?: blockEntity.selectedPhotoId)
        if (selectedId.isNullOrEmpty()) {
            PokedexPhotoUploadResultPacket(false, "No photo selected to print.").sendToPlayer(player)
            return
        }

        val metadata = PokedexPhotoRepository.getMetadata(player.uuid, selectedId)
        val imageData = PokedexPhotoRepository.getPhoto(player.uuid, selectedId)
        if (metadata == null || imageData == null) {
            PokedexPhotoUploadResultPacket(false, "That photo no longer exists on the server.").sendToPlayer(player)
            return
        }

        val input = blockEntity.getItem(PhotoPrinterBlockEntity.INPUT_SLOT)
        if (!input.`is`(Items.PAPER)) {
            PokedexPhotoUploadResultPacket(false, "Photo printing requires paper.").sendToPlayer(player)
            return
        }
        if (input.count < paperCost) {
            PokedexPhotoUploadResultPacket(false, "Need $paperCost paper for a ${printSize}x${printSize} print.").sendToPlayer(player)
            return
        }

        if (!blockEntity.getItem(PhotoPrinterBlockEntity.RESULT_SLOT).isEmpty) {
            PokedexPhotoUploadResultPacket(false, "Take the printed photo from the output slot first.").sendToPlayer(player)
            return
        }

        val printedStack = PokedexPrintedPhotoFactory.createPrintedStack(
            level = player.serverLevel(),
            metadata = metadata,
            imageData = imageData,
            printSize = printSize
        )
        if (printedStack == null) {
            PokedexPhotoUploadResultPacket(false, "Failed to build printed photo data.").sendToPlayer(player)
            return
        }

        input.shrink(paperCost)
        blockEntity.setItem(PhotoPrinterBlockEntity.RESULT_SLOT, printedStack)
        blockEntity.setChanged()
        menu.broadcastChanges()

        if (Cobblemon.config.pokedexDeletePhotoAfterPrint) {
            PokedexPhotoRepository.delete(player.uuid, selectedId)
            blockEntity.selectedPhotoId = null
            PokedexPhotoNetworkUtil.sendGallery(player)
        }

        PokedexPhotoUploadResultPacket(true, "Printed photo ready.").sendToPlayer(player)
    }
}

