package com.cobblemon.mod.common.net.serverhandling.debug

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.api.net.ServerNetworkPacketHandler
import com.cobblemon.mod.common.block.entity.GrottoBlockEntity
import com.cobblemon.mod.common.net.messages.server.debug.UpdateGrottoBlockPacket
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer

object UpdateGrottoBlockHandler : ServerNetworkPacketHandler<UpdateGrottoBlockPacket> {
    override fun handle(packet: UpdateGrottoBlockPacket, server: MinecraftServer, player: ServerPlayer) {
        if (!Cobblemon.config.enableDebugKeys) return

        val block = player.level().getBlockEntity(packet.grottoPos) ?: return
        if (block !is GrottoBlockEntity) return

    }
}