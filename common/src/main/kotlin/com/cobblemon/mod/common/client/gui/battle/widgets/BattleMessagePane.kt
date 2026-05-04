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
import com.cobblemon.mod.common.client.CobblemonResources
import com.cobblemon.mod.common.client.battle.ClientBattleMessageQueue
import com.cobblemon.mod.common.client.gui.CobblemonRenderable
import com.cobblemon.mod.common.client.gui.battle.subscreen.BattleTeamInfoSelection
import com.cobblemon.mod.common.client.render.drawScaledText
import com.cobblemon.mod.common.util.cobblemonResource
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.ObjectSelectionList
import net.minecraft.locale.Language
import net.minecraft.network.chat.Component
import net.minecraft.util.FormattedCharSequence
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round

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

    val battleMessages = mutableListOf<Component>()

    init {
        correctSize()
        //setRenderBackground(false)

        scrollAmount = maxScroll.toDouble()
        messageQueue.subscribe {
            battleMessages.add(it)
            correctBattleText()
        }
    }

    private fun correctBattleText() {
        val isFullyScrolled = maxScroll - scrollAmount < 10
        clearEntries()
        val textRenderer = Minecraft.getInstance().font
        for (message in battleMessages) {
            val line = message.copy().setStyle(message.style.withBold(true).withFont(CobblemonResources.DEFAULT_LARGE))
            val wrappedLines = textRenderer.splitter.splitLines(line, battleLogWidth - 27, line.style)
            val lines = Language.getInstance().getVisualOrder(wrappedLines)
            for (finalLine in lines) {
                addEntry(BattleMessageLine(this, finalLine))
            }
        }
        if (isFullyScrolled) {
            scrollAmount = maxScroll.toDouble()
        }
    }

    private fun correctSize() {
        setRectangle(battleLogWidth - 11, battleLogHeight - 13, battleLogX.toInt(), battleLogY.toInt() + 8)
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

        private val battleMessageHighlight = cobblemonResource("textures/gui/battle/battle_log_row_selected_color.png")

        val topTexture = cobblemonResource("textures/gui/battle/log/top.png")
        val bottomTexture = cobblemonResource("textures/gui/battle/log/bottom.png")
        val leftTexture = cobblemonResource("textures/gui/battle/log/left.png")
        val rightTexture = cobblemonResource("textures/gui/battle/log/right.png")
        val topLeftTexture = cobblemonResource("textures/gui/battle/log/top_left.png")
        val topRightTexture = cobblemonResource("textures/gui/battle/log/top_right.png")
        val bottomLeftTexture = cobblemonResource("textures/gui/battle/log/bottom_left.png")
        val bottomRightTexture = cobblemonResource("textures/gui/battle/log/bottom_right.png")
        val centerTexture = cobblemonResource("textures/gui/battle/log/center.png")

        private var expanded = false

        var battleLogWidth: Int = 153
            set(value) {
                val max = Minecraft.getInstance().window.guiScaledWidth - 20
                if (battleLogX + value <= max) {
                    field = value
                }
            }

        var battleLogHeight: Int = 46
            set(value) {
                val max = Minecraft.getInstance().window.guiScaledHeight - 20
                if (battleLogY + value <= max) {
                    field = value
                }
            }

        var battleLogX: Double = defaultLogX
            get() {
                val max = Minecraft.getInstance().window.guiScaledWidth - battleLogWidth
                return max(min(field, max.toDouble() - 20), 4.0)
            }

        var battleLogY: Double = defaultLogY
            get() {
                val max = Minecraft.getInstance().window.guiScaledHeight - battleLogHeight
                return max(min(field, max.toDouble() - 20), 4.0)
            }

        val defaultLogX: Double
            get() = Minecraft.getInstance().window.guiScaledWidth - 181.0
        val defaultLogY: Double
            get() = Minecraft.getInstance().window.guiScaledHeight - 85.0
    }

    override fun addEntry(entry: BattleMessageLine): Int {
        return super.addEntry(entry)
    }

    override fun getRowLeft(): Int {
        return x + 10
    }

    override fun renderSelection(guiGraphics: GuiGraphics, top: Int, width: Int, height: Int, outerColor: Int, innerColor: Int) {
        blitk(
            matrixStack = guiGraphics.pose(),
            texture = battleMessageHighlight,
            x = x + 6,
            y = top - 2,
            height = 10,
            width = 1,
            alpha = opacity
        )

        blitk(
            matrixStack = guiGraphics.pose(),
            texture = battleMessageHighlight,
            x = x + 6,
            y = top - 2,
            height = 1,
            width = battleLogWidth - 18,
            alpha = opacity
        )

        blitk(
            matrixStack = guiGraphics.pose(),
            texture = battleMessageHighlight,
            x = x + 6,
            y = top + 7,
            height = 1,
            width = battleLogWidth - 18,
            alpha = opacity
        )

        blitk(
            matrixStack = guiGraphics.pose(),
            texture = battleMessageHighlight,
            x = x + 6 + battleLogWidth - 19,
            y = top - 2,
            height = 10,
            width = 1,
            alpha = opacity
        )
    }

    override fun getRowWidth(): Int {
        return battleLogWidth - 16
    }

    override fun getScrollbarPosition(): Int {
        return this.x + battleLogWidth - 16
    }

    override fun renderWidget(context: GuiGraphics, mouseX: Int, mouseY: Int, partialTicks: Float) {
        if (BattleTeamInfoSelection.visible) return
        correctSize()

        val isFullyScrolled = opacity != 1f || maxScroll - scrollAmount < 2
        if (isFullyScrolled) { scrollAmount = maxScroll.toDouble() }

        blitk(
            matrixStack = context.pose(),
            texture = topLeftTexture,
            x = battleLogX,
            y = battleLogY,
            height = 8,
            width = 7,
            textureHeight = 8,
            textureWidth = 7,
            alpha = opacity
        )

        blitk(
            matrixStack = context.pose(),
            texture = topRightTexture,
            x = battleLogX + battleLogWidth - 13,
            y = battleLogY,
            height = 8,
            width = 13,
            textureHeight = 8,
            textureWidth = 13,
            alpha = opacity
        )

        blitk(
            matrixStack = context.pose(),
            texture = bottomLeftTexture,
            x = battleLogX,
            y = battleLogY + battleLogHeight - 5,
            height = 5,
            width = 7,
            textureHeight = 5,
            textureWidth = 7,
            alpha = opacity
        )

        blitk(
            matrixStack = context.pose(),
            texture = bottomRightTexture,
            x = battleLogX + battleLogWidth - 13,
            y = battleLogY + battleLogHeight - 10,
            height = 10,
            width = 13,
            textureHeight = 10,
            textureWidth = 13,
            alpha = opacity
        )

        blitk(
            matrixStack = context.pose(),
            texture = topTexture,
            x = battleLogX + 7,
            y = battleLogY,
            height = 7,
            width = battleLogWidth - 7 - 13,
            textureHeight = 7,
            textureWidth = 1,
            alpha = opacity
        )

        blitk(
            matrixStack = context.pose(),
            texture = bottomTexture,
            x = battleLogX + 7,
            y = battleLogY + battleLogHeight - 4,
            height = 4,
            width = battleLogWidth - 7 - 12,
            textureHeight = 4,
            textureWidth = 1,
            alpha = opacity
        )

        blitk(
            matrixStack = context.pose(),
            texture = leftTexture,
            x = battleLogX,
            y = battleLogY + 8,
            height = battleLogHeight - 8 - 5,
            width = 7,
            textureHeight = 1,
            textureWidth = 7,
            alpha = opacity
        )

        blitk(
            matrixStack = context.pose(),
            texture = rightTexture,
            x = battleLogX + battleLogWidth - 13,
            y = battleLogY + 8,
            height = battleLogHeight - 18,
            width = 13,
            textureHeight = 1,
            textureWidth = 13,
            alpha = opacity
        )

        blitk(
            matrixStack = context.pose(),
            texture = centerTexture,
            x = battleLogX + 7,
            y = battleLogY + 7,
            height = battleLogHeight - 11,
            width = battleLogWidth - 20,
            textureHeight = battleLogHeight - 11,
            textureWidth = battleLogWidth - 20,
            alpha = opacity
        )

        context.enableScissor(
            x + 5,
            round(battleLogY + 6).toInt(),
            x + 6 + battleLogWidth,
            round(battleLogY + 6 + battleLogHeight).toInt()
        )
        super.renderWidget(context, mouseX, mouseY, partialTicks)
        context.disableScissor()
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        updateScrollingState(mouseX, mouseY)
        if (scrolling) {
            focused = getEntryAtPosition(mouseX, mouseY)
            isDragging = true
        }
        return super.mouseClicked(mouseX, mouseY, button)
    }

    override fun mouseDragged(mouseX: Double, mouseY: Double, button: Int, deltaX: Double, deltaY: Double): Boolean {
        if (scrolling) {
            if (mouseY < this.y) {
                scrollAmount = 0.0
            } else if (mouseY > bottom) {
                scrollAmount = maxScroll.toDouble()
            } else {
                scrollAmount += deltaY
            }
        }
        if (!tryMove(mouseX, mouseY, deltaX, deltaY)) {
            tryAdjustWidth(mouseX, mouseY, button, deltaX, deltaY)
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY)
    }

    private fun tryMove(mouseX: Double, mouseY: Double, deltaX: Double, deltaY: Double): Boolean {
        if (mouseY - deltaY < y - 5 || mouseY - deltaY > y + 5) return false
        if (mouseX - deltaX < x - 5 || mouseX - deltaX > (x + width + 5)) return false
        battleLogX = battleLogX + deltaX
        battleLogY = battleLogY + deltaY
        return true
    }

    private fun tryAdjustWidth(mouseX: Double, mouseY: Double, button: Int, deltaX: Double, deltaY: Double) {
        if (button == 1) return
        val frameWidth = battleLogWidth + 16
        val frameHeight = battleLogHeight + 9
        val expandButtonX1 = frameWidth - 9
        val expandButtonX2 = frameWidth - 4
        val expandButtonY1 = frameHeight - 9
        val expandButtonY2 = frameHeight - 4
        if (mouseX - deltaX < x + expandButtonX1 - 15 || mouseX - deltaX > x + expandButtonX2 + 15) return
        if (mouseY - deltaY < y + expandButtonY1 - 15 || mouseY - deltaY > y + expandButtonY2 + 15) return
        val newHeight = max(mouseY.toInt() + 7 - y, TEXT_BOX_HEIGHT)
        val newWidth = max(mouseX.toInt() - x, TEXT_BOX_WIDTH)
        battleLogHeight = newHeight
        battleLogWidth = newWidth
        correctSize()
        correctBattleText()
    }

    private fun updateScrollingState(mouseX: Double, mouseY: Double) {
        scrolling = mouseX >= this.scrollbarPosition.toDouble()
                && mouseX < (this.scrollbarPosition + 3).toDouble()
                && mouseY >= this.y
                && mouseY < bottom
    }

    class BattleMessageLine(val pane: BattleMessagePane, val line: FormattedCharSequence) : Entry<BattleMessageLine>() {
        override fun getNarration() = "".text()
        override fun render(
            context: GuiGraphics,
            index: Int,
            rowTop: Int,
            rowLeft: Int,
            rowWidth: Int,
            rowHeight: Int,
            mouseX: Int,
            mouseY: Int,
            isHovered: Boolean,
            partialTicks: Float
        ) {
            drawScaledText(
                context,
                line,
                rowLeft,
                rowTop - 2,
                opacity = pane.opacity
            )
        }

        override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
            return super.mouseClicked(mouseX, mouseY, button)
        }
    }
}