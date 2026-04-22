/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.net.gui

import com.cobblemon.mod.common.api.net.ServerNetworkPacketHandler
import com.cobblemon.mod.common.net.messages.client.ui.PokedexPhotoDataPacket
import com.cobblemon.mod.common.net.messages.client.ui.RequestPokedexPhotoDataPacket
import com.cobblemon.mod.common.pokedex.photo.PokedexPhotoRepository
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer

object RequestPokedexPhotoDataPacketHandler : ServerNetworkPacketHandler<RequestPokedexPhotoDataPacket> {
    override fun handle(packet: RequestPokedexPhotoDataPacket, server: MinecraftServer, player: ServerPlayer) {
        val metadata = PokedexPhotoRepository.getMetadata(player.uuid, packet.photoId) ?: return
        val imageData = PokedexPhotoRepository.getPhoto(player.uuid, packet.photoId) ?: return
        PokedexPhotoDataPacket(
            photoId = packet.photoId,
            width = metadata.width,
            height = metadata.height,
            imageData = imageData
        ).sendToPlayer(player)
    }
}

