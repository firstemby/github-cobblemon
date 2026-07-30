/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.gui.pokedex

import com.cobblemon.mod.common.api.gui.blitk
import com.cobblemon.mod.common.api.text.font
import com.cobblemon.mod.common.client.CobblemonResources
import com.cobblemon.mod.common.client.render.drawScaledText
import com.cobblemon.mod.common.util.cobblemonResource
import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.network.chat.MutableComponent
import net.minecraft.resources.ResourceLocation

private val tooltipEdge = cobblemonResource("textures/gui/pokedex/tooltip_edge.png")
private val tooltipBackground = cobblemonResource("textures/gui/pokedex/tooltip_background.png")

private const val TOOLTIP_SLICE_SIZE = 11

fun renderTooltip(
    context: GuiGraphics,
    text: MutableComponent,
    posX: Int,
    posY: Int,
    delta: Float,
    offsetY: Int = 0,
) {
    renderTooltip(
        context,
        listOf(text),
        posX,
        posY,
        delta,
        offsetY,
    )
}

fun renderTooltip(
    context: GuiGraphics,
    text: List<MutableComponent>,
    posX: Int,
    posY: Int,
    delta: Float,
    offsetY: Int = 0,
) {
    val poseStack = context.pose()
    val textWidth = text.maxOf { Minecraft.getInstance().font.width(it.font(CobblemonResources.DEFAULT_LARGE)) }
    val tooltipPadding = 6
    val tooltipWidth = textWidth + tooltipPadding
    val tooltipTop = posY + offsetY

    poseStack.pushPose()
    poseStack.translate(0.0, 0.0, 1000.0)

    Minecraft.getInstance().let {
        it.mainRenderTarget.bindWrite(false)
        context.enableScissor(
            posX - (tooltipWidth / 2),
            tooltipTop + 1,
            (posX - (tooltipWidth / 2)) + tooltipWidth,
            tooltipTop + TOOLTIP_SLICE_SIZE * text.size - 1
        )
        it.gameRenderer.processBlurEffect(delta)
        context.disableScissor()
        it.mainRenderTarget.bindWrite(true)
    }

    val totalHeight = TOOLTIP_SLICE_SIZE * text.size

    blitSlicedVertical(poseStack, tooltipEdge, posX - (tooltipWidth / 2) - 1, tooltipTop, 1, totalHeight)
    blitSlicedVertical(poseStack, tooltipBackground, posX - (tooltipWidth / 2), tooltipTop, tooltipWidth, totalHeight)
    blitSlicedVertical(poseStack, tooltipEdge, posX + (tooltipWidth / 2), tooltipTop, 1, totalHeight)

    val centered = text.size == 1

    text.forEachIndexed { index, it ->
        drawScaledText(
            context = context,
            font = CobblemonResources.DEFAULT_LARGE,
            text = it,
            x = if (centered) posX else posX - tooltipWidth / 2 + tooltipPadding / 2,
            y = tooltipTop + 1 + TOOLTIP_SLICE_SIZE * index,
            shadow = true,
            centered = centered
        )
    }

    poseStack.popPose()
}

// Stretching the whole 11px image over an arbitrary height thickens the caps as more lines are added, so
// instead the caps are drawn 1:1 and only the flat fill row is stretched to cover the remaining height
private fun blitSlicedVertical(
    poseStack: PoseStack,
    texture: ResourceLocation,
    x: Number,
    y: Int,
    width: Number,
    totalHeight: Int,
) {
    val middleHeight = totalHeight - 2

    blitk(matrixStack = poseStack, texture = texture, x = x, y = y, width = width, height = 1, vOffset = 0, textureHeight = TOOLTIP_SLICE_SIZE)
    blitk(matrixStack = poseStack, texture = texture, x = x, y = y + 1, width = width, height = middleHeight, vOffset = middleHeight, textureHeight = TOOLTIP_SLICE_SIZE * middleHeight)
    blitk(matrixStack = poseStack, texture = texture, x = x, y = y + 1 + middleHeight, width = width, height = 1, vOffset = TOOLTIP_SLICE_SIZE - 1, textureHeight = TOOLTIP_SLICE_SIZE)
}