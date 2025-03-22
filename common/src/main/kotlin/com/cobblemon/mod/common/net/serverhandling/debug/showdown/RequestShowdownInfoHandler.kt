package com.cobblemon.mod.common.net.serverhandling.debug.showdown

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.CobblemonNetwork.sendPacket
import com.cobblemon.mod.common.api.net.ServerNetworkPacketHandler
import com.cobblemon.mod.common.battles.runner.ShowdownService
import com.cobblemon.mod.common.net.messages.client.debug.showdown.ShowdownRefreshInfoPacket
import com.cobblemon.mod.common.net.messages.server.debug.showdown.RequestShowdownInfoPacket
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer

/**
 * Handles the reception of a [RequestShowdownInfoPacket] on the server side.
 * This handler retrieves the showdown information from the specified service
 * and sends it back to the client.
 *
 * @author landonjw
 */
object RequestShowdownInfoHandler : ServerNetworkPacketHandler<RequestShowdownInfoPacket> {

    /**
     * Handles the received [RequestShowdownInfoPacket] by retrieving the showdown information
     * from the specified service and sending it back to the client in a [ShowdownRefreshInfoPacket].
     *
     * @param packet The received packet requesting showdown information.
     * @param server The Minecraft server instance.
     * @param player The player who sent the request.
     */
    override fun handle(packet: RequestShowdownInfoPacket, server: MinecraftServer, player: ServerPlayer) {
        if (!Cobblemon.config.enableDebugKeys) return
        if (!ShowdownService.contains(packet.service)) return

        val service = ShowdownService.get(packet.service)
        val messages = service.log ?: emptyList()
        val availableServices = ShowdownService.all().map { it.id }
        player.sendPacket(ShowdownRefreshInfoPacket(service.status, messages, availableServices))
    }
}
