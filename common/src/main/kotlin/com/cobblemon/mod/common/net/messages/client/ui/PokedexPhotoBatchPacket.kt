/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.net.messages.client.ui

import com.cobblemon.mod.common.api.net.NetworkPacket
import com.cobblemon.mod.common.client.net.gui.PokedexPhotoBatchPacketHandler
import com.cobblemon.mod.common.util.cobblemonResource
import net.minecraft.network.RegistryFriendlyByteBuf

/**
 * Sends encoded image bytes for a batch of Pokédex photos to the client in a single packet.
 *
 * Handled by [PokedexPhotoBatchPacketHandler].
 */
class PokedexPhotoBatchPacket(val entries: List<Entry>) : NetworkPacket<PokedexPhotoBatchPacket> {
    override val id = ID

    data class Entry(val photoId: String, val width: Int, val height: Int, val imageData: ByteArray)

    override fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeInt(entries.size)
        entries.forEach { entry ->
            buffer.writeUtf(entry.photoId)
            buffer.writeInt(entry.width)
            buffer.writeInt(entry.height)
            buffer.writeByteArray(entry.imageData)
        }
    }

    companion object {
        val ID = cobblemonResource("pokedex_photo_batch")

        fun decode(buffer: RegistryFriendlyByteBuf): PokedexPhotoBatchPacket {
            val size = buffer.readInt()
            val entries = ArrayList<Entry>(size)
            repeat(size) {
                entries += Entry(
                    photoId = buffer.readUtf(),
                    width = buffer.readInt(),
                    height = buffer.readInt(),
                    imageData = buffer.readByteArray()
                )
            }
            return PokedexPhotoBatchPacket(entries)
        }
    }
}
