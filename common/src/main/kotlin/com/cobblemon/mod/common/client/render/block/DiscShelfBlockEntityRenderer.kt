/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.render.block

import com.cobblemon.mod.common.CobblemonItemComponents
import com.cobblemon.mod.common.block.entity.DiscShelfBlockEntity
import com.cobblemon.mod.common.CobblemonItems
import com.cobblemon.mod.common.util.cobblemonResource
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import com.mojang.math.Axis
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.LevelRenderer
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.RenderType
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider
import net.minecraft.core.Direction
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.ResourceLocation
import net.minecraft.util.FastColor
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.BlockAndTintGetter
import net.minecraft.world.level.block.HorizontalDirectionalBlock
import org.joml.Matrix4f

class DiscShelfBlockEntityRenderer(ctx: BlockEntityRendererProvider.Context) :
        BlockEntityRenderer<DiscShelfBlockEntity> {

    private val slotWidthPixels = 6
    private val slotHeightPixels = 2
    private val textureSize = 16f

    private val slotUMax = slotWidthPixels / textureSize
    private val slotVMax = slotHeightPixels / textureSize

    private val leftMargin = 1f
    private val rightMargin = 1f
    private val topMargin = 1f
    private val interRowSpacing = 0f
    private val slotBrightnessMultiplier = 0.65f

    private val musicDiscUnknownTexture = cobblemonResource("textures/block/disc_shelf/music_disc_unknown.png")

    override fun render(
        entity: DiscShelfBlockEntity,
        partialTicks: Float,
        poseStack: PoseStack,
        buffer: MultiBufferSource,
        light: Int,
        overlay: Int
    ) {
        val facing = entity.blockState.getValue(HorizontalDirectionalBlock.FACING)
        val rotation = when (facing) {
            Direction.NORTH -> 0f
            Direction.SOUTH -> 180f
            Direction.WEST -> 90f
            Direction.EAST -> -90f
            else -> 0f
        }

        val lightGetter = entity.level as? BlockAndTintGetter
        val packedLight = lightGetter?.let {
            LevelRenderer.getLightColor(it, entity.blockPos.relative(facing))
        } ?: if (light != 0) light else 0xF000F0

        poseStack.pushPose()
        poseStack.translate(0.5, 0.5, 0.5)
        poseStack.mulPose(Axis.YP.rotationDegrees(rotation))
        poseStack.translate(-0.5, -0.5, -0.5)

        val slotWidth = slotWidthPixels / textureSize
        val slotHeight = slotHeightPixels / textureSize
        val verticalSpacing = (slotHeightPixels + interRowSpacing) / textureSize
        val startY = topMargin / textureSize

        for ((index, item) in entity.items.withIndex()) {
            if (!item.isEmpty) {
                val row = index / 2
                val col = index % 2
                val visualCol = 1 - col

                val isLeftColumn = visualCol == 0
                val posX = if (isLeftColumn)
                    leftMargin / textureSize
                else
                    (16f - rightMargin - slotWidthPixels) / textureSize

                val posY = startY + (6 - row) * verticalSpacing

                poseStack.pushPose()
                poseStack.translate(posX, posY, -0.001f)

                if (item.item == CobblemonItems.TECHNICAL_MACHINE) {
                    val elementId = item.components.get(CobblemonItemComponents.TM_MOVE)!!.move.elementalType.showdownId
                    val texture = cobblemonResource("textures/block/disc_shelf/${elementId}_tm.png")
                    renderSlotQuad(
                            poseStack,
                            buffer,
                            texture,
                            0xFFFFFF,
                            packedLight,
                            slotWidth,
                            slotHeight,
                            tint = true,
                            useTextRenderType = true
                    )
                } else {
                    val tex = getItemTexture(item)
                    renderSlotQuad(
                            poseStack,
                            buffer,
                            tex,
                            0xFFFFFF,
                            packedLight,
                            slotWidth,
                            slotHeight,
                            tint = false,
                            useTextRenderType = true
                    )
                }

                poseStack.popPose()
            }
        }

        poseStack.popPose()
    }

    private fun renderSlotQuad(
            poseStack: PoseStack,
            buffer: MultiBufferSource,
            texture: ResourceLocation,
            color: Int,
            light: Int,
            width: Float,
            height: Float,
            tint: Boolean,
            useTextRenderType: Boolean
    ) {
        val renderType = if (useTextRenderType) RenderType.text(texture) else RenderType.entityCutout(texture)
        val consumer: VertexConsumer = buffer.getBuffer(renderType)
        val matrix: Matrix4f = poseStack.last().pose()

        val (rBase, gBase, bBase, a) = if (tint) {
            listOf(
                    FastColor.ARGB32.red(color) / 255f,
                    FastColor.ARGB32.green(color) / 255f,
                    FastColor.ARGB32.blue(color) / 255f,
                    1.0f
            )
        } else listOf(1f, 1f, 1f, 1f)
        val r = (rBase * slotBrightnessMultiplier).coerceIn(0f, 1f)
        val g = (gBase * slotBrightnessMultiplier).coerceIn(0f, 1f)
        val b = (bBase * slotBrightnessMultiplier).coerceIn(0f, 1f)

        // The quad faces -Z, so a viewer looking at it sees +X on their left.
        // U runs from slotUMax at x = 0 to 0 at x = width so the art is not mirrored.
        consumer.addVertex(matrix, 0f, 0f, 0f).setColor(r, g, b, a).setUv(slotUMax, slotVMax)
                .setUv1(0, 0).setUv2(light and 0xFFFF, light shr 16).setNormal(0f, 0f, -1f)
        consumer.addVertex(matrix, 0f, height, 0f).setColor(r, g, b, a).setUv(slotUMax, 0f)
                .setUv1(0, 0).setUv2(light and 0xFFFF, light shr 16).setNormal(0f, 0f, -1f)
        consumer.addVertex(matrix, width, height, 0f).setColor(r, g, b, a).setUv(0f, 0f)
                .setUv1(0, 0).setUv2(light and 0xFFFF, light shr 16).setNormal(0f, 0f, -1f)
        consumer.addVertex(matrix, width, 0f, 0f).setColor(r, g, b, a).setUv(0f, slotVMax)
                .setUv1(0, 0).setUv2(light and 0xFFFF, light shr 16).setNormal(0f, 0f, -1f)
    }

    private fun getItemTexture(itemStack: ItemStack): ResourceLocation {
        val id = BuiltInRegistries.ITEM.getKey(itemStack.item)
        val resource = cobblemonResource("textures/block/disc_shelf/${id.path}.png")

        val resourceManager = Minecraft.getInstance().resourceManager
        if (resourceManager.getResource(resource).isEmpty) {
            return musicDiscUnknownTexture
        }

        return resource
    }
}
