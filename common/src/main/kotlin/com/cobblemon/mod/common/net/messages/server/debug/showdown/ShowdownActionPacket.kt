package com.cobblemon.mod.common.net.messages.server.debug.showdown

import com.cobblemon.mod.common.api.net.NetworkPacket
import com.cobblemon.mod.common.util.cobblemonResource
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.resources.ResourceLocation

/**
 * A network packet for sending action commands to the Showdown service.
 *
 * @property service The resource location of the Showdown service.
 * @property action The action command to be sent to the Showdown service.
 * @author landonjw
 */
class ShowdownActionPacket(val service: ResourceLocation, val action: Action) : NetworkPacket<ShowdownActionPacket> {
    override val id = ID

    /**
     * Encodes the packet data into the given buffer.
     *
     * @param buffer The buffer to encode the data into.
     */
    override fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeResourceLocation(service)
        buffer.writeEnum(action)
    }

    companion object {
        val ID = cobblemonResource("showdown_action_request")

        /**
         * Decodes the packet data from the given buffer.
         *
         * @param buffer The buffer to decode the data from.
         * @return The decoded ShowdownActionPacket.
         */
        fun decode(buffer: RegistryFriendlyByteBuf) = ShowdownActionPacket(
            service = buffer.readResourceLocation(),
            action = buffer.readEnum(Action::class.java)
        )
    }

    /**
     * Enum representing possible actions for the Showdown service.
     */
    enum class Action {
        START, STOP, RESTART
    }
}
