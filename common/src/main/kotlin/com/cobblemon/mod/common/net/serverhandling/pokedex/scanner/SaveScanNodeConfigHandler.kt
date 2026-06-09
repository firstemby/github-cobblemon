package com.cobblemon.mod.common.net.serverhandling.pokedex.scanner

import com.cobblemon.mod.common.api.net.ServerNetworkPacketHandler
import com.cobblemon.mod.common.api.pokedex.scan.ScannedDataCategory
import com.cobblemon.mod.common.entity.scan.ScanNodeEntity
import com.cobblemon.mod.common.net.messages.server.pokedex.scanner.SaveScanNodeConfigPacket
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer

object SaveScanNodeConfigHandler : ServerNetworkPacketHandler<SaveScanNodeConfigPacket> {
    override fun handle(packet: SaveScanNodeConfigPacket, server: MinecraftServer, player: ServerPlayer) {
        if (!player.hasPermissions(2) && !player.isCreative) return
        val node = player.serverLevel().getEntity(packet.entityId) as? ScanNodeEntity ?: return
        node.category = ScannedDataCategory.fromId(packet.category) ?: ScannedDataCategory.MISC
        node.scanId = packet.scanId.trim()
        for (i in 0 until ScanNodeEntity.FRAME_COUNT) {
            node.setFrameField(i, packet.frames.getOrElse(i) { "" }.trim())
        }
    }
}
