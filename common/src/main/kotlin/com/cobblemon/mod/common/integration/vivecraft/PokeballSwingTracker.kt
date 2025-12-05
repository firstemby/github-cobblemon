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
import org.vivecraft.api.client.tracker.swing.SwingSource
import org.vivecraft.api.client.tracker.swing.SwingTracker
import org.vivecraft.api.data.VRBodyPart
import org.vivecraft.client_vr.gameplay.trackers.DebugRenderTracker

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
        if (this.swingSource != null) this.swingSource!!.removeListener(this)
        this.swingSource = source
        if (this.swingSource != null) this.swingSource!!.addListener(this)
    }

    override fun onSwingEnd(context: SwingContext?) {
        val player = minecraft.player ?: return
        val ctx = context ?: return
        val useHand = VRBodyPart.toInteractionHand(ctx.bodyPart) ?: return
        val itemstack: ItemStack = player.getItemInHand(useHand)

        if (!itemstack.`is`(CobblemonItemTags.POKE_BALLS)) return

        CobblemonNetwork.sendToServer(SendOutPokeballPacket())
    }

    override fun inactiveProcess(player: LocalPlayer?) {
        if (this.swingSource != null) {
            this.swingSource!!.removeListener(this)
            this.swingSource = null
        }
    }

    override fun renderDebug() {
    }
}
