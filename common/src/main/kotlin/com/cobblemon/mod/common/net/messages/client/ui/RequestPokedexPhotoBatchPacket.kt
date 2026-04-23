/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.net.messages.client.ui

import com.cobblemon.mod.common.api.net.NetworkPacket
import com.cobblemon.mod.common.client.net.gui.RequestPokedexPhotoBatchPacketHandler
import com.cobblemon.mod.common.util.cobblemonResource
import net.minecraft.network.RegistryFriendlyByteBuf

/**
 * Requests a batch of Pokédex photos by id from the server in a single round trip.
 *
 * Handled by [RequestPokedexPhotoBatchPacketHandler].
 */
class RequestPokedexPhotoBatchPacket(val ids: List<String>) : NetworkPacket<RequestPokedexPhotoBatchPacket> {
    override val id = ID

    override fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeInt(ids.size)
        ids.forEach { buffer.writeUtf(it) }
    }

    companion object {
        val ID = cobblemonResource("request_pokedex_photo_batch")

        fun decode(buffer: RegistryFriendlyByteBuf): RequestPokedexPhotoBatchPacket {
            val size = buffer.readInt()
            val ids = ArrayList<String>(size)
            repeat(size) { ids += buffer.readUtf() }
            return RequestPokedexPhotoBatchPacket(ids)
        }
    }
}
