package com.cobblemon.mod.client.render.blockentity

import com.cobblemon.mod.common.block.entity.GrottoBlockEntity
import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.LevelRenderer
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider
import net.minecraft.client.renderer.RenderType
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.world.level.block.state.BlockState

class GrottoBlockRenderer(ctx: BlockEntityRendererProvider.Context) : BlockEntityRenderer<GrottoBlockEntity> {
    override fun render(
        entity: GrottoBlockEntity,
        partialTicks: Float,
        poseStack: PoseStack,
        bufferSource: MultiBufferSource,
        packedLight: Int,
        packedOverlay: Int
    ) {
        val stateToRender: BlockState = entity.mimickedState

        val level = entity.level ?: return
        val blockRenderer = Minecraft.getInstance().blockRenderer
        val model = blockRenderer.getBlockModel(stateToRender)
        val renderType = RenderType.solid()
        val light = LevelRenderer.getLightColor(level, entity.blockPos)

        poseStack.pushPose()
        blockRenderer.modelRenderer.renderModel(
            poseStack.last(),                             // current PoseStack pose
            bufferSource.getBuffer(renderType),           // proper buffer
            stateToRender,
            model,
            1.0f, 1.0f, 1.0f,                             // RGB color (full bright)
            light,
            OverlayTexture.NO_OVERLAY
        )
        poseStack.popPose()
    }
}
