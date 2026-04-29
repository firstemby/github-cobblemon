/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.net.messages.server.pokenav

import com.cobblemon.mod.common.api.net.NetworkPacket
import com.cobblemon.mod.common.util.cobblemonResource
import com.cobblemon.mod.common.util.readNullable
import com.cobblemon.mod.common.util.readString
import com.cobblemon.mod.common.util.writeNullable
import com.cobblemon.mod.common.util.writeString
import net.minecraft.network.RegistryFriendlyByteBuf

class RequestPokeNavSpawnDataPacket(val bucketName: String?) : NetworkPacket<RequestPokeNavSpawnDataPacket> {
    override val id = ID

    override fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeNullable(bucketName) { buf, name -> buf.writeString(name) }
    }

    companion object {
        val ID = cobblemonResource("request_pokenav_spawn_data")

        fun decode(buffer: RegistryFriendlyByteBuf) = RequestPokeNavSpawnDataPacket(
            bucketName = buffer.readNullable { it.readString() }
        )
    }
}
