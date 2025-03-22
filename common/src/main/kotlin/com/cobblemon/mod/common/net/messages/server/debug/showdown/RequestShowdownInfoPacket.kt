package com.cobblemon.mod.common.net.messages.server.debug.showdown

import com.cobblemon.mod.common.api.net.NetworkPacket
import com.cobblemon.mod.common.util.cobblemonResource
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.resources.ResourceLocation

/**
 * Packet sent to request showdown information for a specific server.
 *
 * @property service The id of the showdown service to get showdown information for.
 * @author landonjw
 */
class RequestShowdownInfoPacket(val service: ResourceLocation) : NetworkPacket<RequestShowdownInfoPacket> {
    override val id = ID

    /**
     * Encodes this [RequestShowdownInfoPacket] to the given [RegistryFriendlyByteBuf].
     *
     * @param buffer The buffer to write to.
     */
    override fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeResourceLocation(service)
    }

    companion object {
        val ID = cobblemonResource("request_showdown_info")

        /**
         * Decodes a [RequestShowdownInfoPacket] from the given [RegistryFriendlyByteBuf].
         *
         * @param buffer The buffer to read from.
         * @return The decoded [RequestShowdownInfoPacket].
         */
        fun decode(buffer: RegistryFriendlyByteBuf) = RequestShowdownInfoPacket(
            service = buffer.readResourceLocation()
        )
    }
}
