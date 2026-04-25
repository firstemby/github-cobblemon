/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.net.gui

import com.cobblemon.mod.common.api.net.ServerNetworkPacketHandler
import com.cobblemon.mod.common.net.messages.client.ui.PrintedPhotoAssetBatchPacket
import com.cobblemon.mod.common.net.messages.client.ui.RequestPrintedPhotoAssetBatchPacket
import com.cobblemon.mod.common.pokedex.photo.PrintedPhotoAssetRepository
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer

object RequestPrintedPhotoAssetBatchPacketHandler : ServerNetworkPacketHandler<RequestPrintedPhotoAssetBatchPacket> {
    private const val MAX_IDS_PER_REQUEST = 64
    private const val MAX_ENTRIES_PER_RESPONSE = 8

    override fun handle(packet: RequestPrintedPhotoAssetBatchPacket, server: MinecraftServer, player: ServerPlayer) {
        val capped = packet.assetIds.distinct().take(MAX_IDS_PER_REQUEST)
        val entries = capped.mapNotNull { id ->
            val record = PrintedPhotoAssetRepository.getAssetRecord(id) ?: return@mapNotNull null
            val bytes = PrintedPhotoAssetRepository.getAssetBytes(id) ?: return@mapNotNull null
            PrintedPhotoAssetBatchPacket.Entry(id, record.width, record.height, bytes)
        }
        if (entries.isEmpty()) return
        entries.chunked(MAX_ENTRIES_PER_RESPONSE).forEach { chunk ->
            PrintedPhotoAssetBatchPacket(chunk).sendToPlayer(player)
        }
    }
}
