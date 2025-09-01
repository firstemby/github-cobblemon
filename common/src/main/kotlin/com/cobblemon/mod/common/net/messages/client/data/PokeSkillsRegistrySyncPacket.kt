/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.net.messages.client.data

import com.cobblemon.mod.common.CobblemonPokeSkills
import com.cobblemon.mod.common.entity.pokemon.PokeSkill
import com.cobblemon.mod.common.util.cobblemonResource
import net.minecraft.network.RegistryFriendlyByteBuf

class PokeSkillsRegistrySyncPacket(
    pokeSkills: Collection<PokeSkill>
) : DataRegistrySyncPacket<PokeSkill, PokeSkillsRegistrySyncPacket>(pokeSkills) {

    override val id = ID

    override fun encodeEntry(buffer: RegistryFriendlyByteBuf, entry: PokeSkill) {
        PokeSkill.PACKET_CODEC.encode(buffer, entry)
    }

    override fun decodeEntry(buffer: RegistryFriendlyByteBuf): PokeSkill? {
        return PokeSkill.PACKET_CODEC.decode(buffer)
    }

    override fun synchronizeDecoded(entries: Collection<PokeSkill>) {
        CobblemonPokeSkills.reload(entries.associateBy { it.id })
    }

    companion object {
        val ID = cobblemonResource("poke_skill_sync")
        fun decode(buffer: RegistryFriendlyByteBuf) = PokeSkillsRegistrySyncPacket(emptyList()).apply { decodeBuffer(buffer) }
    }
}