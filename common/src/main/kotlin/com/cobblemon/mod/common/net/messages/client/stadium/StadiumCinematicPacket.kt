/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.net.messages.client.stadium

import com.cobblemon.mod.common.api.net.NetworkPacket
import com.cobblemon.mod.common.battles.stadium.StadiumCinematic
import com.cobblemon.mod.common.util.cobblemonResource
import com.cobblemon.mod.common.util.readText
import com.cobblemon.mod.common.util.writeText
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.chat.Component
import net.minecraft.world.phys.Vec3
import java.util.UUID

class StadiumCinematicPacket(
    val requestId: UUID,
    val success: Boolean,
    val message: Component,
    val revision: String = "",
    val source: String = "",
    val origin: Vec3 = Vec3.ZERO
) : NetworkPacket<StadiumCinematicPacket> {
    override val id = ID
    override fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeUUID(requestId)
        buffer.writeBoolean(success)
        buffer.writeText(message)
        buffer.writeUtf(revision, 64)
        buffer.writeUtf(source, StadiumCinematic.MAX_LENGTH)
        buffer.writeVec3(origin)
    }
    companion object {
        val ID = cobblemonResource("stadium_cinematic")
        fun decode(buffer: RegistryFriendlyByteBuf) = StadiumCinematicPacket(buffer.readUUID(), buffer.readBoolean(),
            buffer.readText(), buffer.readUtf(64), buffer.readUtf(StadiumCinematic.MAX_LENGTH), buffer.readVec3())
    }
}
