package com.cobblemon.mod.common.stadium

import com.cobblemon.mod.common.api.net.ClientNetworkPacketHandler
import net.minecraft.client.Minecraft

object StadiumJoinHandler: ClientNetworkPacketHandler<StadiumJoinPacket> {
    override fun handle(packet: StadiumJoinPacket, client: Minecraft) {
        StadiumClientManager.handleJoin(packet.joiningBattle, packet.cameraManager, packet.center, packet.yRot)
    }
}