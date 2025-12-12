package com.cobblemon.mod.common.client.integration.vivecraft.listener.swing

import com.cobblemon.mod.common.CobblemonNetwork
import com.cobblemon.mod.common.api.tags.CobblemonItemTags
import com.cobblemon.mod.common.net.messages.server.pokerod.CastPokeRodPacket
import com.cobblemon.mod.common.net.messages.server.pokerod.ReelPokeRodPacket
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.phys.Vec3

class PokeRodSwingListener : DirectionalSwingListener() {

    override fun isValidItem(itemStack: ItemStack): Boolean =
        itemStack.`is`(CobblemonItemTags.POKE_RODS)

    override fun onUpwardSwing(useHand: InteractionHand, player: Player): Boolean {
        CobblemonNetwork.sendToServer(ReelPokeRodPacket(useHand == InteractionHand.MAIN_HAND))
        return super.onUpwardSwing(useHand, player)
    }

    override fun onForwardSwing(useHand: InteractionHand, handVel: Vec3, player: Player): Boolean {
        CobblemonNetwork.sendToServer(CastPokeRodPacket(useHand == InteractionHand.MAIN_HAND))
        return super.onForwardSwing(useHand, handVel, player)
    }

    override fun onDownwardSwing(useHand: InteractionHand, handVel: Vec3, player: Player): Boolean {
        CobblemonNetwork.sendToServer(CastPokeRodPacket(useHand == InteractionHand.MAIN_HAND))
        return super.onDownwardSwing(useHand, handVel, player)
    }
}