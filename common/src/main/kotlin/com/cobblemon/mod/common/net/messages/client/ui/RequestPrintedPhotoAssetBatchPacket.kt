/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.net.messages.client.ui

import com.cobblemon.mod.common.api.net.NetworkPacket
import com.cobblemon.mod.common.client.net.gui.RequestPrintedPhotoAssetBatchPacketHandler
import com.cobblemon.mod.common.util.cobblemonResource
import net.minecraft.network.RegistryFriendlyByteBuf

/**
 * Requests printed-photo asset bytes by id from the server.
 *
 * Handled by [RequestPrintedPhotoAssetBatchPacketHandler].
 */
class RequestPrintedPhotoAssetBatchPacket(val assetIds: List<Int>) : NetworkPacket<RequestPrintedPhotoAssetBatchPacket> {
    override val id = ID

    override fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeInt(assetIds.size)
        assetIds.forEach { buffer.writeInt(it) }
    }

    companion object {
        val ID = cobblemonResource("request_printed_photo_asset_batch")

        fun decode(buffer: RegistryFriendlyByteBuf): RequestPrintedPhotoAssetBatchPacket {
            val size = buffer.readInt()
            val ids = ArrayList<Int>(size)
            repeat(size) { ids += buffer.readInt() }
            return RequestPrintedPhotoAssetBatchPacket(ids)
        }
    }
}
