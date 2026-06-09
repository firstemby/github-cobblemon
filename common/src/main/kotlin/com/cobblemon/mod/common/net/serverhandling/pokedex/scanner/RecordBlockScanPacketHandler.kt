package com.cobblemon.mod.common.net.serverhandling.pokedex.scanner

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.api.net.ServerNetworkPacketHandler
import com.cobblemon.mod.common.net.messages.server.pokedex.scanner.RecordBlockScanPacket
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer

object RecordBlockScanPacketHandler : ServerNetworkPacketHandler<RecordBlockScanPacket> {
    override fun handle(packet: RecordBlockScanPacket, server: MinecraftServer, player: ServerPlayer) {
        Cobblemon.playerDataManager.getScannedBlocksData(player).record(packet.scanKey)
    }
}
