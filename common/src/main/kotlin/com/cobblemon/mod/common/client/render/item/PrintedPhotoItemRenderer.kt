/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.render.item

import com.cobblemon.mod.common.client.CobblemonClient
import com.cobblemon.mod.common.item.components.PrintedPhotoComponent
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.RenderType
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.ItemDisplayContext
import net.minecraft.world.item.ItemStack

/**
 * Draws a printed photo as a flat textured quad in any item display context.
 * Image bytes come from [com.cobblemon.mod.common.client.pokedex.photo.PrintedPhotoAssetClientStore];
 * a missing/loading asset shows a neutral placeholder.
 */
class PrintedPhotoItemRenderer : CobblemonBuiltinItemRenderer {
    companion object {
        private val PLACEHOLDER: ResourceLocation = ResourceLocation.withDefaultNamespace("textures/item/map.png")
    }

    override fun render(
        stack: ItemStack,
        mode: ItemDisplayContext,
        matrices: PoseStack,
        vertexConsumers: MultiBufferSource,
        light: Int,
        overlay: Int
    ) {
        val component = PrintedPhotoComponent.get(stack)
        val store = CobblemonClient.printedPhotoAssetStore
        val textureLocation = if (component != null) {
            store.getTexture(component.assetId) ?: run {
                store.requestAssets(listOf(component.assetId))
                PLACEHOLDER
            }
        } else PLACEHOLDER

        matrices.pushPose()
        applyDisplayTransform(mode, matrices)

        val buffer = vertexConsumers.getBuffer(RenderType.entityCutout(textureLocation))
        drawQuad(buffer, matrices, light)

        matrices.popPose()
    }

    private fun applyDisplayTransform(mode: ItemDisplayContext, matrices: PoseStack) {
        when (mode) {
            ItemDisplayContext.GUI -> {
                matrices.translate(0.5, 0.5, 0.0)
                matrices.scale(1.0F, -1.0F, 1.0F)
            }
            ItemDisplayContext.GROUND -> {
                matrices.translate(0.5, 0.25, 0.5)
                matrices.scale(0.5F, 0.5F, 0.5F)
            }
            ItemDisplayContext.FIXED -> {
                matrices.translate(0.5, 0.5, 0.0)
                matrices.scale(1.0F, 1.0F, 1.0F)
            }
            ItemDisplayContext.FIRST_PERSON_RIGHT_HAND,
            ItemDisplayContext.FIRST_PERSON_LEFT_HAND -> {
                // Hold up like a map / inspect — face the player camera.
                matrices.translate(0.5, 0.5, -0.05)
                matrices.scale(0.9F, 0.9F, 0.01F)
            }
            ItemDisplayContext.THIRD_PERSON_RIGHT_HAND,
            ItemDisplayContext.THIRD_PERSON_LEFT_HAND -> {
                matrices.translate(0.5, 0.5, 0.05)
                matrices.scale(0.7F, 0.7F, 0.01F)
            }
            ItemDisplayContext.HEAD -> {
                matrices.translate(0.5, 0.7, 0.5)
                matrices.scale(1.0F, 1.0F, 0.05F)
            }
            else -> {
                matrices.translate(0.5, 0.5, 0.0)
            }
        }
    }

    private fun drawQuad(consumer: VertexConsumer, matrices: PoseStack, light: Int) {
        val pose = matrices.last()
        val matrix = pose.pose()
        val half = 0.5F

        // CCW quad facing +Z; normal pointing toward viewer.
        consumer.addVertex(matrix, -half, -half, 0F)
            .setColor(255, 255, 255, 255).setUv(0F, 1F)
            .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, 0F, 0F, 1F)
        consumer.addVertex(matrix, half, -half, 0F)
            .setColor(255, 255, 255, 255).setUv(1F, 1F)
            .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, 0F, 0F, 1F)
        consumer.addVertex(matrix, half, half, 0F)
            .setColor(255, 255, 255, 255).setUv(1F, 0F)
            .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, 0F, 0F, 1F)
        consumer.addVertex(matrix, -half, half, 0F)
            .setColor(255, 255, 255, 255).setUv(0F, 0F)
            .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, 0F, 0F, 1F)
    }
}
