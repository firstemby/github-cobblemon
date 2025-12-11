package com.cobblemon.mod.common.client.integration.vivecraft.party

import com.cobblemon.mod.common.client.CobblemonClient
import com.cobblemon.mod.common.client.CobblemonClient.overlay
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.platform.events.ClientTickEvent
import com.cobblemon.mod.common.platform.events.PlatformEvents
import com.cobblemon.mod.common.platform.events.RenderEvent
import com.cobblemon.mod.common.pokemon.Pokemon
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.renderer.LightTexture
import net.minecraft.world.phys.Vec3
import org.vivecraft.api.client.VRClientAPI
import org.vivecraft.api.data.VRPose
import java.util.function.Consumer

class PartyOnArm {

    val fakePartyEntities: Array<PokemonEntity?> = arrayOfNulls(6)

    private fun onTick(tickEvent: ClientTickEvent.Post) {
        val client = tickEvent.client
        val player = client.player
        if (player == null) return

        // Keep up-to-date fakePartyEntities, which is done so we always have a Pokemon entity available to render
        // and keep it ticking so we have proper animations.
        val party = CobblemonClient.storage.party
        for (i in 0..5) {
            val actual = party.get(i)
            val expected = fakePartyEntities[i]?.pokemon
            if (expected != actual) {
                if (actual == null) {
                    fakePartyEntities[i] = null
                } else {
                    fakePartyEntities[i] = PokemonEntity(client.level!!, actual)
                }
            } else {
                fakePartyEntities[i]?.tick()
            }
        }
    }

    private fun onRender(renderEvent: RenderEvent) {
        if (renderEvent.stage != RenderEvent.Stage.AFTER_ENTITIES) return
        val mc = Minecraft.getInstance()
        val bufferSource = mc.renderBuffers().bufferSource()
        val guiGraphics = GuiGraphics(mc, mc.renderBuffers().bufferSource())
        val cameraPos = renderEvent.camera.getPosition()
        val poseStack = guiGraphics.pose()
        val partialTick = renderEvent.tickCounter.getGameTimeDeltaPartialTick(true)
        poseStack.pushPose()

        val centerPos = getPos(VRClientAPI.instance().getWorldRenderPose())
        val renderPos = Vec3(
            -cameraPos.x + centerPos.x,
            -cameraPos.y + centerPos.y,
            -cameraPos.z + centerPos.z,
        )
        poseStack.translate(renderPos.x, renderPos.y, renderPos.z)

        val pokemon = fakePartyEntities[0]
        if (pokemon != null) {
            mc.entityRenderDispatcher.getRenderer(pokemon).render(pokemon, 0f, partialTick, poseStack, bufferSource,
                LightTexture.FULL_BRIGHT)
        }

        poseStack.popPose()
    }

    private fun getPos(pose: VRPose?): Vec3 {
        // TODO: Get an actual position and rotation
        return Minecraft.getInstance().player!!.getEyePosition()
            .add(Minecraft.getInstance().player!!.getLookAngle().scale(4.0))
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
