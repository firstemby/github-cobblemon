package com.cobblemon.mod.common.client.render.helper

import com.cobblemon.mod.common.api.pokemon.breeding.Egg
import com.cobblemon.mod.common.client.render.models.blockbench.TexturedModel
import com.cobblemon.mod.common.client.render.models.blockbench.repository.EggModelRepo
import com.cobblemon.mod.common.util.cobblemonResource
import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.RenderType
import net.minecraft.resources.ResourceLocation

object EggRenderHelper {
    /**
     * Renders the given egg as a 3D model with the appropriate texture.
     *
     * @param egg The egg to render.
     * @param matrices The pose stack for transformations.
     * @param vertexConsumers The buffer for rendering.
     * @param light The light level.
     * @param overlay The overlay level.
     * @param yRot The rotation angle in degrees around the Y axis.
     * @param scale The scale of the egg model.
     * @param translation Adjustments for translation in the form [x, y, z].
     * @param rotation Adjustments for rotation in the form [x, y, z].
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
        rotation: Triple<Float, Float, Float> = Triple(0f, 0f, 0f)
    ) {
        val model = EggModelRepo.eggModels[cobblemonResource("egg")] ?: return
        val texture = cobblemonResource("textures/egg/${egg.species.path}_egg.png")

        matrices.pushPose()
        matrices.translate(translation.first, translation.second, translation.third)
        matrices.mulPose(com.mojang.math.Axis.XP.rotationDegrees(rotation.first))
        matrices.mulPose(com.mojang.math.Axis.YP.rotationDegrees(-yRot + rotation.second))
        matrices.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(rotation.third))
        matrices.scale(scale, scale, scale)

        val buffer = vertexConsumers.getBuffer(RenderType.entityCutout(texture))
        val bakedModel = model.createWithUvOverride(false, 0, 0, 64, 16).bakeRoot()
        bakedModel.render(matrices, buffer, light, overlay)

        matrices.popPose()
    }
}
