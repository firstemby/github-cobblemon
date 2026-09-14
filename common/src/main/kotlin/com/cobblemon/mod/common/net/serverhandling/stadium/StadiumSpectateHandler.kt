package com.cobblemon.mod.common.net.serverhandling.stadium

import com.cobblemon.mod.common.api.net.ServerNetworkPacketHandler
import com.cobblemon.mod.common.battles.stadium.StadiumSpectating
import com.cobblemon.mod.common.net.messages.server.stadium.StadiumSpectatePacket
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer

object StadiumSpectateHandler : ServerNetworkPacketHandler<StadiumSpectatePacket> {
    override fun handle(packet: StadiumSpectatePacket, server: MinecraftServer, player: ServerPlayer) {
        if (packet.leave) StadiumSpectating.leave(player, packet.sessionId)
        else StadiumSpectating.join(player, packet.sessionId)
        StadiumSpectating.refresh(player, force = true)
    }
}
