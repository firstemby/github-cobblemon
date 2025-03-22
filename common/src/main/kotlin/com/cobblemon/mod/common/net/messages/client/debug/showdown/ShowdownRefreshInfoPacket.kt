package com.cobblemon.mod.common.net.messages.client.debug.showdown

import com.cobblemon.mod.common.api.net.NetworkPacket
import com.cobblemon.mod.common.battles.runner.ShowdownStatus
import com.cobblemon.mod.common.util.cobblemonResource
import com.cobblemon.mod.common.util.readString
import com.cobblemon.mod.common.util.writeString
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.resources.ResourceLocation

/**
 * Packet sent to refresh the showdown information with the current status, messages, and available services.
 * This is typically sent when a user is in the Showdown Debug UI after they send a [RequestShowdownInfoPacket].
 *
 * @property status The current status of the showdown.
 * @property messages A list of messages from the active showdown service currently being refreshed.
 * @property availableServices A list of available showdown services. We send this so that the client can choose to
 *                             switch which server they are viewing.
 * @author landonjw
 */
class ShowdownRefreshInfoPacket(
    val status: ShowdownStatus,
    val messages: List<String>,
    val availableServices: List<ResourceLocation>
) : NetworkPacket<ShowdownRefreshInfoPacket> {
    companion object {
        val ID = cobblemonResource("refresh_showdown_info")

        /**
         * Decodes a [ShowdownRefreshInfoPacket] from the given [RegistryFriendlyByteBuf].
         *
         * @param buffer The buffer to read from.
         * @return The decoded [ShowdownRefreshInfoPacket].
         */
        fun decode(buffer: RegistryFriendlyByteBuf) = ShowdownRefreshInfoPacket(
            status = ShowdownStatus.valueOf(buffer.readString()),
            messages = buffer.readList { buffer.readString() },
            availableServices = buffer.readList { buffer.readResourceLocation() }
        )
    }

    override val id = ID

    /**
     * Encodes this [ShowdownRefreshInfoPacket] to the given [RegistryFriendlyByteBuf].
     *
     * @param buffer The buffer to write to.
     */
    override fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeString(status.name)
        // Only take the last 150 messages to prevent sending too much data.
        buffer.writeCollection(messages.takeLast(150)) { pb, value -> pb.writeString(value) }
        buffer.writeCollection(availableServices) { pb, value -> pb.writeResourceLocation(value) }
    }
}
