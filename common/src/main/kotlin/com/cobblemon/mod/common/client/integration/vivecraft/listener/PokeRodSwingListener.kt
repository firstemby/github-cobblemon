package com.cobblemon.mod.common.client.integration.vivecraft.listener

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.CobblemonNetwork
import com.cobblemon.mod.common.api.tags.CobblemonItemTags
import com.cobblemon.mod.common.net.messages.server.pokeball.SendOutPokeBallPacket
import com.cobblemon.mod.common.net.messages.server.pokerod.CastPokeRodPacket
import net.minecraft.client.Minecraft
import net.minecraft.world.InteractionHand
import net.minecraft.world.item.ItemStack
import net.minecraft.world.phys.Vec3
import org.vivecraft.api.client.VRClientAPI
import org.vivecraft.api.client.tracker.swing.SwingContext
import org.vivecraft.api.client.tracker.swing.SwingListener
import org.vivecraft.api.data.VRBodyPart

class PokeRodSwingListener() : SwingListener {

    private val minecraft: Minecraft = Minecraft.getInstance()
    private val clientData: VRClientAPI = VRClientAPI.instance()

    private fun changeForVelocity(bodyPart: VRBodyPart, ticks: Int = 10): Vec3? {
        val history = clientData.historicalVRPoses ?: return null
        if (history.ticksOfHistory() < ticks) return null

        val delta = history.netMovement(bodyPart, ticks)
        delta?.lengthSqr()?.let { if (it < 1e-6) return null } // Ignore tiny movements

        return delta
    }

    override fun onSwingEnd(context: SwingContext?) {
        val player = minecraft.player ?: return
        val ctx = context ?: return
        val useHand: InteractionHand = VRBodyPart.toInteractionHand(ctx.bodyPart) ?: return
        val itemStack: ItemStack = player.getItemInHand(useHand)

        if (!itemStack.`is`(CobblemonItemTags.POKE_RODS)) return

        val handVel = changeForVelocity(ctx.bodyPart) ?: run {
            Cobblemon.LOGGER.info(
                "Ignoring swing: insufficient historical pose data for velocity. bodyPart={}, player={}",
                ctx.bodyPart, player
            )
            return
        }

        // Ignore swings that are moving upward
        val vertical = handVel.y
        if (vertical > 0.0) {
            Cobblemon.LOGGER.info(
                "Ignoring swing: upward movement. vertical={}, handVel={}, player={}",
                vertical, handVel, player
            )
            return
        }

        val forward = player.lookAngle.normalize()
        val handDir = handVel.normalize()
        val forwardDot = handDir.dot(forward)

        // Only allow swings within ~100 degrees of forward (dot > -0.1736)
        if (forwardDot <= 0.3) { // You can tweak this threshold
            Cobblemon.LOGGER.info(
                "Ignoring swing: backward or too sideways. forwardDot={}, handVel={}, player={}",
                forwardDot, handVel, player
            )
            return
        }

        CobblemonNetwork.sendToServer(CastPokeRodPacket(useHand == InteractionHand.MAIN_HAND))
    }

}