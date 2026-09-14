package com.cobblemon.mod.common.net.serverhandling.battle

import com.cobblemon.mod.common.api.net.ServerNetworkPacketHandler
import com.cobblemon.mod.common.battles.BattleRegistry
import com.cobblemon.mod.common.net.messages.server.battle.StadiumPreviewChoicePacket
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer

object StadiumPreviewChoiceHandler : ServerNetworkPacketHandler<StadiumPreviewChoicePacket> {
    override fun handle(packet: StadiumPreviewChoicePacket, server: MinecraftServer, player: ServerPlayer) {
        val battle = BattleRegistry.getBattle(packet.battleId) ?: return
        val actor = battle.actors.find { player.uuid in it.getPlayerUUIDs() } ?: return
        battle.stadiumSession?.submit(actor, packet.rqid, packet.order)
    }
}
