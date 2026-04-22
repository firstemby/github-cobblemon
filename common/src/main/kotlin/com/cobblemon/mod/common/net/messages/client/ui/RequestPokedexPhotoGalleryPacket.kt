/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.net.messages.client.ui

import com.cobblemon.mod.common.api.net.NetworkPacket
import com.cobblemon.mod.common.client.net.gui.RequestPokedexPhotoGalleryPacketHandler
import com.cobblemon.mod.common.util.cobblemonResource
import net.minecraft.network.RegistryFriendlyByteBuf

/**
 * Requests the player's current Pokédex photo listing from the server.
 *
 * Handled by [RequestPokedexPhotoGalleryPacketHandler].
 */
class RequestPokedexPhotoGalleryPacket : NetworkPacket<RequestPokedexPhotoGalleryPacket> {
    override val id = ID

    override fun encode(buffer: RegistryFriendlyByteBuf) = Unit

    companion object {
        val ID = cobblemonResource("request_pokedex_photo_gallery")

        fun decode(buffer: RegistryFriendlyByteBuf) = RequestPokedexPhotoGalleryPacket()
    }
}

