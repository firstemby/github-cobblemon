/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.net.messages.client.data

import com.cobblemon.mod.common.api.interaction.PokemonInteractionSet
import com.cobblemon.mod.common.api.net.NetworkPacket
import com.cobblemon.mod.common.util.cobblemonResource
import net.minecraft.network.RegistryFriendlyByteBuf

class PokemonInteractionsSyncPacket(
    val speciesInteractions: List<PokemonInteractionSet>,
    val generalInteractions: List<PokemonInteractionSet>,
) : NetworkPacket<PokemonInteractionsSyncPacket> {
    override val id = ID

    override fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeCollection(speciesInteractions) { it, speciesInteraction ->
            speciesInteraction.encode(it)
        }
        buffer.writeCollection(generalInteractions) { it, generalInteraction ->
            generalInteraction.encode(it)
        }
    }

    companion object {
        val ID = cobblemonResource("pokemon_interactions_sync")

        fun decode(buffer: RegistryFriendlyByteBuf): PokemonInteractionsSyncPacket {
            val speciesInteractions = buffer.readList { it ->
                PokemonInteractionSet.decode(it)
            }
            val generalInteractions = buffer.readList { it ->
                PokemonInteractionSet.decode(it)
            }

            return PokemonInteractionsSyncPacket(
                speciesInteractions,
                generalInteractions,
            )
        }
    }
}