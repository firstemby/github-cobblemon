package com.cobblemon.mod.common.net.serverhandling.debug.showdown

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.api.net.ServerNetworkPacketHandler
import com.cobblemon.mod.common.battles.runner.ShowdownService
import com.cobblemon.mod.common.net.messages.server.debug.showdown.ShowdownInputPacket
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer

/**
 * Handles the processing of `ShowdownInputPacket` on the server side.
 * This handler sends a command to the given showdown service. This is used for debugging purposes.
 *
 * @author landonjw
 */
object ShowdownInputHandler : ServerNetworkPacketHandler<ShowdownInputPacket> {

    /**
     * Handles the received `ShowdownInputPacket`.
     *
     * @param packet The received packet containing the input command and service information.
     * @param server The Minecraft server instance.
     * @param player The player who sent the packet.
     */
    override fun handle(packet: ShowdownInputPacket, server: MinecraftServer, player: ServerPlayer) {
        if (!Cobblemon.config.enableDebugKeys) return
        if (!ShowdownService.contains(packet.service)) return

        val service = ShowdownService.get(packet.service)
        service.send(packet.input)
    }

}
