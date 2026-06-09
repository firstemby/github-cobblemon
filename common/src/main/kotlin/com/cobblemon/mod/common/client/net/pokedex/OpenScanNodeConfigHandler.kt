package com.cobblemon.mod.common.client.net.pokedex

import com.cobblemon.mod.common.api.net.ClientNetworkPacketHandler
import com.cobblemon.mod.common.client.gui.scan.ScanNodeConfigScreen
import com.cobblemon.mod.common.net.messages.client.pokedex.OpenScanNodeConfigPacket
import net.minecraft.client.Minecraft

object OpenScanNodeConfigHandler : ClientNetworkPacketHandler<OpenScanNodeConfigPacket> {
    override fun handle(packet: OpenScanNodeConfigPacket, client: Minecraft) {
        client.setScreen(ScanNodeConfigScreen(packet.entityId, packet.category, packet.scanId, packet.frames))
    }
}
