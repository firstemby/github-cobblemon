package com.cobblemon.mod.common.net.serverhandling.pokerod

import com.cobblemon.mod.common.api.net.ServerNetworkPacketHandler
import com.cobblemon.mod.common.item.interactive.PokerodItem
import com.cobblemon.mod.common.net.messages.server.pokerod.ApplyBaitPacket
import com.cobblemon.mod.common.util.isServerSide
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionHand

object ApplyBaitHandler : ServerNetworkPacketHandler<ApplyBaitPacket> {

    override fun handle(packet: ApplyBaitPacket, server: MinecraftServer, player: ServerPlayer) {

        val mainHandItem = player.getItemInHand(InteractionHand.MAIN_HAND)
        val offHandItem = player.getItemInHand(InteractionHand.OFF_HAND)

        println("[ApplyBaitHandler] Main hand: ${mainHandItem.item}")
        println("[ApplyBaitHandler] Off hand:  ${offHandItem.item}")

        (mainHandItem.item as? PokerodItem)?.let { pokerod ->
            if (player.level().isServerSide()) {
                pokerod.applyBait(
                    mainHandItem,
                    offHandItem,
                    player
                )
            }
        }

        (offHandItem.item as? PokerodItem)?.let { pokerod ->
            if (player.level().isServerSide()) {
                pokerod.applyBait(
                    offHandItem,
                    mainHandItem,
                    player
                )
            }
        }
    }
}
