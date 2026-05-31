/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.net.messages.server.pokemon.update.form

import com.cobblemon.mod.common.api.net.NetworkPacket
import com.cobblemon.mod.common.util.cobblemonResource
import java.util.UUID
import net.minecraft.network.RegistryFriendlyByteBuf

class RevertFormPacket(val pokemonUUID: UUID) : NetworkPacket<RevertFormPacket> {

    override val id = ID

    override fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeUUID(this.pokemonUUID)
    }

    companion object {
        val ID = cobblemonResource("revert_form")
        fun decode(buffer: RegistryFriendlyByteBuf) = RevertFormPacket(buffer.readUUID())
    }
}