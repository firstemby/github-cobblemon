/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.net.messages.client.ui

import com.cobblemon.mod.common.api.net.NetworkPacket
import com.cobblemon.mod.common.client.net.gui.PokedexPhotoDataPacketHandler
import com.cobblemon.mod.common.util.cobblemonResource
import net.minecraft.network.RegistryFriendlyByteBuf

/**
 * Sends encoded image bytes for one Pokédex photo id to the client.
 *
 * Handled by [PokedexPhotoDataPacketHandler].
 */
class PokedexPhotoDataPacket(
    val photoId: String,
    val width: Int,
    val height: Int,
    val imageData: ByteArray
) : NetworkPacket<PokedexPhotoDataPacket> {
    override val id = ID

    override fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeUtf(photoId)
        buffer.writeInt(width)
        buffer.writeInt(height)
        buffer.writeByteArray(imageData)
    }

    companion object {
        val ID = cobblemonResource("pokedex_photo_data")

        fun decode(buffer: RegistryFriendlyByteBuf): PokedexPhotoDataPacket {
            return PokedexPhotoDataPacket(
                photoId = buffer.readUtf(),
                width = buffer.readInt(),
                height = buffer.readInt(),
                imageData = buffer.readByteArray()
            )
        }
    }
}

