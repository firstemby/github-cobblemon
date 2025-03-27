package com.cobblemon.mod.common.client.net.debug.showdown

import com.cobblemon.mod.common.api.net.ClientNetworkPacketHandler
import com.cobblemon.mod.common.client.gui.debug.ShowdownDebugGUI
import com.cobblemon.mod.common.net.messages.client.debug.showdown.ShowdownRefreshInfoPacket
import net.minecraft.client.Minecraft

/**
 * Handles the reception of a [ShowdownRefreshInfoPacket] on the client side.
 * This handler updates the Showdown Debug GUI with the latest showdown information.
 * @author landonjw
 */
object ShowdownRefreshInfoHandler : ClientNetworkPacketHandler<ShowdownRefreshInfoPacket> {
    /**
     * Handles the received [ShowdownRefreshInfoPacket] by refreshing the showdown information
     * in the [ShowdownDebugGUI] if it is currently open.
     *
     * @param packet The received packet containing the showdown information.
     * @param client The Minecraft client instance.
     */
    override fun handle(packet: ShowdownRefreshInfoPacket, client: Minecraft) {
        if (client.screen is ShowdownDebugGUI) {
            (client.screen as ShowdownDebugGUI).refreshShowdownInfo(packet)
        }
    }
}
