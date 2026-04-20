/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.net.messages.server.molangui

import com.cobblemon.mod.common.api.net.NetworkPacket
import com.cobblemon.mod.common.util.cobblemonResource
import com.cobblemon.mod.common.util.readUUID
import com.cobblemon.mod.common.util.writeUUID
import net.minecraft.network.RegistryFriendlyByteBuf
import java.util.UUID

/**
 * C2S packet sent when the player closes/dismisses a Molang UI.
 */
class MolangUiClosedPacket(
    val sessionId: UUID
) : NetworkPacket<MolangUiClosedPacket> {

    override val id = ID

    override fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeUUID(sessionId)
    }

    companion object {
        val ID = cobblemonResource("molang_ui_closed")

        fun decode(buffer: RegistryFriendlyByteBuf): MolangUiClosedPacket {
            return MolangUiClosedPacket(buffer.readUUID())
        }
    }
}
