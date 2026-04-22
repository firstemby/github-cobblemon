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
import com.cobblemon.mod.common.net.messages.client.ui.PokedexPhotoUploadPacket
import com.cobblemon.mod.common.net.messages.client.ui.PokedexPhotoUploadResultPacket
import com.cobblemon.mod.common.pokedex.photo.PokedexPhotoRepository
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import kotlin.math.max

object PokedexPhotoUploadPacketHandler : ServerNetworkPacketHandler<PokedexPhotoUploadPacket> {
    private const val MAX_UPLOAD_SIZE = 5 * 1024 * 1024

    override fun handle(packet: PokedexPhotoUploadPacket, server: MinecraftServer, player: ServerPlayer) {
        val configuredResolution = max(64, Cobblemon.config.pokedexScannerPhotoResolution)
        val expectedRawSize = configuredResolution.toLong() * configuredResolution.toLong() * 4L
        val maxUploadSize = max(MAX_UPLOAD_SIZE.toLong(), expectedRawSize + (512L * 1024L))
            .coerceAtMost(32L * 1024L * 1024L)
            .toInt()

        if (packet.pngData.isEmpty() || packet.pngData.size > maxUploadSize) {
            PokedexPhotoUploadResultPacket(false, "Failed to save Pokedex photo: upload is invalid.").sendToPlayer(player)
            return
        }

        val result = PokedexPhotoRepository.saveFromPng(player.uuid, packet.pngData)
        if (result.success && result.photo != null) {
            val photoId = result.photo.id
            PokedexPhotoUploadResultPacket(true, "Saved Pokedex scan photo: $photoId").sendToPlayer(player)
            PokedexPhotoNetworkUtil.sendGallery(player)
        } else {
            val message = result.errorMessage ?: "Unknown save error."
            PokedexPhotoUploadResultPacket(false, "Failed to save Pokedex photo: $message").sendToPlayer(player)
        }
    }
}
