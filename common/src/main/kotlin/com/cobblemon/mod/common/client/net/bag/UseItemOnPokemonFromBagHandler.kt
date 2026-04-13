package com.cobblemon.mod.common.client.net.bag

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.api.net.ServerNetworkPacketHandler
import com.cobblemon.mod.common.item.bag.CobblemonBagMenu
import com.cobblemon.mod.common.net.messages.client.bag.UseItemOnPokemonFromBagPacket
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer

object UseItemOnPokemonFromBagHandler: ServerNetworkPacketHandler<UseItemOnPokemonFromBagPacket> {
    override fun handle(packet: UseItemOnPokemonFromBagPacket, server: MinecraftServer, player: ServerPlayer) {
        if (player.containerMenu !is CobblemonBagMenu) {
            Cobblemon.LOGGER.debug("Player {} interacted with invalid menu {}", player, player.containerMenu)
            return
        }

        val menu = player.containerMenu as? CobblemonBagMenu ?: return
        menu.useHeldItemOnPokemon(packet.partySlot)
    }
}