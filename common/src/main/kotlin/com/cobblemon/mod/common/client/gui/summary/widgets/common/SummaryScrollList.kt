/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.gui.summary.widgets.common

import com.cobblemon.mod.common.api.gui.blitk
import com.cobblemon.mod.common.api.text.bold
import com.cobblemon.mod.common.client.CobblemonResources
import com.cobblemon.mod.common.client.gui.CobblemonRenderable
import com.cobblemon.mod.common.client.render.drawScaledText
import com.cobblemon.mod.common.util.cobblemonResource
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.ObjectSelectionList
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.MutableComponent

abstract class SummaryScrollList<T : ObjectSelectionList.Entry<T>>(
    val listX: Int,
    val listY: Int,
    val label: MutableComponent,
    slotHeight: Int
) : ObjectSelectionList<T>(
    Minecraft.getInstance(),
    WIDTH, // width
    HEIGHT, // height
    0, // top
    slotHeight
), CobblemonRenderable {
    companion object {
        const val WIDTH = 108
        const val HEIGHT = 112
        const val SLOT_WIDTH = 91

        private val backgroundResource = cobblemonResource("textures/gui/summary/summary_scroll_background.png")
        private val scrollOverlayResource = cobblemonResource("textures/gui/summary/summary_scroll_overlay.png")
    }

    private var scrolling = false

    override fun getRowWidth(): Int {
        return SLOT_WIDTH
    }

    init {
        this.x = listX
        this.y = listY
        correctSize()
    }

    override fun scrollBarX(): Int {
        return x + width - 3
    }

    override fun extractListBackground(context: GuiGraphicsExtractor) {}

    override fun extractWidgetRenderState(context: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTicks: Float) {
        val matrices = context.pose()
        correctSize()

        blitk(
            matrixStack = matrices,
            texture = backgroundResource,
            x = listX,
            y = listY,
            height = HEIGHT,
            width = WIDTH
        )
        context.enableScissor(
            x,
            y,
            x + width,
            y + height
        )
        super.extractWidgetRenderState(context, mouseX, mouseY, partialTicks)
        context.disableScissor()

        // Scroll Overlay
        val scrollOverlayOffset = 4
        blitk(
            matrixStack = matrices,
            texture = scrollOverlayResource,
            x = listX,
            y = listY - (scrollOverlayOffset / 2) - 1,
            height = HEIGHT + scrollOverlayOffset + 2,
            width = WIDTH
        )

        // Label
        drawScaledText(
            context = context,
            font = CobblemonResources.DEFAULT_LARGE,
            text = label.bold(),
            x = listX + 32.5,
            y = listY - 13.5,
            centered = true,
            shadow = true
        )

    }

    override fun mouseClicked(mouseButtonEvent: MouseButtonEvent, doubleClick: Boolean): Boolean {
        updateScrollingState(mouseButtonEvent.x, mouseButtonEvent.y)
        if (scrolling) {
            focused = getEntryAtPosition(mouseButtonEvent.x, mouseButtonEvent.y)
            isDragging = true
        }
        return super.mouseClicked(mouseButtonEvent, doubleClick)
    }

    override fun mouseDragged(mouseButtonEvent: MouseButtonEvent, deltaX: Double, deltaY: Double): Boolean {
        val mouseY = mouseButtonEvent.y()
        if (scrolling) {
            if (mouseY < listY) {
                setScrollAmount(0.0)
            } else if (mouseY > bottom) {
                setScrollAmount(maxScrollAmount().toDouble())
            } else {
                setScrollAmount(scrollAmount() + deltaY)
            }
        }
        return super.mouseDragged(mouseButtonEvent, deltaX, deltaY)
    }

    private fun updateScrollingState(mouseX: Double, mouseY: Double) {
        scrolling = mouseX >= this.scrollBarX().toDouble()
                && mouseX < (this.scrollBarX() + 3).toDouble()
                && mouseY >= listY
                && mouseY < bottom
    }

    private fun correctSize() {
        setRectangle(WIDTH, HEIGHT, listX, listY)
    }
}
