package com.cobblemon.mod.common.client.render.helper

import com.cobblemon.mod.common.api.pokemon.breeding.Egg
import com.cobblemon.mod.common.client.render.models.blockbench.egg.EggModel
import com.cobblemon.mod.common.client.render.models.blockbench.repository.EggModelRepo
import com.cobblemon.mod.common.util.cobblemonResource
import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.RenderType
import net.minecraft.resources.ResourceLocation

object EggRenderHelper {

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
        val model = EggModelRepo.eggModel ?: return

        // Determine the texture
        val form = if (egg.form.lowercase() == "normal") "" else "_${egg.form.lowercase()}"
        val texture = cobblemonResource("textures/egg/${egg.species.path}${form}_egg.png")

        // Determine the animation based on time to hatch
        val animationName = when {
            egg.timeToHatch > 2000 -> null // No animation
            egg.timeToHatch > 1000 -> "animation.egg.nestjiggletiny"
            egg.timeToHatch > 500 -> "animation.egg.nestjigglesmall"
            egg.timeToHatch > 200 -> "animation.egg.nestjigglemid"
            else -> "animation.egg.nestjigglelarge"
        }

        if (animationName == null) return

        matrices.pushPose()
        matrices.translate(translation.first, translation.second, translation.third)
        matrices.mulPose(com.mojang.math.Axis.XP.rotationDegrees(rotation.first))
        matrices.mulPose(com.mojang.math.Axis.YP.rotationDegrees(-yRot + rotation.second))
        matrices.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(rotation.third))
        matrices.scale(scale, scale, scale)

        val buffer = vertexConsumers.getBuffer(RenderType.entityCutout(texture))

        // Apply animation
        model.applyAnimations(
                entity = null,
                state = null,
                headYaw = 0f,
                headPitch = 0f,
                limbSwing = 0f,
                limbSwingAmount = 0f,
                ageInTicks = animTime * 20f
        )

        // Render the model
        model.render(null, matrices, buffer, light, overlay, -0x1)

        // Apply and render additional layers
        model.withLayerContext(vertexConsumers, null, listOf()) {
            model.render(null, matrices, buffer, light, OverlayTexture.NO_OVERLAY, -0x1)
        }
        matrices.popPose()
    }
}
