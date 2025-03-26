package com.cobblemon.mod.common.net.serverhandling.debug.showdown

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.api.net.ServerNetworkPacketHandler
import com.cobblemon.mod.common.battles.BattleRegistry.packTeam
import com.cobblemon.mod.common.battles.pokemon.BattlePokemon
import com.cobblemon.mod.common.battles.runner.ShowdownService
import com.cobblemon.mod.common.net.messages.server.debug.showdown.ShowdownActionPacket
import com.cobblemon.mod.common.util.party
import com.google.gson.JsonObject
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
            ShowdownActionPacket.Action.QUEUE -> {
                val cobblemonTeam = player.party().saveToJSON(JsonObject(), player.registryAccess())
                val showdownTeam = player.party().map { BattlePokemon(it, it.clone(false)) { entity -> entity.discard() } }
                showdownTeam.forEach {
                    it.effectedPokemon.level = 100
                    it.effectedPokemon.heal()
                }
                val showdownTeamStr = showdownTeam.packTeam()
                service.send(">queue-battle ${player.uuid} $cobblemonTeam@|@$showdownTeamStr")
            }
        }
    }

}
