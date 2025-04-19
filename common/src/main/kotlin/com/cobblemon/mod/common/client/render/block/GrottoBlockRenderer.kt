package com.cobblemon.mod.common.client.render.block

import com.cobblemon.mod.common.block.entity.GrottoBlockEntity
import com.cobblemon.mod.common.util.cobblemonResource
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
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

        poseStack.pushPose()

        Minecraft.getInstance().blockRenderer.renderBatched(
            stateToRender,
            entity.blockPos,
            level,
            poseStack,
            bufferSource.getBuffer(RenderType.solid()),
            true,
            level.random
        )

        val overlayTexture = cobblemonResource("textures/block/grotto_overlay.png")
        val overlayRenderType = RenderType.entityTranslucentCull(overlayTexture)
        val overlayBuffer = bufferSource.getBuffer(overlayRenderType)

        renderOverlayCube(
            poseStack,
            overlayBuffer,
            packedLight,
            OverlayTexture.NO_OVERLAY
        )

        poseStack.popPose()
    }
}

fun renderOverlayCube(
    poseStack: PoseStack,
    buffer: VertexConsumer,
    light: Int,
    overlay: Int
) {
    val matrix = poseStack.last().pose()
    val min = -0.001f
    val max = 1.001f

    // Front (Z+)
    buffer.addVertex(matrix, min, min, max).setColor(255, 255, 255, 128)
        .setUv(min, max).setOverlay(overlay).setLight(light).setNormal(min, min, max)
    buffer.addVertex(matrix, max, min, max).setColor(255, 255, 255, 128)
        .setUv(max, max).setOverlay(overlay).setLight(light).setNormal(min, min, max)
    buffer.addVertex(matrix, max, max, max).setColor(255, 255, 255, 128)
        .setUv(max, min).setOverlay(overlay).setLight(light).setNormal(min, min, max)
    buffer.addVertex(matrix, min, max, max).setColor(255, 255, 255, 128)
        .setUv(min, min).setOverlay(overlay).setLight(light).setNormal(min, min, max)

    // Back (Z-)
    buffer.addVertex(matrix, max, min, min).setColor(255, 255, 255, 128)
        .setUv(min, max).setOverlay(overlay).setLight(light).setNormal(min, min, -max)
    buffer.addVertex(matrix, min, min, min).setColor(255, 255, 255, 128)
        .setUv(max, max).setOverlay(overlay).setLight(light).setNormal(min, min, -max)
    buffer.addVertex(matrix, min, max, min).setColor(255, 255, 255, 128)
        .setUv(max, min).setOverlay(overlay).setLight(light).setNormal(min, min, -max)
    buffer.addVertex(matrix, max, max, min).setColor(255, 255, 255, 128)
        .setUv(min, min).setOverlay(overlay).setLight(light).setNormal(min, min, -max)

    // Left (X-)
    buffer.addVertex(matrix, min, min, min).setColor(255, 255, 255, 128)
        .setUv(min, max).setOverlay(overlay).setLight(light).setNormal(-max, min, min)
    buffer.addVertex(matrix, min, min, max).setColor(255, 255, 255, 128)
        .setUv(max, max).setOverlay(overlay).setLight(light).setNormal(-max, min, min)
    buffer.addVertex(matrix, min, max, max).setColor(255, 255, 255, 128)
        .setUv(max, min).setOverlay(overlay).setLight(light).setNormal(-max, min, min)
    buffer.addVertex(matrix, min, max, min).setColor(255, 255, 255, 128)
        .setUv(min, min).setOverlay(overlay).setLight(light).setNormal(-max, min, min)

    // Right (X+)
    buffer.addVertex(matrix, max, min, max).setColor(255, 255, 255, 128)
        .setUv(min, max).setOverlay(overlay).setLight(light).setNormal(max, min, min)
    buffer.addVertex(matrix, max, min, min).setColor(255, 255, 255, 128)
        .setUv(max, max).setOverlay(overlay).setLight(light).setNormal(max, min, min)
    buffer.addVertex(matrix, max, max, min).setColor(255, 255, 255, 128)
        .setUv(max, min).setOverlay(overlay).setLight(light).setNormal(max, min, min)
    buffer.addVertex(matrix, max, max, max).setColor(255, 255, 255, 128)
        .setUv(min, min).setOverlay(overlay).setLight(light).setNormal(max, min, min)

    // Top (Y+)
    buffer.addVertex(matrix, min, max, max).setColor(255, 255, 255, 128)
        .setUv(min, max).setOverlay(overlay).setLight(light).setNormal(min, max, min)
    buffer.addVertex(matrix, max, max, max).setColor(255, 255, 255, 128)
        .setUv(max, max).setOverlay(overlay).setLight(light).setNormal(min, max, min)
    buffer.addVertex(matrix, max, max, min).setColor(255, 255, 255, 128)
        .setUv(max, min).setOverlay(overlay).setLight(light).setNormal(min, max, min)
    buffer.addVertex(matrix, min, max, min).setColor(255, 255, 255, 128)
        .setUv(min, min).setOverlay(overlay).setLight(light).setNormal(min, max, min)

    // Bottom (Y-)
    buffer.addVertex(matrix, min, min, min).setColor(255, 255, 255, 128)
        .setUv(min, max).setOverlay(overlay).setLight(light).setNormal(min, -max, min)
    buffer.addVertex(matrix, max, min, min).setColor(255, 255, 255, 128)
        .setUv(max, max).setOverlay(overlay).setLight(light).setNormal(min, -max, min)
    buffer.addVertex(matrix, max, min, max).setColor(255, 255, 255, 128)
        .setUv(max, min).setOverlay(overlay).setLight(light).setNormal(min, -max, min)
    buffer.addVertex(matrix, min, min, max).setColor(255, 255, 255, 128)
        .setUv(min, min).setOverlay(overlay).setLight(light).setNormal(min, -max, min)
}

