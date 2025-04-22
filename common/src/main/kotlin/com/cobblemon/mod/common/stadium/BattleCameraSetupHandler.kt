package com.cobblemon.mod.common.stadium

import com.cobblemon.mod.common.api.net.ClientNetworkPacketHandler
import net.minecraft.client.Minecraft

object BattleCameraSetupHandler: ClientNetworkPacketHandler<BattleCameraPacket> {
    override fun handle(packet: BattleCameraPacket, client: Minecraft) {
        BattleCameraManagerClient.handleJoin(packet.joiningBattle, packet.cameraManager, packet.center, packet.yRot)
    }
}