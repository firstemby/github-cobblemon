/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://www.mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.net.messages.server.storage.pc

import com.cobblemon.mod.common.api.net.NetworkPacket
import com.cobblemon.mod.common.api.storage.pc.PCPosition
import com.cobblemon.mod.common.util.cobblemonResource
import com.cobblemon.mod.common.util.readPCPosition
import com.cobblemon.mod.common.util.writePCPosition
import java.util.UUID
import net.minecraft.network.RegistryFriendlyByteBuf

class MovePCPokemonBatchPacket(val moves: List<PCPokemonMove>) : NetworkPacket<MovePCPokemonBatchPacket> {
    override val id = ID

    data class PCPokemonMove(
        val pokemonID: UUID,
        val oldPosition: PCPosition,
        val newPosition: PCPosition
    )

    override fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeVarInt(moves.size)
        moves.forEach { move ->
            buffer.writeUUID(move.pokemonID)
            buffer.writePCPosition(move.oldPosition)
            buffer.writePCPosition(move.newPosition)
        }
    }

    companion object {
        val ID = cobblemonResource("move_pc_pokemon_batch")
        private const val MAX_MOVES = 30

        fun decode(buffer: RegistryFriendlyByteBuf): MovePCPokemonBatchPacket {
            val size = buffer.readVarInt()
            require(size in 1..MAX_MOVES) { "Invalid batch PC move size: $size" }
            return MovePCPokemonBatchPacket(
                List(size) {
                    PCPokemonMove(
                        pokemonID = buffer.readUUID(),
                        oldPosition = buffer.readPCPosition(),
                        newPosition = buffer.readPCPosition()
                    )
                }
            )
        }
    }
}
