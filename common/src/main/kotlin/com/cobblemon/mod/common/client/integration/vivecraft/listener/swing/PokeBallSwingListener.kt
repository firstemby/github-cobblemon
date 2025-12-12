package com.cobblemon.mod.common.client.integration.vivecraft.listener.swing

import com.cobblemon.mod.common.CobblemonNetwork
import com.cobblemon.mod.common.api.tags.CobblemonItemTags
import com.cobblemon.mod.common.net.messages.server.pokeball.SendOutPokeBallPacket
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.phys.Vec3

class PokeBallSwingListener : DirectionalSwingListener() {

    override fun isValidItem(itemStack: ItemStack): Boolean =
        itemStack.`is`(CobblemonItemTags.POKE_BALLS)

    override fun onForwardSwing(
        useHand: InteractionHand,
        handVel: Vec3,
        player: Player
    ): Boolean {
        CobblemonNetwork.sendToServer(SendOutPokeBallPacket())
        return true
    }

    override fun onDownwardSwing(useHand: InteractionHand, handVel: Vec3, player: Player): Boolean {
        CobblemonNetwork.sendToServer(SendOutPokeBallPacket())
        return super.onDownwardSwing(useHand, handVel, player)
    }
}