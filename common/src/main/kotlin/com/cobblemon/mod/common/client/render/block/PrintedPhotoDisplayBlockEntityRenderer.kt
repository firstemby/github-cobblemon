/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.render.block

import com.cobblemon.mod.common.block.PrintedPhotoDisplayBlock
import com.cobblemon.mod.common.block.entity.PrintedPhotoDisplayBlockEntity
import com.cobblemon.mod.common.client.CobblemonClient
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.RenderType
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.resources.ResourceLocation

class PrintedPhotoDisplayBlockEntityRenderer(
    @Suppress("UNUSED_PARAMETER") context: BlockEntityRendererProvider.Context
) : BlockEntityRenderer<PrintedPhotoDisplayBlockEntity> {

    companion object {
        private val PLACEHOLDER: ResourceLocation = ResourceLocation.withDefaultNamespace("textures/item/map.png")
    }

    override fun render(
        be: PrintedPhotoDisplayBlockEntity,
        partialTicks: Float,
        matrices: PoseStack,
        bufferSource: MultiBufferSource,
        light: Int,
        overlay: Int
    ) {
        val component = be.component
        val store = CobblemonClient.printedPhotoAssetStore
        val texture = if (component != null) {
            store.getTexture(component.assetId) ?: run {
                store.requestAssets(listOf(component.assetId))
                PLACEHOLDER
            }
        } else PLACEHOLDER

        val buffer = bufferSource.getBuffer(RenderType.entityCutout(texture))
        val axes = PrintedPhotoDisplayBlock.resolveAxes(be.blockState)
        drawQuad(buffer, matrices, light, axes, be.cellX, be.cellY, be.gridSize)
    }

    private fun drawQuad(
        consumer: VertexConsumer,
        matrices: PoseStack,
        light: Int,
        axes: PrintedPhotoDisplayBlock.SurfaceAxes,
        cellX: Int,
        cellY: Int,
        gridSize: Int
    ) {
        val pose = matrices.last()
        val matrix = pose.pose()
        val half = 0.5F
        val normalX = axes.normal.stepX.toFloat()
        val normalY = axes.normal.stepY.toFloat()
        val normalZ = axes.normal.stepZ.toFloat()
        val rightX = axes.right.stepX.toFloat()
        val rightY = axes.right.stepY.toFloat()
        val rightZ = axes.right.stepZ.toFloat()
        val upX = axes.up.stepX.toFloat()
        val upY = axes.up.stepY.toFloat()
        val upZ = axes.up.stepZ.toFloat()

        val offset = -0.499F
        val centreX = 0.5F + normalX * offset
        val centreY = 0.5F + normalY * offset
        val centreZ = 0.5F + normalZ * offset

        val size = gridSize.coerceIn(1, 3)
        val tileX = cellX.coerceIn(0, size - 1)
        val tileY = cellY.coerceIn(0, size - 1)
        val inv = 1F / size.toFloat()
        val u0 = tileX * inv
        val u1 = (tileX + 1) * inv
        val v0 = 1F - (tileY + 1) * inv
        val v1 = 1F - tileY * inv

        val p0x = centreX - rightX * half - upX * half
        val p0y = centreY - rightY * half - upY * half
        val p0z = centreZ - rightZ * half - upZ * half
        val p1x = centreX + rightX * half - upX * half
        val p1y = centreY + rightY * half - upY * half
        val p1z = centreZ + rightZ * half - upZ * half
        val p2x = centreX + rightX * half + upX * half
        val p2y = centreY + rightY * half + upY * half
        val p2z = centreZ + rightZ * half + upZ * half
        val p3x = centreX - rightX * half + upX * half
        val p3y = centreY - rightY * half + upY * half
        val p3z = centreZ - rightZ * half + upZ * half

        consumer.addVertex(matrix, p0x, p0y, p0z)
            .setColor(255, 255, 255, 255).setUv(u0, v1)
            .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, normalX, normalY, normalZ)
        consumer.addVertex(matrix, p1x, p1y, p1z)
            .setColor(255, 255, 255, 255).setUv(u1, v1)
            .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, normalX, normalY, normalZ)
        consumer.addVertex(matrix, p2x, p2y, p2z)
            .setColor(255, 255, 255, 255).setUv(u1, v0)
            .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, normalX, normalY, normalZ)
        consumer.addVertex(matrix, p3x, p3y, p3z)
            .setColor(255, 255, 255, 255).setUv(u0, v0)
            .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, normalX, normalY, normalZ)
    }
}
