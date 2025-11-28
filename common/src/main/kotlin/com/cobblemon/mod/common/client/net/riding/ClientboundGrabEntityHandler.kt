package com.cobblemon.mod.common.client.net.riding

import com.cobblemon.mod.common.api.net.ClientNetworkPacketHandler
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.net.messages.client.orientation.ClientboundUpdateDriverInputPacket
import com.cobblemon.mod.common.net.messages.client.riding.ClientboundGrabEntityPacket
import net.minecraft.client.Minecraft
import net.minecraft.client.player.RemotePlayer

object ClientboundGrabEntityHandler : ClientNetworkPacketHandler<ClientboundGrabEntityPacket> {
    override fun handle(packet: ClientboundGrabEntityPacket, client: Minecraft) {
        client.executeIfPossible {
            val level = client.level ?: return@executeIfPossible
            val vehicle = level.getEntity(packet.vehicle) ?: return@executeIfPossible
            val entity = packet.entity?.let { level.getEntity(it) }
            val pokemon = vehicle as? PokemonEntity ?: return@executeIfPossible

            if (entity == null) {
                pokemon.entityHook?.stopRiding()
                pokemon.entityHook = null
            }
            else {
                pokemon.entityHook?.stopRiding()
                entity.startRiding(pokemon, true)
                pokemon.entityHook = entity
            }
        }
    }
}
