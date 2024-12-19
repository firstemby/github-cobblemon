package com.cobblemon.mod.common.client.render.block

import com.cobblemon.mod.common.api.pokemon.breeding.Egg
import com.cobblemon.mod.common.block.entity.EggBlockEntity
import com.cobblemon.mod.common.client.render.helper.EggRenderHelper
import com.cobblemon.mod.common.client.render.models.blockbench.TexturedModel
import com.cobblemon.mod.common.client.render.models.blockbench.repository.EggModelRepo
import com.cobblemon.mod.common.util.cobblemonResource
import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.RenderType
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider
import net.minecraft.resources.ResourceLocation

class EggBlockRenderer(ctx: BlockEntityRendererProvider.Context) : BlockEntityRenderer<EggBlockEntity> {
    override fun render(
        entity: EggBlockEntity,
        tickDelta: Float,
        matrices: PoseStack,
        vertexConsumers: MultiBufferSource,
        light: Int,
        overlay: Int
    ) {
        entity.getEgg()?.let { egg ->
            matrices.pushPose() // Start transformation stack
            try {
                // Render the egg model with the dynamically determined texture
                EggRenderHelper.renderEgg(
                    egg = egg,
                    matrices = matrices,
                    vertexConsumers = vertexConsumers,
                    light = light,
                    overlay = overlay,
                    yRot = 0f,
                    scale = 1f,
                    translation = Triple(0.5f, 0.285f, 0.5f), // Adjust translation
                    rotation = Triple(180f, 0f, 0f)           // Adjust rotation
                )
            } finally {
                matrices.popPose() // Ensure this is always called
            }
        }
    }
}
