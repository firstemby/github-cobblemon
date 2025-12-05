package com.cobblemon.mod.common.integration.vivecraft

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.CobblemonNetwork
import com.cobblemon.mod.common.api.tags.CobblemonItemTags
import com.cobblemon.mod.common.net.messages.server.SendOutPokeballPacket
import net.minecraft.client.Minecraft
import net.minecraft.client.player.LocalPlayer
import net.minecraft.world.item.ItemStack
import org.vivecraft.api.client.Tracker
import org.vivecraft.api.client.VRClientAPI
import org.vivecraft.api.client.tracker.swing.SwingContext
import org.vivecraft.api.client.tracker.swing.SwingTracker
import org.vivecraft.api.data.VRBodyPart
import org.vivecraft.client_vr.gameplay.trackers.DebugRenderTracker
import net.minecraft.world.phys.Vec3
import org.vivecraft.api.client.tracker.swing.SwingSource

class PokeballSwingTracker(
    private val minecraft: Minecraft,
    private val clientData: VRClientAPI
) : SwingTracker, DebugRenderTracker {

    private var swingSource: SwingSource? = null

    override fun processType(): Tracker.ProcessType = Tracker.ProcessType.PER_TICK

    override fun isActive(localPlayer: LocalPlayer?): Boolean = true

    override fun activeProcess(localPlayer: LocalPlayer?) {
        val source = clientData.getTrackerSource(SwingSource::class.java)
        if (this.swingSource === source) return
        this.swingSource?.removeListener(this)
        this.swingSource = source
        this.swingSource?.addListener(this)
    }

    override fun inactiveProcess(player: LocalPlayer?) {
        this.swingSource?.removeListener(this)
        this.swingSource = null
    }

    override fun renderDebug() {}

    private fun changeForVelocity(bodyPart: VRBodyPart, ticks: Int = 20): Vec3? {
        val history = clientData.historicalVRPoses ?: return null
        if (history.ticksOfHistory() < ticks) return null

        val delta = history.netMovement(bodyPart, ticks)
        delta?.lengthSqr()?.let { if (it < 1e-6) return null } // Ignore tiny movements

        return delta
    }

    override fun onSwingEnd(context: SwingContext?) {
        val player = minecraft.player ?: return
        val ctx = context ?: return
        val useHand = VRBodyPart.toInteractionHand(ctx.bodyPart) ?: return
        val itemStack: ItemStack = player.getItemInHand(useHand)

        if (!itemStack.`is`(CobblemonItemTags.POKE_BALLS)) return

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

        CobblemonNetwork.sendToServer(SendOutPokeballPacket())
    }

}