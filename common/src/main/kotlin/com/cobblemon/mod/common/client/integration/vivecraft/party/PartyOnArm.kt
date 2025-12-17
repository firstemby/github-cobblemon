package com.cobblemon.mod.common.client.integration.vivecraft.party

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.client.CobblemonClient
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.platform.events.ClientTickEvent
import com.cobblemon.mod.common.platform.events.PlatformEvents
import com.cobblemon.mod.common.platform.events.RenderEvent
import com.mojang.math.Axis
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.player.LocalPlayer
import net.minecraft.client.renderer.LightTexture
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.InteractionHand
import net.minecraft.world.phys.Vec3
import org.joml.Vector3f
import org.vivecraft.api.VRAPI
import org.vivecraft.api.client.InteractModule
import org.vivecraft.api.client.VRClientAPI
import org.vivecraft.api.data.VRBodyPartData
import java.util.function.Consumer

class PartyOnArm : InteractModule {

    val fakePartyEntities: Array<PokemonEntity?> = arrayOfNulls(6)
    var selectedIndex = -1

    private fun onTick(tickEvent: ClientTickEvent.Post) {
        val client = tickEvent.client
        val player = client.player ?: return
        val playerData = VRClientAPI.instance().postTickWorldPose ?: return
        val offHandData = playerData.offHand

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

        // Check primary hand for overlap with any of the Pokemon
        val centerPos = getCenterPos(offHandData)
        selectedIndex = -1
        for (i in 0..5) {
            if (party.get(i) != null) {
                val pokemonPos = getPokemonPos(centerPos, playerData.offHand, i)
                if (playerData.mainHand.pos.distanceToSqr(pokemonPos) < DIST_SELECT_SQR) {
                    selectedIndex = i
                    break
                }
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
        val offHandData = VRClientAPI.instance().getWorldRenderPose()?.offHand ?: return

        val centerPos = getCenterPos(offHandData)


        for (i in 0..5) {
            val pokemon = fakePartyEntities[i]
            if (pokemon != null) {
                poseStack.pushPose()
                val pokemonPos = getPokemonPos(centerPos, offHandData, i)
                val renderPos = Vec3(
                    -cameraPos.x + pokemonPos.x,
                    -cameraPos.y + pokemonPos.y,
                    -cameraPos.z + pokemonPos.z,
                )
                poseStack.translate(renderPos.x, renderPos.y, renderPos.z)
                poseStack.scale(0.05f, 0.05f, 0.05f)
                poseStack.mulPose(Axis.YN.rotation(offHandData.yaw.toFloat()))
                poseStack.mulPose(Axis.XN.rotation(offHandData.pitch.toFloat()))
                poseStack.mulPose(Axis.ZP.rotation(offHandData.roll.toFloat() + Math.PI.toFloat()))
                mc.entityRenderDispatcher.getRenderer(pokemon).render(pokemon, 0f, partialTick, poseStack, bufferSource,
                    LightTexture.FULL_BRIGHT)
                poseStack.popPose()
            }
        }
    }

    private fun getCenterPos(pose: VRBodyPartData): Vec3 {
        return pose.pos.subtract(pose.dir.scale(0.125))
    }

    private fun getPokemonPos(centerPos: Vec3, controllerData: VRBodyPartData, index: Int): Vec3 {
        var pos = centerPos
        // Move to bottom of hand
        var downVecF = Vector3f(0f, -0.0625f, 0f)
        downVecF.rotate(Axis.ZP.rotation(controllerData.roll.toFloat()))
        downVecF.rotate(Axis.XN.rotation(controllerData.pitch.toFloat()))
        downVecF.rotate(Axis.YN.rotation(controllerData.yaw.toFloat()))
        pos = pos.add(Vec3(downVecF.x.toDouble(), downVecF.y.toDouble(), downVecF.z.toDouble()))
        // Move right or left based on index
        val rightF = Vector3f(0f, 0f, 1f) // Default forward vector of Minecraft
        rightF.rotate(Axis.YN.rotation(Math.PI.toFloat() / 2f))
        rightF.rotate(Axis.ZP.rotation(controllerData.roll.toFloat()))
        rightF.rotate(Axis.XN.rotation(controllerData.pitch.toFloat()))
        rightF.rotate(Axis.YN.rotation(controllerData.yaw.toFloat()))
        val right = Vec3(rightF.x.toDouble(), rightF.y.toDouble(), rightF.z.toDouble())
        if (index > 2) {
            pos = pos.add(right.scale(-0.0625))
        } else {
            pos = pos.add(right.scale(0.0625))
        }
        // Offset by -hand direction if on left, 0 if center, or +hand direction if right
        return pos.add(controllerData.dir.scale(DIST_BETWEEN_HORIZONTAL * (index % 3 - 1)))
    }

    override fun getId(): ResourceLocation {
        return INTERACT_ID
    }

    override fun isActive(
        player: LocalPlayer,
        hand: InteractionHand,
        handPosition: Vec3
    ): Boolean {
        return hand == InteractionHand.MAIN_HAND && selectedIndex > -1
    }

    override fun onPress(
        player: LocalPlayer,
        hand: InteractionHand
    ): Boolean {
        // UUID lookup is done at press time since the Pokemon may have switched since initially hovered over, such
        // as via commands.
        val uuid = CobblemonClient.storage.party.get(selectedIndex)?.uuid ?: return false
        CobblemonClient.storage.switchToPokemon(uuid)
        return true
    }

    companion object {
        const val DIST_BETWEEN_HORIZONTAL: Double = 0.125
        const val DIST_BETWEEN_VERTICAL: Float = 0.05f
        const val DIST_SELECT_SQR: Double = 0.1 * 0.1

        val INTERACT_ID = ResourceLocation(Cobblemon.MODID, "party_on_arm_interact_module")

        private val INSTANCE = PartyOnArm()

        fun init() {
            PlatformEvents.RENDER.subscribe(Consumer { renderEvent: RenderEvent? -> INSTANCE.onRender(renderEvent!!) })
            PlatformEvents.CLIENT_TICK_POST.subscribe(Consumer { tickEvent: ClientTickEvent.Post? ->
                INSTANCE.onTick(
                    tickEvent!!
                )
            })
            VRAPI.instance().register(INSTANCE)
        }
    }
}
