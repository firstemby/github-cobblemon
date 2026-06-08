/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.net.messages.server.pokedex.scanner

import com.cobblemon.mod.common.api.net.NetworkPacket
import com.cobblemon.mod.common.util.cobblemonResource
import net.minecraft.core.BlockPos
import net.minecraft.network.RegistryFriendlyByteBuf

class FinishPrintedPhotoScanningPacket(val blockPos: BlockPos, val zoomLevel: Int) : NetworkPacket<FinishPrintedPhotoScanningPacket> {
    override val id = ID

    override fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeBlockPos(blockPos)
        buffer.writeInt(zoomLevel)
    }

    companion object {
        val ID = cobblemonResource("finish_printed_photo_scanning_packet")

        fun decode(buffer: RegistryFriendlyByteBuf): FinishPrintedPhotoScanningPacket {
            return FinishPrintedPhotoScanningPacket(
                blockPos = buffer.readBlockPos(),
                zoomLevel = buffer.readInt()
            )
        }
    }
}
