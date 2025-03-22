package com.cobblemon.mod.common.stadium

import com.cobblemon.mod.common.api.net.ClientNetworkPacketHandler
import com.cobblemon.mod.common.api.net.ServerNetworkPacketHandler
import net.minecraft.client.Minecraft
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer

object StadiumJoinHandler: ClientNetworkPacketHandler<StadiumJoinPacket> {
    override fun handle(packet: StadiumJoinPacket, client: Minecraft) {
        StadiumClientManager.handleJoin(packet.joiningBattle)
    }
}