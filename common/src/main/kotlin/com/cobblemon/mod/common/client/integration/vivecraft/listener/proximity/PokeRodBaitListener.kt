package com.cobblemon.mod.common.client.integration.vivecraft.listener.proximity

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.CobblemonNetwork
import com.cobblemon.mod.common.api.tags.CobblemonItemTags
import com.cobblemon.mod.common.client.integration.vivecraft.proximity.ProximityListener
import com.cobblemon.mod.common.client.integration.vivecraft.proximity.ProximityRegistration
import com.cobblemon.mod.common.net.messages.server.pokerod.ApplyBaitPacket
import net.minecraft.client.Minecraft
import net.minecraft.world.InteractionHand
import net.minecraft.world.item.ItemStack
import org.vivecraft.api.data.VRBodyPart
import org.vivecraft.api.data.VRPose

class PokeRodBaitListener(private val name: String) : ProximityListener {
    //TODO: Also add that if 1 is Rod the other needs to be seasoning
    override fun isActive(registration: ProximityRegistration): Boolean {
        val player = Minecraft.getInstance().player ?: return false
        val handA: InteractionHand = VRBodyPart.toInteractionHand(registration.bodyA) ?: return false
        val itemStackA: ItemStack = player.getItemInHand(handA)

        val handB: InteractionHand = VRBodyPart.toInteractionHand(registration.bodyB) ?: return false
        val itemStackB: ItemStack = player.getItemInHand(handB)

        return itemStackA.`is`(CobblemonItemTags.POKE_RODS) || itemStackB.`is`(CobblemonItemTags.POKE_RODS)
    }

    override fun onEnter(bodyA: VRBodyPart, bodyB: VRBodyPart, pose: VRPose) {
        Cobblemon.LOGGER.info("[$name] Enter: $bodyA <-> $bodyB")
    }

    override fun onExit(bodyA: VRBodyPart, bodyB: VRBodyPart, pose: VRPose) {
        Cobblemon.LOGGER.info("[$name] Exit: $bodyA <-> $bodyB")
    }

    override fun onProximity(bodyA: VRBodyPart, bodyB: VRBodyPart, pose: VRPose) {
        Cobblemon.LOGGER.info("[$name] Proximity: $bodyA <-> $bodyB")
        CobblemonNetwork.sendToServer(ApplyBaitPacket())
    }
}