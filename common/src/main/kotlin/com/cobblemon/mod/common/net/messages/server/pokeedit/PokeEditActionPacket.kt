/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.net.messages.server.pokeedit

import com.cobblemon.mod.common.api.net.NetworkPacket
import com.cobblemon.mod.common.util.cobblemonResource
import com.cobblemon.mod.common.util.readEnumConstant
import com.cobblemon.mod.common.util.readString
import com.cobblemon.mod.common.util.writeEnumConstant
import com.cobblemon.mod.common.util.writeString
import net.minecraft.network.RegistryFriendlyByteBuf

enum class PokeEditAction {
    CREATE,
    SPAWN
}

class PokeEditActionPacket(
    val action: PokeEditAction,
    val properties: String,
    val moves: List<String>,
    val cosmeticItem: String
) : NetworkPacket<PokeEditActionPacket> {
    override val id = ID

    override fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeEnumConstant(action)
        buffer.writeString(properties)
        buffer.writeInt(moves.size)
        moves.forEach(buffer::writeString)
        buffer.writeString(cosmeticItem)
    }

    companion object {
        val ID = cobblemonResource("pokeedit_action")

        fun decode(buffer: RegistryFriendlyByteBuf): PokeEditActionPacket {
            val action = buffer.readEnumConstant(PokeEditAction::class.java)
            val properties = buffer.readString()
            val moves = List(buffer.readInt()) { buffer.readString() }
            val cosmeticItem = buffer.readString()
            return PokeEditActionPacket(action, properties, moves, cosmeticItem)
        }
    }
}
