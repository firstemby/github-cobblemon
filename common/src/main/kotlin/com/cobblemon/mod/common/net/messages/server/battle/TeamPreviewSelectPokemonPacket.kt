/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.net.messages.server.battle

import com.cobblemon.mod.common.api.net.NetworkPacket
import com.cobblemon.mod.common.util.cobblemonResource
import net.minecraft.network.RegistryFriendlyByteBuf
import java.util.UUID

class TeamPreviewSelectPokemonPacket(val selected: Set<Int>) : NetworkPacket<TeamPreviewSelectPokemonPacket> {
    override val id = ID
    override fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeCollection(selected) { _, value -> buffer.writeInt(value) }
    }

    companion object {
        val ID = cobblemonResource("team_preview_select_pokemon")
        fun decode(buffer: RegistryFriendlyByteBuf) = TeamPreviewSelectPokemonPacket(
            buffer.readList { buffer.readInt() }.toSet()
        )
    }
}