package com.cobblemon.mod.common.net.serverhandling.breeding

import com.cobblemon.mod.common.block.entity.NestBlockEntity
import com.cobblemon.mod.common.net.messages.server.breeding.TriggerEggHatchPacket
import net.minecraft.server.level.ServerPlayer
import com.cobblemon.mod.common.api.net.ServerNetworkPacketHandler
import net.minecraft.server.MinecraftServer

object TriggerEggHatchHandler : ServerNetworkPacketHandler<TriggerEggHatchPacket> {
    override fun handle(packet: TriggerEggHatchPacket, server: MinecraftServer, player: ServerPlayer) {
        val level = player.serverLevel()
        val be = level.getBlockEntity(packet.pos)
        if (be is NestBlockEntity) {
            be.hatchPokemon(be.blockState, level, packet.pos, player)
        }
    }
}

