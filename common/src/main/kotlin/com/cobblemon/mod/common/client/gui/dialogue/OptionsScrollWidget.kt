/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.gui.dialogue

import com.cobblemon.mod.common.client.gui.dialogue.widgets.DialogueOptionWidget
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.client.gui.narration.NarrationElementOutput
import net.minecraft.network.chat.Component
import net.minecraft.util.Mth

class OptionsScrollWidget(
    dialogueScreen: DialogueScreen,
    x: Int,
    y: Int,
    width: Int,
    height: Int,
    val entries: List<DialogueOptionWidget>,
    val vertical: Boolean,
    val containerSize: Int
) : AbstractWidget(x, y, width, height, Component.empty()) {

    private var scrollOffset = 0

    private val horizontalInset = DialogueScreen.BUTTON_EDGE_PADDING
    private val verticalInset = DialogueScreen.BUTTON_EDGE_PADDING

    private val contentAreaWidth
        get() = if (vertical) width - containerSize - horizontalInset * 2 else width - horizontalInset * 2
    private val contentAreaHeight
        get() = if (vertical) height - verticalInset * 2 else height - containerSize - verticalInset * 2

    private val slotSize =
        if (vertical) DialogueScreen.OPTION_HEIGHT + DialogueScreen.OPTION_VERTICAL_SPACING else DialogueScreen.OPTION_WIDTH_NARROW + DialogueScreen.OPTION_HORIZONTAL_SPACING
    private val totalSize = kotlin.math.max(
        0,
        entries.size * slotSize - (if (vertical) DialogueScreen.OPTION_VERTICAL_SPACING else DialogueScreen.OPTION_HORIZONTAL_SPACING)
    )
    private val maxScroll = kotlin.math.max(0, totalSize - (if (vertical) contentAreaHeight else contentAreaWidth))

    // Drag state for the scrollbar
    private var dragging = false
    private var dragAnchor = 0
    private var dragStartScroll = 0

    // Inclusive visible index range (covers partially-visible items)
    private fun visibleRange(): IntRange {
        val start = (scrollOffset / slotSize).coerceAtLeast(0)
        val visiblePixels = if (vertical) contentAreaHeight else contentAreaWidth
        val end = ((scrollOffset + visiblePixels + slotSize - 1) / slotSize).coerceAtMost(entries.size - 1)
        return start..end
    }

    override fun renderWidget(context: GuiGraphics, mouseX: Int, mouseY: Int, partialTicks: Float) {
        // Ensure scrollOffset never exceeds maxScroll (prevents top being cut off after resize/changes)
        scrollOffset = scrollOffset.coerceIn(0, maxScroll)

        // Enable scissor to clip children
        context.enableScissor(x, y, x + width, y + height)
        try {
            val range = visibleRange()

            val contentLeft = x + horizontalInset
            val contentTop = y + verticalInset

            for (i in range) {
                val entry = entries[i]
                val posIndex = i

                val px = if (vertical) {
                    x + horizontalInset + (contentAreaWidth - entry.width) / 2
                } else {
                    x + horizontalInset + (posIndex * slotSize) - scrollOffset
                }

                val py = if (vertical) {
                    y + verticalInset + (posIndex * slotSize) - scrollOffset
                } else {
                    y + verticalInset + (contentAreaHeight - entry.height) / 2
                }

                val prevX = entry.x
                val prevY = entry.y
                entry.x = px
                entry.y = py
                entry.render(context, mouseX, mouseY, partialTicks)
                entry.x = prevX
                entry.y = prevY
            }

            if (maxScroll > 0) {
                if (vertical) {
                    val containerX = x + horizontalInset + contentAreaWidth + DialogueScreen.SCROLL_INSET
                    val containerY = y + DialogueScreen.SCROLL_INSET
                    val containerW = kotlin.math.max(8, containerSize - DialogueScreen.SCROLL_INSET * 2)
                    val containerH = kotlin.math.max(16, height - DialogueScreen.SCROLL_INSET * 2)


                    // Scroll container background
                    context.fill(
                        containerX,
                        containerY + 2,
                        containerX + containerW,
                        containerY + containerH - 2,
                        0xFFDDDDDD.toInt()
                    )
                    context.fill(
                        containerX + 1,
                        containerY + 3,
                        containerX + containerW - 1,
                        containerY + containerH - 3,
                        0xFFEEEEEE.toInt()
                    )
                    context.fill(
                        containerX,
                        containerY + 2,
                        containerX + 1,
                        containerY + containerH - 2,
                        0xFF888888.toInt()
                    )

                    val trackX = containerX + (containerW - DialogueScreen.SCROLL_TRACK_THICKNESS) / 2
                    val trackY = containerY + DialogueScreen.SCROLL_TRACK_PADDING
                    val trackH = (containerH - DialogueScreen.SCROLL_TRACK_PADDING * 2).coerceAtLeast(8)

                    var thumbSize = (trackH.toFloat() * trackH.toFloat() / totalSize.toFloat()).toInt()
                    thumbSize = Mth.clamp(thumbSize, 8, trackH - 4)
                    val thumbPos = (scrollOffset.toFloat() / maxScroll.toFloat() * (trackH - thumbSize)).toInt() + trackY

                    context.fill(
                        trackX + 3,
                        trackY,
                        trackX + DialogueScreen.SCROLL_TRACK_THICKNESS - 2,
                        trackY + trackH,
                        0xFFCCCCCC.toInt()
                    )
                    context.fill(
                        trackX,
                        thumbPos,
                        trackX + DialogueScreen.SCROLL_TRACK_THICKNESS + 1,
                        thumbPos + thumbSize,
                        0xFF888888.toInt()
                    )
                } else {
                    val containerX = x + DialogueScreen.SCROLL_INSET
                    val containerY = y + verticalInset + contentAreaHeight + DialogueScreen.SCROLL_INSET
                    val containerW = kotlin.math.max(16, width - DialogueScreen.SCROLL_INSET * 2)
                    val containerH = kotlin.math.max(8, containerSize - DialogueScreen.SCROLL_INSET * 2)

                    // Scroll container background
                    context.fill(
                        containerX + 2,
                        containerY,
                        containerX + containerW - 2,
                        containerY + containerH,
                        0xFFDDDDDD.toInt()
                    )
                    context.fill(
                        containerX + 3,
                        containerY + 1,
                        containerX + containerW - 3,
                        containerY + containerH - 1,
                        0xFFEEEEEE.toInt()
                    )
                    context.fill(
                        containerX + 2,
                        containerY,
                        containerX + containerW - 2,
                        containerY + 1,
                        0xFFA0A0A0.toInt()
                    )

                    val trackX = containerX + DialogueScreen.SCROLL_TRACK_PADDING
                    val trackW = (containerW - DialogueScreen.SCROLL_TRACK_PADDING * 2).coerceAtLeast(16)
                    val trackY = containerY + (containerH - DialogueScreen.SCROLL_TRACK_THICKNESS) / 2

                    var thumbSize = (trackW.toFloat() * trackW.toFloat() / totalSize.toFloat()).toInt()
                    thumbSize = Mth.clamp(thumbSize, 8, trackW - 4)
                    val thumbPos = (scrollOffset.toFloat() / maxScroll.toFloat() * (trackW - thumbSize)).toInt() + trackX

                    context.fill(
                        trackX,
                        trackY + 3,
                        trackX + trackW,
                        trackY + DialogueScreen.SCROLL_TRACK_THICKNESS - 2,
                        0xFFCCCCCC.toInt()
                    )
                    context.fill(
                        thumbPos,
                        trackY,
                        thumbPos + thumbSize,
                        trackY + DialogueScreen.SCROLL_TRACK_THICKNESS + 1,
                        0xFFA0A0A0.toInt()
                    )
                }
            }
        } finally {
            context.disableScissor()
        }
    }

    override fun updateWidgetNarration(narrationElementOutput: NarrationElementOutput) {
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, scrollX: Double, scrollY: Double): Boolean {
        if (mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height) {
            scrollOffset = Mth.clamp(scrollOffset - (scrollY * slotSize).toInt(), 0, maxScroll)
            return true
        }
        return false
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height) {
            val range = visibleRange()

            val contentLeft = x + horizontalInset
            val contentTop = y + verticalInset
            val contentRight = contentLeft + contentAreaWidth
            val contentBottom = contentTop + contentAreaHeight

            for (i in range) {
                val entry = entries[i]
                val posIndex = i
                val px = if (vertical) {
                    x + horizontalInset + (contentAreaWidth - entry.width) / 2
                } else {
                    x + horizontalInset + (posIndex * slotSize) - scrollOffset
                }
                val py = if (vertical) {
                    y + verticalInset + (posIndex * slotSize) - scrollOffset
                } else {
                    y + verticalInset + (contentAreaHeight - entry.height) / 2
                }

                // require click to be within both the entry rect and the visible content area
                if (mouseX >= px && mouseX <= px + entry.width && mouseY >= py && mouseY <= py + entry.height
                    && mouseX >= contentLeft && mouseX <= contentRight && mouseY >= contentTop && mouseY <= contentBottom
                ) {
                    val prevX = entry.x
                    val prevY = entry.y
                    entry.x = px
                    entry.y = py
                    val consumed = entry.mouseClicked(mouseX, mouseY, button)
                    entry.x = prevX
                    entry.y = prevY
                    if (consumed) return true
                }
            }

            // If not clicking an entry, check scrollbar container/track and handle click/drag/jump
            if (maxScroll > 0) {
                if (vertical) {
                    val containerX = x + horizontalInset + contentAreaWidth + DialogueScreen.SCROLL_INSET
                    val containerY = y + DialogueScreen.SCROLL_INSET
                    val containerW = kotlin.math.max(8, containerSize - DialogueScreen.SCROLL_INSET * 2)
                    val containerH = kotlin.math.max(16, height - DialogueScreen.SCROLL_INSET * 2)

                    // if clicked within the container area (the 0xFFEEEEEE / light fill), map to scroll
                    if (mouseX >= containerX && mouseX <= containerX + containerW && mouseY >= containerY && mouseY <= containerY + containerH) {
                        val trackY = containerY + DialogueScreen.SCROLL_TRACK_PADDING
                        val trackH = (containerH - DialogueScreen.SCROLL_TRACK_PADDING * 2).coerceAtLeast(8)

                        var thumbSize = (trackH.toFloat() * trackH.toFloat() / totalSize.toFloat()).toInt()
                        thumbSize = Mth.clamp(thumbSize, 8, trackH - 4)

                        // jump so thumb centers under the click, then start dragging
                        val relative = (mouseY.toInt() - trackY)
                        val trackRange = (trackH - thumbSize).coerceAtLeast(1)
                        val target = ((relative - thumbSize / 2f) / trackRange * maxScroll).toInt()
                        scrollOffset = Mth.clamp(target, 0, maxScroll)

                        // start dragging
                        dragging = true
                        dragAnchor = mouseY.toInt()
                        dragStartScroll = scrollOffset
                        return true
                    }
                } else {
                    val containerX = x + DialogueScreen.SCROLL_INSET
                    val containerY = y + verticalInset + contentAreaHeight + DialogueScreen.SCROLL_INSET
                    val containerW = kotlin.math.max(16, width - DialogueScreen.SCROLL_INSET * 2)
                    val containerH = kotlin.math.max(8, containerSize - DialogueScreen.SCROLL_INSET * 2)

                    if (mouseX >= containerX && mouseX <= containerX + containerW && mouseY >= containerY && mouseY <= containerY + containerH) {
                        val trackX = containerX + DialogueScreen.SCROLL_TRACK_PADDING
                        val trackW = (containerW - DialogueScreen.SCROLL_TRACK_PADDING * 2).coerceAtLeast(16)

                        var thumbSize = (trackW.toFloat() * trackW.toFloat() / totalSize.toFloat()).toInt()
                        thumbSize = Mth.clamp(thumbSize, 8, trackW - 4)

                        val relative = (mouseX.toInt() - trackX)
                        val trackRange = (trackW - thumbSize).coerceAtLeast(1)
                        val target = ((relative - thumbSize / 2f) / trackRange * maxScroll).toInt()
                        scrollOffset = Mth.clamp(target, 0, maxScroll)

                        // start dragging
                        dragging = true
                        dragAnchor = mouseX.toInt()
                        dragStartScroll = scrollOffset
                        return true
                    }
                }
            }

            return true
        }
        return false
    }

    override fun mouseDragged(
        mouseX: Double,
        mouseY: Double,
        button: Int,
        deltaX: Double,
        deltaY: Double
    ): Boolean {
        if (!dragging) return false

        if (vertical) {
            val containerX = x + horizontalInset + contentAreaWidth + DialogueScreen.SCROLL_INSET
            val containerY = y + DialogueScreen.SCROLL_INSET
            val containerW = kotlin.math.max(8, containerSize - DialogueScreen.SCROLL_INSET * 2)
            val containerH = kotlin.math.max(16, height - DialogueScreen.SCROLL_INSET * 2)
            val trackY = containerY + DialogueScreen.SCROLL_TRACK_PADDING
            val trackH = (containerH - DialogueScreen.SCROLL_TRACK_PADDING * 2).coerceAtLeast(8)

            var thumbSize = (trackH.toFloat() * trackH.toFloat() / totalSize.toFloat()).toInt()
            thumbSize = Mth.clamp(thumbSize, 8, trackH - 4)
            val trackRange = (trackH - thumbSize).coerceAtLeast(1)

            val delta = (mouseY.toInt() - dragAnchor)
            if (trackRange > 0) {
                val deltaScroll = (delta.toFloat() / trackRange * maxScroll).toInt()
                scrollOffset = Mth.clamp(dragStartScroll + deltaScroll, 0, maxScroll)
            }
        } else {
            val containerX = x + DialogueScreen.SCROLL_INSET
            val containerY = y + verticalInset + contentAreaHeight + DialogueScreen.SCROLL_INSET
            val containerW = kotlin.math.max(16, width - DialogueScreen.SCROLL_INSET * 2)
            val containerH = kotlin.math.max(8, containerSize - DialogueScreen.SCROLL_INSET * 2)
            val trackX = containerX + DialogueScreen.SCROLL_TRACK_PADDING
            val trackW = (containerW - DialogueScreen.SCROLL_TRACK_PADDING * 2).coerceAtLeast(16)

            var thumbSize = (trackW.toFloat() * trackW.toFloat() / totalSize.toFloat()).toInt()
            thumbSize = Mth.clamp(thumbSize, 8, trackW - 4)
            val trackRange = (trackW - thumbSize).coerceAtLeast(1)

            val delta = (mouseX.toInt() - dragAnchor)
            if (trackRange > 0) {
                val deltaScroll = (delta.toFloat() / trackRange * maxScroll).toInt()
                scrollOffset = Mth.clamp(dragStartScroll + deltaScroll, 0, maxScroll)
            }
        }

        return true
    }

    override fun mouseReleased(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (dragging) {
            dragging = false
            return true
        }
        return false
    }
}

