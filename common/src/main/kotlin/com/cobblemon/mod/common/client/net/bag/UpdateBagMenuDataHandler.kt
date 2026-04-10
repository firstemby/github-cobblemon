package com.cobblemon.mod.common.client.net.bag

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.api.net.ServerNetworkPacketHandler
import com.cobblemon.mod.common.item.bag.CobblemonBagMenu
import com.cobblemon.mod.common.net.messages.client.bag.UpdateBagMenuDataPacket
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer

object UpdateBagMenuDataHandler : ServerNetworkPacketHandler<UpdateBagMenuDataPacket> {
    override fun handle(packet: UpdateBagMenuDataPacket, server: MinecraftServer, player: ServerPlayer) {
        if (player.containerMenu !is CobblemonBagMenu) {
            Cobblemon.LOGGER.debug("Player {} interacted with invalid menu {}", player, player.containerMenu)
            return
        }

        val menu = player.containerMenu as? CobblemonBagMenu ?: return
        menu.currentlySelectedCategory = packet.categoryId
        menu.scroll = packet.scroll
        menu.setupSlotsBasedOnContent()
    }
}