/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.net.messages.client.ui

import com.cobblemon.mod.common.api.net.NetworkPacket
import com.cobblemon.mod.common.client.net.gui.PokedexPhotoUploadResultPacketHandler
import com.cobblemon.mod.common.util.cobblemonResource
import net.minecraft.network.RegistryFriendlyByteBuf

/**
 * Sends upload success/failure feedback for a scanner photo upload.
 *
 * Handled by [PokedexPhotoUploadResultPacketHandler].
 */
class PokedexPhotoUploadResultPacket(
    val success: Boolean,
    val message: String
) : NetworkPacket<PokedexPhotoUploadResultPacket> {
    override val id = ID

    override fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeBoolean(success)
        buffer.writeUtf(message)
    }

    companion object {
        val ID = cobblemonResource("pokedex_photo_upload_result")

        fun decode(buffer: RegistryFriendlyByteBuf): PokedexPhotoUploadResultPacket {
            return PokedexPhotoUploadResultPacket(
                success = buffer.readBoolean(),
                message = buffer.readUtf()
            )
        }
    }
}

