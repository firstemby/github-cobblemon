package com.cobblemon.mod.common.stadium

import com.cobblemon.mod.common.api.net.ServerNetworkPacketHandler
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer

object StadiumJoinHandler: ServerNetworkPacketHandler<StadiumJoinPacket> {
    override fun handle(packet: StadiumJoinPacket, server: MinecraftServer, player: ServerPlayer) {
        StadiumClientManager.handleJoin(packet.joiningBattle)
    }
}