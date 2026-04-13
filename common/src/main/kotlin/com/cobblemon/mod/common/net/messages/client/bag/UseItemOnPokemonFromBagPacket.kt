package com.cobblemon.mod.common.net.messages.client.bag

import com.cobblemon.mod.common.api.net.NetworkPacket
import com.cobblemon.mod.common.util.cobblemonResource
import net.minecraft.network.RegistryFriendlyByteBuf

class UseItemOnPokemonFromBagPacket(val partySlot: Int): NetworkPacket<UseItemOnPokemonFromBagPacket> {
    companion object {
        val ID = cobblemonResource("use_item_on_pokemon_from_bag")
        fun decode(buffer: RegistryFriendlyByteBuf): UseItemOnPokemonFromBagPacket {
            return UseItemOnPokemonFromBagPacket(buffer.readInt())
        }
    }
    override val id = ID
    override fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeInt(partySlot)
    }
}