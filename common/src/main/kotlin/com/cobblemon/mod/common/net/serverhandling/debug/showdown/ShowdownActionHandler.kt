package com.cobblemon.mod.common.net.serverhandling.debug.showdown

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.api.net.ServerNetworkPacketHandler
import com.cobblemon.mod.common.api.npc.NPCClasses
import com.cobblemon.mod.common.battles.BattleBuilder
import com.cobblemon.mod.common.battles.runner.ShowdownService
import com.cobblemon.mod.common.entity.npc.NPCEntity
import com.cobblemon.mod.common.net.messages.server.debug.showdown.ShowdownActionPacket
import com.cobblemon.mod.common.util.cobblemonResource
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer

/**
 * Handles network packets for Showdown actions on the server side.
 *
 * This handler processes incoming `ShowdownActionPacket` packets and performs the corresponding
 * actions (ie. restart it) on the specified Showdown service.
 *
 * @author landonjw
 */
object ShowdownActionHandler : ServerNetworkPacketHandler<ShowdownActionPacket> {

    /**
     * Handles the received `ShowdownActionPacket`.
     *
     * @param packet The received packet containing the action to perform.
     * @param server The Minecraft server instance.
     * @param player The player who sent the packet.
     */
    override fun handle(packet: ShowdownActionPacket, server: MinecraftServer, player: ServerPlayer) {
        if (!Cobblemon.config.enableDebugKeys) return
        if (!ShowdownService.contains(packet.service)) return

        val service = ShowdownService.get(packet.service)
        when (packet.action) {
            ShowdownActionPacket.Action.START -> service.openConnection()
            ShowdownActionPacket.Action.STOP -> service.closeConnection()
            ShowdownActionPacket.Action.RESTART -> service.restartConnection()
            ShowdownActionPacket.Action.START_NPC_BATTLE -> {
                val npc = NPCEntity(player.level())
                npc.moveTo(player.x + 5, player.y, player.z, player.yRot, player.xRot)
                npc.npc = NPCClasses.getByIdentifier(cobblemonResource("ai_test"))!!
                npc.initialize(1)
                player.level().addFreshEntity(npc)
                BattleBuilder.pvn(
                    player = player,
                    npcEntity = npc,
                    service = service
                )
            }
        }
    }

}
