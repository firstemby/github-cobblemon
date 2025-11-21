package com.cobblemon.mod.common.net.messages.server.pokemon.skills

import com.cobblemon.mod.common.api.net.NetworkPacket
import com.cobblemon.mod.common.util.cobblemonResource
import net.minecraft.network.RegistryFriendlyByteBuf

class PokeSkillMinePacket() : NetworkPacket<PokeSkillMinePacket> {
    override val id = ID
    override fun encode(buffer: RegistryFriendlyByteBuf) {
    }

    companion object {
        val ID = cobblemonResource("poke_skill_mine")
        fun decode(buffer: RegistryFriendlyByteBuf) = PokeSkillMinePacket()
    }
}