package com.cobblemon.mod.common.net.serverhandling

import com.cobblemon.mod.common.api.net.ServerNetworkPacketHandler
import com.cobblemon.mod.common.net.messages.server.pokemon.update.form.RevertFormPacket
import com.cobblemon.mod.common.util.party
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer

object RevertFormHandler : ServerNetworkPacketHandler<RevertFormPacket> {
    override fun handle(packet: RevertFormPacket, server: MinecraftServer, player: ServerPlayer) {
        val pokemon = player.party().get(packet.pokemonUUID) ?: return
        pokemon.formProxy.server().revert()
    }
}