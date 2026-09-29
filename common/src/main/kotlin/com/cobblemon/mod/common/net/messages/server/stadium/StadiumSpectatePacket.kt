/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.net.messages.server.stadium

import com.cobblemon.mod.common.api.net.NetworkPacket
import com.cobblemon.mod.common.util.cobblemonResource
import net.minecraft.network.RegistryFriendlyByteBuf
import java.util.UUID

class StadiumSpectatePacket(val sessionId: UUID, val leave: Boolean) : NetworkPacket<StadiumSpectatePacket> {
    override val id = ID
    override fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeUUID(sessionId)
        buffer.writeBoolean(leave)
    }
    companion object {
        val ID = cobblemonResource("stadium_spectate")
        fun decode(buffer: RegistryFriendlyByteBuf) = StadiumSpectatePacket(buffer.readUUID(), buffer.readBoolean())
    }
}
