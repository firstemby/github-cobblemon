package com.cobblemon.mod.common.client.integration.vivecraft.tracker

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.CobblemonNetwork
import com.cobblemon.mod.common.api.tags.CobblemonItemTags
import com.cobblemon.mod.common.client.integration.vivecraft.proximity.ProximityListener
import com.cobblemon.mod.common.net.messages.server.pokerod.ApplyBaitPacket
import net.minecraft.client.Minecraft
import net.minecraft.world.InteractionHand
import net.minecraft.world.item.ItemStack
import org.vivecraft.api.data.VRBodyPart
import org.vivecraft.api.data.VRPose

//TODO: Remove Logger once this is all functional
class PokeRodBaitListener(private val name: String) : ProximityListener {
    override fun onEnter(bodyA: VRBodyPart, bodyB: VRBodyPart, pose: VRPose) {
        Cobblemon.LOGGER.info("[$name] Enter: $bodyA <-> $bodyB")
    }

    override fun onExit(bodyA: VRBodyPart, bodyB: VRBodyPart, pose: VRPose) {
        Cobblemon.LOGGER.info("[$name] Exit: $bodyA <-> $bodyB")
    }

    override fun onProximity(bodyA: VRBodyPart, bodyB: VRBodyPart, pose: VRPose) {
        Cobblemon.LOGGER.info("[$name] Proximity: $bodyA <-> $bodyB")
        val player = Minecraft.getInstance().player ?: return
        val useHandA: InteractionHand = VRBodyPart.toInteractionHand(bodyA) ?: return
        val itemStackA: ItemStack = player.getItemInHand(useHandA)

        val useHandB: InteractionHand = VRBodyPart.toInteractionHand(bodyB) ?: return
        val itemStackB: ItemStack = player.getItemInHand(useHandB)

        if (itemStackA.`is`(CobblemonItemTags.POKE_RODS)) {
            CobblemonNetwork.sendToServer(ApplyBaitPacket(true))
        } else if (itemStackB.`is`(CobblemonItemTags.POKE_RODS)) {
            CobblemonNetwork.sendToServer(ApplyBaitPacket(false))
        }
    }
}
