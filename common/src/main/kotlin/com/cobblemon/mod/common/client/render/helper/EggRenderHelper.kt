package com.cobblemon.mod.common.client.render.helper

import com.cobblemon.mod.common.api.pokemon.breeding.Egg
import com.cobblemon.mod.common.client.render.models.blockbench.PosableState
import com.cobblemon.mod.common.client.render.models.blockbench.egg.EggModel
import com.cobblemon.mod.common.client.render.models.blockbench.fossil.EggState
import com.cobblemon.mod.common.client.render.models.blockbench.repository.EggModelRepo
import com.cobblemon.mod.common.client.render.models.blockbench.repository.RenderContext
import com.cobblemon.mod.common.util.cobblemonResource
import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.RenderType
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.resources.ResourceLocation

object EggRenderHelper {
    val context = RenderContext().also {
        it.put(RenderContext.DO_QUIRKS, true)
        it.put(RenderContext.RENDER_STATE, RenderContext.RenderState.RESURRECTION_MACHINE) // todo make new one that is false
    }

    val eggState = EggState()

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
            animTime: Float
    ) {
        // Retrieve the egg model
        //val model = EggModelRepo. ?: return

        // Determine the texture
        val form = if (egg.form.lowercase() == "normal") "" else "_${egg.form.lowercase()}"
        val texture = cobblemonResource("textures/egg/${egg.species.path}${form}_egg.png")

        // render state
        val eggState = state

        val eggModel = EggModelRepo.getPoser(cobblemonResource("egg.geo"), eggState)
        eggModel.context = context

        val vertexConsumer = vertexConsumers.getBuffer(RenderType.entityCutout(texture))

        eggState.currentModel = eggModel
        val poseName = when {
            egg.timeToHatch > 20000 -> "stage0" // No animation
            egg.timeToHatch > 15000 -> "stage1"
            egg.timeToHatch > 10000 -> "stage2"
            egg.timeToHatch > 20 -> "stage3"
            else -> "stage4"
        }
        eggState.setPose(poseName)

        matrices.pushPose()
        matrices.translate(translation.first, translation.second, translation.third)
        matrices.mulPose(com.mojang.math.Axis.XP.rotationDegrees(rotation.first))
        matrices.mulPose(com.mojang.math.Axis.YP.rotationDegrees(-yRot + rotation.second))
        matrices.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(rotation.third))
        matrices.scale(scale, scale, scale)

        context.put(RenderContext.TEXTURE, texture)
        context.put(RenderContext.RENDER_STATE, RenderContext.RenderState.RESURRECTION_MACHINE)
        context.put(RenderContext.POSABLE_STATE, eggState)

        matrices.pushPose() // todo may not need to push
        eggModel.applyAnimations(
            entity = null,
            state = eggState,
            headYaw = 0F,
            headPitch = 0F,
            limbSwing = 0F,
            limbSwingAmount = 0F,
            ageInTicks = animTime * 20
        )

        eggModel.render(context, matrices, vertexConsumer, light, overlay, -0x1)
        eggModel.withLayerContext(vertexConsumers, eggState, EggModelRepo.getLayers(cobblemonResource("egg"), eggState)) {
            eggModel.render(context, matrices, vertexConsumer, light, OverlayTexture.NO_OVERLAY, -0x1)
        }

        eggModel.setDefault()
        matrices.popPose()
        matrices.popPose()
    }
}
