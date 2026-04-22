/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.net.gui

import com.cobblemon.mod.common.api.net.ServerNetworkPacketHandler
import com.cobblemon.mod.common.block.entity.PhotoPrinterBlockEntity
import com.cobblemon.mod.common.block.photoprinter.PhotoPrinterMenu
import com.cobblemon.mod.common.net.messages.client.ui.SetPhotoPrinterSelectionPacket
import com.cobblemon.mod.common.pokedex.photo.PokedexPhotoRepository
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer

object SetPhotoPrinterSelectionPacketHandler : ServerNetworkPacketHandler<SetPhotoPrinterSelectionPacket> {
    override fun handle(packet: SetPhotoPrinterSelectionPacket, server: MinecraftServer, player: ServerPlayer) {
        val menu = player.containerMenu as? PhotoPrinterMenu ?: return
        if (menu.blockPos() != packet.blockPos) return

        val blockEntity = player.level().getBlockEntity(packet.blockPos) as? PhotoPrinterBlockEntity ?: return
        val selectedId = packet.photoId?.takeIf { PokedexPhotoRepository.getMetadata(player.uuid, it) != null }

        blockEntity.selectedPhotoId = selectedId
        blockEntity.setChanged()
        menu.broadcastChanges()
    }
}

