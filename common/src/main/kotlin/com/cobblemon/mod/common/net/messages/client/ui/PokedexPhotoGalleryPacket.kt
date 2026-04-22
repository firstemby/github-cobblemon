/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.net.messages.client.ui

import com.cobblemon.mod.common.api.net.NetworkPacket
import com.cobblemon.mod.common.client.net.gui.PokedexPhotoGalleryPacketHandler
import com.cobblemon.mod.common.pokedex.photo.PokedexPhotoMetadata
import com.cobblemon.mod.common.util.cobblemonResource
import net.minecraft.network.RegistryFriendlyByteBuf
import java.util.UUID

/**
 * Sends a player's Pokédex photo listing to the client.
 *
 * Handled by [PokedexPhotoGalleryPacketHandler].
 */
class PokedexPhotoGalleryPacket(
    val maxPhotosAllowed: Int,
    val photos: List<PokedexPhotoMetadata>
) : NetworkPacket<PokedexPhotoGalleryPacket> {
    override val id = ID

    override fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeInt(maxPhotosAllowed)
        buffer.writeInt(photos.size)
        photos.forEach { photo ->
            buffer.writeUtf(photo.id)
            buffer.writeUUID(photo.owner)
            buffer.writeLong(photo.index)
            buffer.writeInt(photo.width)
            buffer.writeInt(photo.height)
            buffer.writeLong(photo.createdAt)
            buffer.writeInt(photo.dataSize)
        }
    }

    companion object {
        val ID = cobblemonResource("pokedex_photo_gallery")

        fun decode(buffer: RegistryFriendlyByteBuf): PokedexPhotoGalleryPacket {
            val maxAllowed = buffer.readInt()
            val photoCount = buffer.readInt()
            val photos = ArrayList<PokedexPhotoMetadata>(photoCount)
            repeat(photoCount) {
                photos += PokedexPhotoMetadata(
                    id = buffer.readUtf(),
                    owner = buffer.readUUID(),
                    index = buffer.readLong(),
                    width = buffer.readInt(),
                    height = buffer.readInt(),
                    createdAt = buffer.readLong(),
                    dataSize = buffer.readInt()
                )
            }
            return PokedexPhotoGalleryPacket(maxAllowed, photos)
        }
    }
}

