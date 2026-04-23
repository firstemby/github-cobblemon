/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.net.gui

import com.cobblemon.mod.common.api.net.ServerNetworkPacketHandler
import com.cobblemon.mod.common.net.messages.client.ui.DeletePokedexPhotoPacket
import com.cobblemon.mod.common.net.messages.client.ui.PokedexPhotoUploadResultPacket
import com.cobblemon.mod.common.pokedex.photo.PokedexPhotoRepository
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer

object DeletePokedexPhotoPacketHandler : ServerNetworkPacketHandler<DeletePokedexPhotoPacket> {
    override fun handle(packet: DeletePokedexPhotoPacket, server: MinecraftServer, player: ServerPlayer) {
        val deleted = PokedexPhotoRepository.delete(player.uuid, packet.photoId)
        if (deleted) {
            PokedexPhotoUploadResultPacket(true, "Deleted Pokedex photo: ${packet.photoId}").sendToPlayer(player)
        } else {
            PokedexPhotoUploadResultPacket(false, "Could not delete Pokedex photo: ${packet.photoId}").sendToPlayer(player)
        }
        PokedexPhotoNetworkUtil.sendGallery(player)
    }
}
