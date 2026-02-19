/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.gui.pokeedit.widgets

import com.cobblemon.mod.common.api.text.text
import com.cobblemon.mod.common.client.gui.ScrollingWidget
import com.cobblemon.mod.common.client.render.drawScaledText
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.network.chat.Component
import net.minecraft.util.Mth
import java.util.Locale

class PokeEditAnimationListWidget(
    val pX: Int,
    val pY: Int,
    val listWidth: Int,
    val listHeight: Int,
    private val isPlaying: (String) -> Boolean,
    private val isLooping: (String) -> Boolean,
    private val getDuration: (String) -> Float,
    private val getProgress: (String) -> Float,
    private val onPlay: (String) -> Unit,
    private val onPause: (String) -> Unit,
    private val onToggleLoop: (String, Boolean) -> Unit
) : ScrollingWidget<PokeEditAnimationListWidget.AnimationRow>(
    left = pX,
    top = pY - listHeight,
    width = listWidth,
    height = listHeight,
    slotHeight = ROW_HEIGHT
) {
    companion object {
        private const val ROW_HEIGHT = 12
        private const val TEXT_SCALE = 0.5F
        private const val BORDER_COLOR = 0xFF1B1B1B.toInt()
        private const val FILL_COLOR = 0xFF3B3B3B.toInt()
        private const val TEXT_COLOR = 0xFFFCFCFC.toInt()
        private const val BUTTON_WIDTH = 12
        private const val BUTTON_HEIGHT = 9
        private const val BUTTON_GAP = 3
        private const val BUTTON_PAD_RIGHT = 4
        private const val BUTTON_PAD_TOP = 1
        private const val BUTTON_ACTIVE = 0xFF6BD46B.toInt()
        private const val BUTTON_INACTIVE = 0xFF5B5B5B.toInt()
        private const val BUTTON_BORDER = 0xFF1B1B1B.toInt()
        private const val LOOP_BOX_SIZE = 8
        private const val LOOP_GAP = 3
        private const val LOOP_LABEL_SCALE = 0.45F
        private const val PROGRESS_HEIGHT = 2
        private const val PROGRESS_BG = 0xFF2C2C2C.toInt()
        private const val PROGRESS_FG = 0xFF8AD5FF.toInt()
        private const val DURATION_SCALE = 0.45F
        private const val DURATION_WIDTH = 28
    }

    private val listTop = pY - listHeight
    private val listBottom = pY
    private var draggingScrollbar = false

    fun setAnimations(animations: Collection<String>) {
        clearEntries()
        animations.sorted().forEach { addEntry(AnimationRow(it)) }
        scrollAmount = 0.0
    }

    override fun renderWidget(context: GuiGraphics, mouseX: Int, mouseY: Int, delta: Float) {
        context.fill(pX - 1, listTop - 1, pX + listWidth + 1, listBottom + 1, BORDER_COLOR)
        context.fill(pX, listTop, pX + listWidth, listBottom, FILL_COLOR)

        focused = if (mouseX in pX..(pX + listWidth) && mouseY in listTop..listBottom) {
            getEntryAtPosition(mouseX.toDouble(), mouseY.toDouble())
        } else {
            null
        }

        context.enableScissor(pX, listTop, pX + listWidth, listBottom)
        renderListItems(context, mouseX, mouseY, delta)
        context.disableScissor()

        if (maxScroll > 0) {
            renderScrollbar(context, mouseX, mouseY, delta)
        }
    }

    override fun renderListItems(context: GuiGraphics, mouseX: Int, mouseY: Int, delta: Float) {
        val rowX = rowLeft
        for (index in 0 until itemCount) {
            val rowY = getRowTop(index)
            val rowBottom = getRowBottom(index)
            if (rowBottom >= listTop && rowY <= listBottom) {
                val entry = getEntry(index)
                if (entry is AnimationRow) {
                    entry.isPlaying = isPlaying(entry.name)
                    entry.isLooping = isLooping(entry.name)
                    entry.durationSeconds = getDuration(entry.name)
                    entry.progress = getProgress(entry.name)
                }
                renderItem(context, mouseX, mouseY, delta, index, rowX, rowY, rowWidth, itemHeight)
            }
        }
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, amount: Double, verticalAmount: Double): Boolean {
        if (mouseX !in pX.toDouble()..(pX + listWidth).toDouble()
            || mouseY !in listTop.toDouble()..listBottom.toDouble()
        ) {
            return false
        }
        val maxScroll = (itemCount * itemHeight - listHeight).coerceAtLeast(0)
        if (maxScroll <= 0) return true
        val step = if (verticalAmount != 0.0) verticalAmount else amount
        scrollAmount = Mth.clamp(scrollAmount - step * itemHeight, 0.0, maxScroll.toDouble())
        return true
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (mouseX !in pX.toDouble()..(pX + listWidth).toDouble()
            || mouseY !in listTop.toDouble()..listBottom.toDouble()
            || button != 0
        ) {
            return false
        }
        if (isOverScrollbar(mouseX, mouseY) && maxScroll > 0) {
            draggingScrollbar = true
            return true
        }

        val localY = mouseY.toInt() - listTop + scrollAmount.toInt()
        if (localY < 0) return false
        val index = localY / itemHeight
        if (index !in 0 until itemCount) return false
        val entry = getEntry(index)
        if (entry is AnimationRow) {
            val rowX = rowLeft
            val rowY = getRowTop(index)
            val (playX, pauseX, loopX, buttonY, durationX) = buttonPositions(rowX, rowY)
            val inPlay = mouseX.toInt() in playX..(playX + BUTTON_WIDTH)
                && mouseY.toInt() in buttonY..(buttonY + BUTTON_HEIGHT)
            val inPause = mouseX.toInt() in pauseX..(pauseX + BUTTON_WIDTH)
                && mouseY.toInt() in buttonY..(buttonY + BUTTON_HEIGHT)
            val inLoop = mouseX.toInt() in loopX..(loopX + LOOP_BOX_SIZE)
                && mouseY.toInt() in buttonY..(buttonY + LOOP_BOX_SIZE)
            if (inPlay) {
                onPlay(entry.name)
                return true
            }
            if (inPause) {
                onPause(entry.name)
                return true
            }
            if (inLoop) {
                val newValue = !isLooping(entry.name)
                onToggleLoop(entry.name, newValue)
                return true
            }
        }
        return false
    }

    override fun mouseReleased(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (button == 0 && draggingScrollbar) {
            draggingScrollbar = false
            return true
        }
        return super.mouseReleased(mouseX, mouseY, button)
    }

    override fun mouseDragged(mouseX: Double, mouseY: Double, button: Int, deltaX: Double, deltaY: Double): Boolean {
        if (!draggingScrollbar || maxScroll <= 0) {
            return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY)
        }
        val barHeight = listBottom - listTop
        var thumbHeight = ((barHeight * barHeight).toFloat() / maxPosition.toFloat()).toInt()
        thumbHeight = Mth.clamp(thumbHeight, 16, barHeight - 4)
        val trackHeight = barHeight - thumbHeight
        if (trackHeight <= 0) return true
        val clampedY = Mth.clamp(mouseY.toInt() - listTop - thumbHeight / 2, 0, trackHeight)
        scrollAmount = (clampedY.toDouble() * maxScroll.toDouble()) / trackHeight.toDouble()
        return true
    }

    override fun isMouseOver(mouseX: Double, mouseY: Double): Boolean {
        return mouseX in pX.toDouble()..(pX + listWidth).toDouble()
            && mouseY in listTop.toDouble()..listBottom.toDouble()
    }

    override fun renderScrollbar(context: GuiGraphics, mouseX: Int, mouseY: Int, delta: Float) {
        val xLeft = this.scrollbarPosition
        val xRight = xLeft + 3
        val yStart = listTop
        val barHeight = listBottom - listTop

        var yBottom = ((barHeight * barHeight).toFloat() / this.maxPosition.toFloat()).toInt()
        yBottom = Mth.clamp(yBottom, 16, barHeight - 4)
        var yTop = scrollAmount.toInt() * (barHeight - yBottom) / this.maxScroll + yStart
        if (yTop < yStart) yTop = yStart

        context.fill(xLeft + 1, yStart, xRight - 1, listBottom, 0xFF7AEEF1.toInt())
        context.fill(xLeft, yTop, xRight, yTop + yBottom, 0xFF3A96B6.toInt())
    }

    override fun getRowTop(index: Int): Int {
        return listTop - scrollAmount.toInt() + (index * itemHeight)
    }

    override fun getRowBottom(index: Int): Int {
        return getRowTop(index) + itemHeight
    }

    override fun getBottom(): Int {
        return listBottom
    }

    private fun isOverScrollbar(mouseX: Double, mouseY: Double): Boolean {
        val xLeft = scrollbarPosition
        val xRight = xLeft + 3
        return mouseX >= xLeft && mouseX <= xRight && mouseY >= listTop && mouseY <= listBottom
    }

    private fun buttonPositions(rowX: Int, rowY: Int): List<Int> {
        val loopX = rowX + listWidth - BUTTON_PAD_RIGHT - LOOP_BOX_SIZE
        val pauseX = loopX - LOOP_GAP - BUTTON_WIDTH
        val playX = pauseX - BUTTON_GAP - BUTTON_WIDTH
        val buttonY = rowY + BUTTON_PAD_TOP
        val durationX = playX - BUTTON_GAP - DURATION_WIDTH
        return listOf(playX, pauseX, loopX, buttonY, durationX)
    }

    class AnimationRow(val name: String) : Slot<AnimationRow>() {
        var isPlaying: Boolean = false
        var isLooping: Boolean = false
        var durationSeconds: Float = 0F
        var progress: Float = 0F

        override fun render(
            context: GuiGraphics,
            index: Int,
            y: Int,
            x: Int,
            entryWidth: Int,
            entryHeight: Int,
            mouseX: Int,
            mouseY: Int,
            hovered: Boolean,
            tickDelta: Float
        ) {
            val bg = if (hovered) 0xFF2F2F2F.toInt() else null
            if (bg != null) {
                context.fill(x + 1, y, x + entryWidth - 1, y + ROW_HEIGHT, bg)
            }

            val loopX = x + entryWidth - BUTTON_PAD_RIGHT - LOOP_BOX_SIZE
            val pauseX = loopX - LOOP_GAP - BUTTON_WIDTH
            val playX = pauseX - BUTTON_GAP - BUTTON_WIDTH
            val durationX = playX - BUTTON_GAP - DURATION_WIDTH
            val buttonY = y + BUTTON_PAD_TOP
            drawButton(context, playX, buttonY, BUTTON_WIDTH, BUTTON_HEIGHT, !isPlaying)
            drawPlayIcon(context, playX, buttonY, BUTTON_WIDTH, BUTTON_HEIGHT)
            drawButton(context, pauseX, buttonY, BUTTON_WIDTH, BUTTON_HEIGHT, isPlaying)
            drawPauseIcon(context, pauseX, buttonY, BUTTON_WIDTH, BUTTON_HEIGHT)
            drawLoopBox(context, loopX, buttonY, isLooping)
            drawDuration(context, durationX, buttonY, durationSeconds)

            drawScaledText(
                context = context,
                text = name.text(),
                x = x + 3,
                y = y + 2,
                scale = TEXT_SCALE,
                colour = TEXT_COLOR,
                shadow = false
            )

            drawProgressBar(context, x + 2, y + ROW_HEIGHT - PROGRESS_HEIGHT, entryWidth - 4, progress)
        }

        override fun getNarration(): Component {
            return Component.literal(name)
        }

        private fun drawButton(
            context: GuiGraphics,
            x: Int,
            y: Int,
            width: Int,
            height: Int,
            active: Boolean
        ) {
            val fill = if (active) BUTTON_ACTIVE else BUTTON_INACTIVE
            context.fill(x - 1, y - 1, x + width + 1, y + height + 1, BUTTON_BORDER)
            context.fill(x, y, x + width, y + height, fill)
        }

        private fun drawLoopBox(context: GuiGraphics, x: Int, y: Int, enabled: Boolean) {
            val fill = if (enabled) BUTTON_ACTIVE else BUTTON_INACTIVE
            context.fill(x - 1, y - 1, x + LOOP_BOX_SIZE + 1, y + LOOP_BOX_SIZE + 1, BUTTON_BORDER)
            context.fill(x, y, x + LOOP_BOX_SIZE, y + LOOP_BOX_SIZE, fill)
            drawLoopIcon(context, x, y, LOOP_BOX_SIZE)
        }

        private fun drawPlayIcon(context: GuiGraphics, x: Int, y: Int, width: Int, height: Int) {
            val iconWidth = 6
            val iconHeight = 7
            val startX = x + (width - iconWidth) / 2
            val startY = y + (height - iconHeight) / 2
            for (i in 0 until iconWidth) {
                val top = startY + i / 2
                val bottom = startY + iconHeight - i / 2
                context.fill(startX + i, top, startX + i + 1, bottom, TEXT_COLOR)
            }
        }

        private fun drawPauseIcon(context: GuiGraphics, x: Int, y: Int, width: Int, height: Int) {
            val barWidth = 2
            val barHeight = 7
            val gap = 2
            val startX = x + (width - (barWidth * 2 + gap)) / 2
            val startY = y + (height - barHeight) / 2
            context.fill(startX, startY, startX + barWidth, startY + barHeight, TEXT_COLOR)
            context.fill(startX + barWidth + gap, startY, startX + barWidth * 2 + gap, startY + barHeight, TEXT_COLOR)
        }

        private fun drawLoopIcon(context: GuiGraphics, x: Int, y: Int, size: Int) {
            val startX = x + 1
            val startY = y + 1
            val endX = x + size - 1
            val endY = y + size - 1
            context.fill(startX, startY, endX, startY + 1, TEXT_COLOR)
            context.fill(startX, endY - 1, endX, endY, TEXT_COLOR)
            context.fill(startX, startY, startX + 1, endY, TEXT_COLOR)
            context.fill(endX - 1, startY, endX, endY, TEXT_COLOR)
            context.fill(endX - 2, startY, endX, startY + 2, TEXT_COLOR)
        }

        private fun drawDuration(context: GuiGraphics, x: Int, y: Int, duration: Float) {
            val text = String.format(Locale.US, "%.2fs", duration.coerceAtLeast(0F))
            drawScaledText(
                context = context,
                text = text.text(),
                x = x + DURATION_WIDTH / 2,
                y = y + 2,
                scale = DURATION_SCALE,
                colour = TEXT_COLOR,
                maxCharacterWidth = DURATION_WIDTH,
                centered = true,
                shadow = false
            )
        }

        private fun drawProgressBar(context: GuiGraphics, x: Int, y: Int, width: Int, progress: Float) {
            val clamped = Mth.clamp(progress, 0F, 1F)
            context.fill(x, y, x + width, y + PROGRESS_HEIGHT, PROGRESS_BG)
            context.fill(x, y, x + (width * clamped).toInt(), y + PROGRESS_HEIGHT, PROGRESS_FG)
        }
    }
}
