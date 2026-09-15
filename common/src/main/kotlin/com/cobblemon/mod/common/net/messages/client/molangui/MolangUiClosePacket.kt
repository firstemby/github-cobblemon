/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.net.messages.client.molangui

import com.cobblemon.mod.common.api.net.NetworkPacket
import com.cobblemon.mod.common.util.cobblemonResource
import net.minecraft.network.RegistryFriendlyByteBuf
import java.util.UUID
import com.cobblemon.mod.common.api.molangui.MolangUiPacketLimits

class MolangUiClosePacket(
    val sessionId: UUID
) : NetworkPacket<MolangUiClosePacket> {

    override val id = ID

    override fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeUUID(sessionId)
    }

    companion object {
        val ID = cobblemonResource("molang_ui_close")

        fun decode(buffer: RegistryFriendlyByteBuf): MolangUiClosePacket {
            require(buffer.readableBytes() <= MolangUiPacketLimits.MAX_PACKET_BYTES)
            return MolangUiClosePacket(buffer.readUUID())
        }
    }
}
