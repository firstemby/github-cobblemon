package com.cobblemon.mod.common.client.net.stadium

import com.cobblemon.mod.common.api.net.ClientNetworkPacketHandler
import com.cobblemon.mod.common.client.gui.stadium.StadiumCinematicAuthoring
import com.cobblemon.mod.common.net.messages.client.stadium.StadiumCinematicPacket
import net.minecraft.client.Minecraft

object StadiumCinematicHandler : ClientNetworkPacketHandler<StadiumCinematicPacket> {
    override fun handle(packet: StadiumCinematicPacket, client: Minecraft) {
        StadiumCinematicAuthoring.receive(packet, client)
    }
}
