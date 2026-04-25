/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.net.messages.client.ui

import com.cobblemon.mod.common.api.net.NetworkPacket
import com.cobblemon.mod.common.client.net.gui.PrintedPhotoAssetBatchPacketHandler
import com.cobblemon.mod.common.util.cobblemonResource
import net.minecraft.network.RegistryFriendlyByteBuf

/**
 * Sends printed-photo asset image bytes to the client in batches.
 *
 * Handled by [PrintedPhotoAssetBatchPacketHandler].
 */
class PrintedPhotoAssetBatchPacket(val entries: List<Entry>) : NetworkPacket<PrintedPhotoAssetBatchPacket> {
    override val id = ID

    data class Entry(val assetId: Int, val width: Int, val height: Int, val imageData: ByteArray)

    override fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeInt(entries.size)
        entries.forEach { entry ->
            buffer.writeInt(entry.assetId)
            buffer.writeInt(entry.width)
            buffer.writeInt(entry.height)
            buffer.writeByteArray(entry.imageData)
        }
    }

    companion object {
        val ID = cobblemonResource("printed_photo_asset_batch")

        fun decode(buffer: RegistryFriendlyByteBuf): PrintedPhotoAssetBatchPacket {
            val size = buffer.readInt()
            val entries = ArrayList<Entry>(size)
            repeat(size) {
                entries += Entry(
                    assetId = buffer.readInt(),
                    width = buffer.readInt(),
                    height = buffer.readInt(),
                    imageData = buffer.readByteArray()
                )
            }
            return PrintedPhotoAssetBatchPacket(entries)
        }
    }
}
