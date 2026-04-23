/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.net.messages.client.ui

import com.cobblemon.mod.common.api.net.NetworkPacket
import com.cobblemon.mod.common.client.net.gui.PokedexPhotoUploadPacketHandler
import com.cobblemon.mod.common.util.cobblemonResource
import net.minecraft.network.RegistryFriendlyByteBuf

/**
 * Uploads a captured Pokédex scan photo to the server.
 *
 * Handled by [PokedexPhotoUploadPacketHandler].
 */
class PokedexPhotoUploadPacket(
    val width: Int,
    val height: Int,
    val detectedSpecies: List<String>,
    val biome: String,
    val dimension: String,
    val timeOfDayTicks: Long,
    val pngData: ByteArray
) : NetworkPacket<PokedexPhotoUploadPacket> {
    override val id = ID

    override fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeInt(width)
        buffer.writeInt(height)
        buffer.writeCollection(detectedSpecies) { _, species -> buffer.writeUtf(species) }
        buffer.writeUtf(biome)
        buffer.writeUtf(dimension)
        buffer.writeLong(timeOfDayTicks)
        buffer.writeByteArray(pngData)
    }

    companion object {
        val ID = cobblemonResource("pokedex_photo_upload")

        fun decode(buffer: RegistryFriendlyByteBuf): PokedexPhotoUploadPacket {
            return PokedexPhotoUploadPacket(
                width = buffer.readInt(),
                height = buffer.readInt(),
                detectedSpecies = buffer.readList { it.readUtf() },
                biome = buffer.readUtf(),
                dimension = buffer.readUtf(),
                timeOfDayTicks = buffer.readLong(),
                pngData = buffer.readByteArray()
            )
        }
    }
}

