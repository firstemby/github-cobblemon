/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.net.messages.server.molangui

import com.cobblemon.mod.common.api.net.NetworkPacket
import com.cobblemon.mod.common.util.*
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.resources.ResourceLocation

/**
 * C2S packet sent on join with the client's Molang UI definition version map.
 * The server validates these against its manifest versions.
 */
class MolangUiVersionPacket(
    val versions: Map<ResourceLocation, String>
) : NetworkPacket<MolangUiVersionPacket> {

    override val id = ID

    override fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeMap(
            versions,
            { buf, key -> buf.writeIdentifier(key) },
            { buf, value -> buf.writeString(value) }
        )
    }

    companion object {
        val ID = cobblemonResource("molang_ui_version")

        fun decode(buffer: RegistryFriendlyByteBuf): MolangUiVersionPacket {
            val versions = buffer.readMap(
                { buf -> buf.readIdentifier() },
                { buf -> buf.readString() }
            )
            return MolangUiVersionPacket(versions)
        }
    }
}
