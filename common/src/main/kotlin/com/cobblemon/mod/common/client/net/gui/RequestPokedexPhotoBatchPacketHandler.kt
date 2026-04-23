/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.net.gui

import com.cobblemon.mod.common.api.net.ServerNetworkPacketHandler
import com.cobblemon.mod.common.net.messages.client.ui.PokedexPhotoBatchPacket
import com.cobblemon.mod.common.net.messages.client.ui.RequestPokedexPhotoBatchPacket
import com.cobblemon.mod.common.pokedex.photo.PokedexPhotoRepository
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer

object RequestPokedexPhotoBatchPacketHandler : ServerNetworkPacketHandler<RequestPokedexPhotoBatchPacket> {
    // Cap packets to avoid oversized payloads if a client ever asks for too many at once.
    private const val MAX_ENTRIES_PER_RESPONSE = 8

    override fun handle(packet: RequestPokedexPhotoBatchPacket, server: MinecraftServer, player: ServerPlayer) {
        val entries = packet.ids.mapNotNull { id ->
            val metadata = PokedexPhotoRepository.getMetadata(player.uuid, id) ?: return@mapNotNull null
            val bytes = PokedexPhotoRepository.getPhoto(player.uuid, id) ?: return@mapNotNull null
            PokedexPhotoBatchPacket.Entry(id, metadata.width, metadata.height, bytes)
        }
        if (entries.isEmpty()) return
        entries.chunked(MAX_ENTRIES_PER_RESPONSE).forEach { chunk ->
            PokedexPhotoBatchPacket(chunk).sendToPlayer(player)
        }
    }
}
