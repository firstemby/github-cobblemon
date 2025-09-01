/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.net.messages.server

import com.cobblemon.mod.common.api.net.NetworkPacket
import java.util.UUID
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.resources.ResourceLocation

/**
 * Packet sent by the client to the server to indicate that it wants to use a Poké Skill.
 *
 * The server will validate if the skill can be used, and if so, will execute it.
 *
 * @param skillId The ID of the skill to use.
 * @param pokemonId The UUID of the Pokémon using the skill.
 *
 * @author Hiroku
 * @since August 31st, 2025
 */
class UsePokeSkillPacket(
    val skillId: ResourceLocation,
    val pokemonId: UUID,
) : NetworkPacket<UsePokeSkillPacket> {
    override val id: ResourceLocation = ID

    override fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeResourceLocation(skillId)
        buffer.writeUUID(pokemonId)
    }

    companion object {
        val ID = ResourceLocation("cobblemon", "use_poke_skill")
        fun decode(buffer: RegistryFriendlyByteBuf): UsePokeSkillPacket {
            return UsePokeSkillPacket(
                buffer.readResourceLocation(),
                buffer.readUUID()
            )
        }
    }
}