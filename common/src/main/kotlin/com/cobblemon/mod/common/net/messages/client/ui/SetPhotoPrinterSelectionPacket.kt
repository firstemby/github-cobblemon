/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.net.messages.client.ui

import com.cobblemon.mod.common.api.net.NetworkPacket
import com.cobblemon.mod.common.client.net.gui.SetPhotoPrinterSelectionPacketHandler
import com.cobblemon.mod.common.util.cobblemonResource
import net.minecraft.core.BlockPos
import net.minecraft.network.RegistryFriendlyByteBuf

/**
 * Sets currently selected photo for a photo printer block entity.
 *
 * Handled by [SetPhotoPrinterSelectionPacketHandler].
 */
class SetPhotoPrinterSelectionPacket(
    val blockPos: BlockPos,
    val photoId: String?
) : NetworkPacket<SetPhotoPrinterSelectionPacket> {
    override val id = ID

    override fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeBlockPos(blockPos)
        buffer.writeNullable(photoId) { packetBuffer, value -> packetBuffer.writeUtf(value) }
    }

    companion object {
        val ID = cobblemonResource("set_photo_printer_selection")

        fun decode(buffer: RegistryFriendlyByteBuf): SetPhotoPrinterSelectionPacket {
            return SetPhotoPrinterSelectionPacket(
                blockPos = buffer.readBlockPos(),
                photoId = buffer.readNullable { it.readUtf() }
            )
        }
    }
}

