package com.cobblemon.mod.common.net.messages.server.debug.showdown

import com.cobblemon.mod.common.api.net.NetworkPacket
import com.cobblemon.mod.common.util.cobblemonResource
import com.cobblemon.mod.common.util.readString
import com.cobblemon.mod.common.util.writeString
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.resources.ResourceLocation

/**
 * A network packet for sending input commands to the Showdown service.
 *
 * @property service The resource location of the Showdown service.
 * @property input The input command to be sent to the Showdown service.
 * @author landonjw
 */
class ShowdownInputPacket(val service: ResourceLocation, val input: String) : NetworkPacket<ShowdownInputPacket> {
    override val id = ID

    /**
     * Encodes the packet data into the given buffer.
     *
     * @param buffer The buffer to encode the data into.
     */
    override fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeResourceLocation(service)
        buffer.writeString(input)
    }

    companion object {
        val ID = cobblemonResource("showdown_info_request")

        /**
         * Decodes the packet data from the given buffer.
         *
         * @param buffer The buffer to decode the data from.
         * @return The decoded ShowdownInputPacket.
         */
        fun decode(buffer: RegistryFriendlyByteBuf) = ShowdownInputPacket(
            service = buffer.readResourceLocation(),
            input = buffer.readString()
        )
    }
}
