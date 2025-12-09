package com.cobblemon.mod.common.client.integration.vivecraft.party

import com.cobblemon.mod.common.client.CobblemonClient.overlay
import com.cobblemon.mod.common.platform.events.ClientTickEvent
import com.cobblemon.mod.common.platform.events.PlatformEvents
import com.cobblemon.mod.common.platform.events.RenderEvent
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.world.phys.Vec3
import org.vivecraft.api.client.VRClientAPI
import org.vivecraft.api.data.VRPose
import java.util.function.Consumer

class PartyOnArm {
    private fun onTick(tickEvent: ClientTickEvent.Post) {
        val client = tickEvent.client
        val player = client.player
        if (player == null) return
    }

    private fun onRender(renderEvent: RenderEvent) {
        if (renderEvent.stage != RenderEvent.Stage.AFTER_ENTITIES) return
        val mc = Minecraft.getInstance()
        val guiGraphics = GuiGraphics(mc, mc.renderBuffers().bufferSource())
        val cameraPos = renderEvent.camera.getPosition()
        val poseStack = guiGraphics.pose()
        poseStack.pushPose()

        val centerPos = getPos(VRClientAPI.instance().getWorldRenderPose())
        val renderPos = Vec3(
            -cameraPos.x + centerPos.x,
            -cameraPos.y + centerPos.y,
            -cameraPos.z + centerPos.z,
        )
        poseStack.translate(renderPos.x, renderPos.y, renderPos.z)

        overlay.render(guiGraphics, renderEvent.tickCounter)

        poseStack.popPose()
    }

    private fun getPos(pose: VRPose?): Vec3 {
        // TODO: Get an actual position and rotation
        return Minecraft.getInstance().player!!.getEyePosition()
            .add(Minecraft.getInstance().player!!.getLookAngle().scale(16.0))
    }

    companion object {
        private val INSTANCE = PartyOnArm()

        fun init() {
            PlatformEvents.RENDER.subscribe(Consumer { renderEvent: RenderEvent? -> INSTANCE.onRender(renderEvent!!) })
            PlatformEvents.CLIENT_TICK_POST.subscribe(Consumer { tickEvent: ClientTickEvent.Post? ->
                INSTANCE.onTick(
                    tickEvent!!
                )
            })
        }
    }
}
