/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.gui.battle.widgets

import com.cobblemon.mod.common.api.gui.blitk
import com.cobblemon.mod.common.api.text.text
import com.cobblemon.mod.common.client.battle.ClientBattleMessageQueue
import com.cobblemon.mod.common.client.gui.CobblemonRenderable
import com.cobblemon.mod.common.client.render.drawScaledText
import com.cobblemon.mod.common.util.cobblemonResource
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.ObjectSelectionList
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.util.FormattedCharSequence

/**
 * Pane for seeing and interacting with battle messages.
 *
 * @author Hiroku
 * @since June 24th, 2022
 */
class BattleMessagePane(
    messageQueue: ClientBattleMessageQueue
): ObjectSelectionList<BattleMessagePane.BattleMessageLine>(
    Minecraft.getInstance(),
    FRAME_WIDTH, // width
    FRAME_HEIGHT, // height
    1, // top
    LINE_HEIGHT
), CobblemonRenderable {
    var opacity = 1F
    private var scrolling = false

    val appropriateX: Int
        get() = minecraft.window.guiScaledWidth - (FRAME_WIDTH + 12)
    val appropriateY: Int
        get() = minecraft.window.guiScaledHeight - (30 + (if (expanded) FRAME_EXPANDED_HEIGHT else FRAME_HEIGHT))

    init {
        correctSize()
        //setRenderBackground(false)

        messageQueue.subscribe {
            val fullyScrolledDown = maxScrollAmount() - scrollAmount < 10
            addEntry(BattleMessageLine(this, it))
            if (fullyScrolledDown) {
                scrollAmount = maxScrollAmount().toDouble()
            }
        }
    }

    private fun correctSize() {
        val textBoxHeight = if (expanded) TEXT_BOX_HEIGHT * 2 else TEXT_BOX_HEIGHT
        setRectangle(TEXT_BOX_WIDTH, textBoxHeight, appropriateY + 6, appropriateY + 6)
        this.x = appropriateX
    }

    companion object {
        const val LINE_HEIGHT = 10
        const val LINE_WIDTH = 146
        const val FRAME_WIDTH = 169
        const val FRAME_HEIGHT = 55
        const val FRAME_EXPANDED_HEIGHT = 101
        const val TEXT_BOX_WIDTH = 153
        const val TEXT_BOX_HEIGHT = 46
        const val EXPAND_TOGGLE_SIZE = 5

        private val battleMessagePaneFrameResource = cobblemonResource("textures/gui/battle/battle_log.png")
        private val battleMessagePaneFrameExpandedResource = cobblemonResource("textures/gui/battle/battle_log_expanded.png")
        private val battleMessageHighlight = cobblemonResource("textures/gui/battle/battle_log_row_selected_color.png")
        private var expanded = false
    }

    override fun addEntry(entry: BattleMessageLine): Int {
        return super.addEntry(entry)
    }

    override fun getRowLeft(): Int {
        return super.getRowLeft() + 4
    }

    override fun renderSelection(guiGraphics: GuiGraphics, entry: BattleMessageLine, i: Int) {
        blitk(
            matrixStack = guiGraphics.pose(),
            texture = battleMessageHighlight,
            x = x + 6,
            y = getRowTop(i) - 2,
            height = 10,
            width = 1,
            alpha = opacity
        )

        blitk(
            matrixStack = guiGraphics.pose(),
            texture = battleMessageHighlight,
            x = x + 6,
            y =  getRowTop(i) - 2,
            height = 1,
            width = LINE_WIDTH,
            alpha = opacity
        )

        blitk(
            matrixStack = guiGraphics.pose(),
            texture = battleMessageHighlight,
            x = x + 6,
            y =  getRowTop(i) + 7,
            height = 1,
            width = LINE_WIDTH,
            alpha = opacity
        )

        blitk(
            matrixStack = guiGraphics.pose(),
            texture = battleMessageHighlight,
            x = x + 6 + LINE_WIDTH - 1,
            y =  getRowTop(i) - 2,
            height = 10,
            width = 1,
            alpha = opacity
        )
    }

    override fun getRowWidth(): Int {
        return LINE_WIDTH
    }

    override fun scrollBarX(): Int {
        return this.x + 154
    }

    override fun renderWidget(context: GuiGraphics, mouseX: Int, mouseY: Int, partialTicks: Float) {
        correctSize()
        blitk(
            matrixStack = context.pose(),
            texture = if (expanded) battleMessagePaneFrameExpandedResource else battleMessagePaneFrameResource,
            x = this.x,
            y = appropriateY,
            height = if (expanded) FRAME_EXPANDED_HEIGHT else FRAME_HEIGHT,
            width = FRAME_WIDTH,
            alpha = opacity
        )

        val textBoxHeight = if (expanded) TEXT_BOX_HEIGHT * 2 else TEXT_BOX_HEIGHT
        context.enableScissor(
            this.x + 5,
            appropriateY + 6,
            this.x + 5 + width,
            appropriateY + 6 + textBoxHeight
        )
        super.renderWidget(context, mouseX, mouseY, partialTicks)
        context.disableScissor()
    }

    override fun mouseClicked(mouseButtonEvent: MouseButtonEvent, bl: Boolean): Boolean {
        val toggleOffsetY = if (expanded) 92 else 46
        if (mouseButtonEvent.x > (this.x + 160) &&
            mouseButtonEvent.x < (this.x + 160 + EXPAND_TOGGLE_SIZE) &&
            mouseButtonEvent.y > (appropriateY + toggleOffsetY) &&
            mouseButtonEvent.y < (appropriateY + toggleOffsetY + EXPAND_TOGGLE_SIZE)) {
            expanded = !expanded
        }

        updateScrollingState(mouseButtonEvent.x, mouseButtonEvent.y)
        if (scrolling) {
            focused = getEntryAtPosition(mouseButtonEvent.x, mouseButtonEvent.y)
            isDragging = true
        }

        return super.mouseClicked(mouseButtonEvent, bl)
    }

    override fun mouseDragged(mouseButtonEvent: MouseButtonEvent, deltaY: Double, deltaX: Double): Boolean {
        if (scrolling) {
            if (mouseButtonEvent.y < this.y) {
                scrollAmount = 0.0
            } else if (mouseButtonEvent.y > bottom) {
                scrollAmount = maxScrollAmount().toDouble()
            } else {
                scrollAmount += deltaY
            }
        }
        return super.mouseDragged(mouseButtonEvent, deltaY, deltaX)
    }

    private fun updateScrollingState(mouseX: Double, mouseY: Double) {
        scrolling = mouseX >= this.scrollBarX().toDouble()
                && mouseX < (this.scrollBarY() + 3).toDouble()
                && mouseY >= this.y
                && mouseY < bottom
    }

    class BattleMessageLine(val pane: BattleMessagePane, val line: FormattedCharSequence) : Entry<BattleMessageLine>() {
        override fun getNarration() = "".text()
        override fun renderContent(
            context: GuiGraphics,
            mouseX: Int,
            mouseY: Int,
            isHovered: Boolean,
            partialTicks: Float
        ) {
            drawScaledText(
                context,
                line,
                mouseX, //TODO THIS AINT RIGHT????
                mouseY - 2,
                opacity = pane.opacity
            )
        }
    }
}
