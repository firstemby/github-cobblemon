/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.net.messages.client.ui

import com.cobblemon.mod.common.api.net.NetworkPacket
import com.cobblemon.mod.common.client.net.gui.DeletePokedexPhotoPacketHandler
import com.cobblemon.mod.common.util.cobblemonResource
import net.minecraft.network.RegistryFriendlyByteBuf

/**
 * Requests deletion of a server-stored scanner photo owned by the sender.
 *
 * Handled by [DeletePokedexPhotoPacketHandler].
 */
class DeletePokedexPhotoPacket(val photoId: String) : NetworkPacket<DeletePokedexPhotoPacket> {
    override val id = ID

    override fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeUtf(photoId)
    }

    companion object {
        val ID = cobblemonResource("delete_pokedex_photo")

        fun decode(buffer: RegistryFriendlyByteBuf): DeletePokedexPhotoPacket {
            return DeletePokedexPhotoPacket(buffer.readUtf())
        }
    }
}
