package com.cobblemon.mod.common.client.render.helper

import com.cobblemon.mod.common.CobblemonNetwork
import com.cobblemon.mod.common.CobblemonSounds
import com.cobblemon.mod.common.api.pokemon.breeding.Egg
import com.cobblemon.mod.common.client.render.models.blockbench.PosableState
import com.cobblemon.mod.common.client.render.models.blockbench.egg.EggModel
import com.cobblemon.mod.common.client.render.models.blockbench.egg.EggState
import com.cobblemon.mod.common.client.render.models.blockbench.fossil.FossilModel
import com.cobblemon.mod.common.client.render.models.blockbench.repository.EggModelRepo
import com.cobblemon.mod.common.client.render.models.blockbench.repository.RenderContext
import com.cobblemon.mod.common.client.render.models.blockbench.repository.VaryingModelRepository
import com.cobblemon.mod.common.net.messages.client.sound.UnvalidatedPlaySoundS2CPacket
import com.cobblemon.mod.common.net.messages.server.breeding.TriggerEggHatchPacket
import com.cobblemon.mod.common.util.cobblemonResource
import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.RenderType
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.client.resources.sounds.SimpleSoundInstance
import net.minecraft.resources.ResourceLocation
import net.minecraft.sounds.SoundEvent
import net.minecraft.sounds.SoundSource

object EggRenderHelper {
    val context = RenderContext().also {
        it.put(RenderContext.DO_QUIRKS, true)
        it.put(RenderContext.RENDER_STATE, RenderContext.RenderState.RESURRECTION_MACHINE) // todo make new one that is false
    }

    /**
     * Renders the given egg with animations based on its time to hatch.
     *
     * @param egg The egg to render.
     * @param matrices The pose stack for transformations.
     * @param vertexConsumers The buffer for rendering.
     * @param light The light level.
     * @param overlay The overlay level.
     * @param yRot The rotation angle in degrees around the Y-axis.
     * @param scale The scale of the egg model.
     * @param translation Adjustments for translation in the form [x, y, z].
     * @param rotation Adjustments for rotation in the form [x, y, z].
     * @param animTime Current animation time in seconds.
     */
    fun renderEgg(
            egg: Egg,
            state: EggState,
            matrices: PoseStack,
            vertexConsumers: MultiBufferSource,
            light: Int,
            overlay: Int,
            yRot: Float,
            scale: Float = 1.0f,
            translation: Triple<Float, Float, Float> = Triple(0.5f, 0.5f, 0.5f),
            rotation: Triple<Float, Float, Float> = Triple(0f, 0f, 0f),
            tickDelta: Float
    ) {
        // Determine the texture
        val form = if (egg.form.lowercase() == "normal") "" else "_${egg.form.lowercase()}"
        val texture = cobblemonResource("textures/egg/${egg.species.path}${form}_egg.png")

        val eggState = state
        eggState.updatePartialTicks(tickDelta)

        val eggModel = VaryingModelRepository.getPoser(cobblemonResource("egg"), eggState) as EggModel
        eggModel.context = context

        val vertexConsumer = vertexConsumers.getBuffer(RenderType.entityCutout(texture))

        eggState.currentModel = eggModel
        val poseName = when { // todo polish these values
            egg.timeToHatch > 9600 -> "stage0" // Above 8 minutes left has No animation
            egg.timeToHatch > 2400 -> "stage1" // 8 minutes left
            egg.timeToHatch > 1200 -> "stage2"  // 2 minutes left
            egg.timeToHatch > 20 -> "stage3"    // 1 minute left
            (egg.timeToHatch == 0F && eggState.isHatching) -> "hatching" // currently trying to hatch....
                else -> "stage4"                // Ready to hatch!
        }
        eggState.setPose(poseName)

        if (poseName == "hatching" && eggState.primaryAnimation == null) {
            val animation = eggModel.getAnimation(eggState, "hatch", eggModel.runtime)
            if (animation is com.cobblemon.mod.common.client.render.models.blockbench.animation.PrimaryAnimation) {
                eggState.addPrimaryAnimation(animation)
            }
        }

        // Trigger hatch packet after animation finishes
        if (state.isHatching && !state.hatchingTriggered && state.isHatchAnimationFinished()) {
            println("Sending hatch packet for block at ${state.renderingBlockPos}")
            state.hatchingTriggered = true
            state.renderingBlockPos?.let {
                CobblemonNetwork.sendToServer(TriggerEggHatchPacket(it))
            }
            eggState.resetHatchingState()
        }

        matrices.pushPose()
        matrices.translate(translation.first, translation.second, translation.third)
        matrices.mulPose(com.mojang.math.Axis.XP.rotationDegrees(rotation.first))
        matrices.mulPose(com.mojang.math.Axis.YP.rotationDegrees(-yRot + rotation.second))
        matrices.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(rotation.third))
        matrices.scale(scale, scale, scale)

        context.put(RenderContext.TEXTURE, texture)
        context.put(RenderContext.RENDER_STATE, RenderContext.RenderState.RESURRECTION_MACHINE)
        context.put(RenderContext.POSABLE_STATE, eggState)

        matrices.pushPose()
        eggModel.applyAnimations(
            entity = null,
            state = eggState,
            headYaw = 0F,
            headPitch = 0F,
            limbSwing = 0F,
            limbSwingAmount = 0F,
            ageInTicks = eggState.animationSeconds * 20
        )

        eggModel.render(context, matrices, vertexConsumer, light, overlay, -0x1)
        eggModel.withLayerContext(vertexConsumers, eggState, VaryingModelRepository.getLayers(cobblemonResource("egg"), eggState)) {
            eggModel.render(context, matrices, vertexConsumer, light, OverlayTexture.NO_OVERLAY, -0x1)
        }

        eggModel.setDefault()
        matrices.popPose()
        matrices.popPose()
    }

    fun playSound(soundEvent: SoundEvent) {
        Minecraft.getInstance().soundManager.play(SimpleSoundInstance.forUI(soundEvent, 1.0F))
    }
}
