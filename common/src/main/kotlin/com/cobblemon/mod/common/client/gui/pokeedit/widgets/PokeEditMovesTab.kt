/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.gui.pokeedit.widgets

import com.cobblemon.mod.common.api.gui.blitk
import com.cobblemon.mod.common.api.moves.MoveTemplate
import com.cobblemon.mod.common.api.moves.Moves
import com.cobblemon.mod.common.api.text.bold
import com.cobblemon.mod.common.client.CobblemonResources
import com.cobblemon.mod.common.client.gui.MoveCategoryIcon
import com.cobblemon.mod.common.client.gui.ScrollingWidget
import com.cobblemon.mod.common.client.gui.TypeIcon
import com.cobblemon.mod.common.client.gui.pokeedit.PokeEditDraft
import com.cobblemon.mod.common.client.gui.summary.widgets.SoundlessWidget
import com.cobblemon.mod.common.client.render.drawScaledText
import com.cobblemon.mod.common.util.cobblemonResource
import com.cobblemon.mod.common.util.lang
import com.cobblemon.mod.common.util.math.toRGB
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.network.chat.Component
import net.minecraft.util.Mth

class PokeEditMovesTab(
    pX: Int,
    pY: Int,
    private val draft: PokeEditDraft
) : SoundlessWidget(pX, pY, WIDTH, HEIGHT, lang("ui.pokeedit.moves")) {
    companion object {
        private const val WIDTH = 139
        private const val HEIGHT = 163
        private const val SLOT_HEIGHT = 18
        private const val SLOT_WIDTH = 91
        private const val SLOT_SPACING = 2
        private const val SCALE = 0.5F

        private val moveResource = cobblemonResource("textures/gui/summary/summary_move_condensed.png")
        private val moveOverlayResource = cobblemonResource("textures/gui/summary/summary_move_overlay_condensed.png")
        private val moveEmptyResource = cobblemonResource("textures/gui/summary/summary_move_condensed_empty.png")
        private val moveEmptyIconResource = cobblemonResource("textures/gui/summary/summary_move_condensed_empty_icon.png")
    }

    private val activeSlotX = x + ((WIDTH - SLOT_WIDTH) / 2)
    private val activeSlotY = y + 4
    private val moveListX = x + 15
    private val moveListY = y + 84
    private val moveListWidth = 108
    private val moveListHeight = HEIGHT - (moveListY - y) - 4

    private var selectedSlotIndex = -1
    private var allMoves: List<MoveTemplate> = emptyList()
    private var filteredMoves: List<MoveTemplate> = emptyList()

    private val slots = (0 until 4).map { index ->
        ActiveMoveSlot(
            activeSlotX,
            activeSlotY + index * (SLOT_HEIGHT + SLOT_SPACING),
            index
        ).also { addWidget(it) }
    }

    private val moveList = MoveList(
        moveListX,
        moveListY,
        moveListWidth,
        moveListHeight
    ).also { addWidget(it) }

    init {
        refreshMoves()
    }

    override fun renderWidget(context: GuiGraphics, mouseX: Int, mouseY: Int, delta: Float) {
        if (allMoves.isEmpty()) {
            refreshMoves()
        }

        drawScaledText(
            context = context,
            font = CobblemonResources.DEFAULT_LARGE,
            text = lang("ui.pokeedit.moves.active").bold(),
            x = x + (WIDTH / 2.0),
            y = y + 0.5,
            scale = SCALE,
            centered = true
        )

        slots.forEach { it.render(context, mouseX, mouseY, delta) }

        context.fill(
            moveListX,
            moveListY,
            moveListX + moveListWidth,
            moveListY + moveListHeight,
            0x22000000
        )

        drawScaledText(
            context = context,
            font = CobblemonResources.DEFAULT_LARGE,
            text = lang("ui.pokeedit.moves.moveset").bold(),
            x = x + (WIDTH / 2.0),
            y = moveListY - 10,
            scale = SCALE,
            centered = true
        )

        moveList.render(context, mouseX, mouseY, delta)
    }

    private fun refreshMoves() {
        allMoves = Moves.all().sortedBy { it.displayName.string }
        filteredMoves = allMoves
        moveList.setEntries(filteredMoves)
    }

    private fun assignMove(slotIndex: Int, move: MoveTemplate) {
        // Remove duplicates from other active slots
        draft.activeMoves.indices.forEach { index ->
            if (index != slotIndex && draft.activeMoves[index] == move) {
                draft.activeMoves[index] = null
            }
        }
        draft.activeMoves[slotIndex] = move
        draft.benchedMoves.remove(move)
    }

    private fun toggleBenched(move: MoveTemplate) {
        if (draft.activeMoves.any { it == move }) return
        if (draft.benchedMoves.contains(move)) {
            draft.benchedMoves.remove(move)
        } else {
            draft.benchedMoves.add(move)
        }
    }

    private fun isActive(move: MoveTemplate): Boolean {
        return draft.activeMoves.any { it == move }
    }

    private fun isBenched(move: MoveTemplate): Boolean {
        return draft.benchedMoves.contains(move)
    }

    private inner class ActiveMoveSlot(
        pX: Int,
        pY: Int,
        private val slotIndex: Int
    ) : SoundlessWidget(pX, pY, SLOT_WIDTH, SLOT_HEIGHT, Component.literal("move-slot-$slotIndex")) {

        override fun renderWidget(context: GuiGraphics, mouseX: Int, mouseY: Int, delta: Float) {
            val move = draft.activeMoves[slotIndex]
            val matrices = context.pose()
            val hovered = isHovered(mouseX.toDouble(), mouseY.toDouble())

            if (selectedSlotIndex == slotIndex) {
                context.fill(x - 1, y - 1, x + width + 1, y + height + 1, 0x5500BBD4)
            }

            if (move == null) {
                blitk(
                    matrixStack = matrices,
                    texture = moveEmptyResource,
                    x = x,
                    y = y,
                    width = width,
                    height = height,
                    vOffset = if (hovered) height else 0,
                    textureHeight = height * 2
                )
                blitk(
                    matrixStack = matrices,
                    texture = moveEmptyIconResource,
                    x = x + (width / 2) - 3,
                    y = y + (height / 2) - 3,
                    width = 7,
                    height = 7,
                    vOffset = if (hovered) 7 else 0,
                    textureHeight = 14
                )
                return
            }

            val rgb = move.elementalType.hue.toRGB()
            blitk(
                matrixStack = matrices,
                texture = moveResource,
                x = x,
                y = y,
                width = width,
                height = height,
                vOffset = if (hovered) height else 0,
                textureHeight = height * 2,
                red = rgb.first,
                green = rgb.second,
                blue = rgb.third
            )

            blitk(
                matrixStack = matrices,
                texture = moveOverlayResource,
                x = x,
                y = y,
                width = width,
                height = height
            )

            TypeIcon(
                x = x - 9,
                y = y,
                type = move.elementalType
            ).render(context)

            MoveCategoryIcon(
                x = x + 67,
                y = y + 1.5,
                category = move.damageCategory
            ).render(context)

            drawScaledText(
                context = context,
                font = CobblemonResources.DEFAULT_LARGE,
                text = move.displayName.bold(),
                x = x + 12,
                y = y + 3.5,
                scale = SCALE,
                shadow = true
            )
        }

        override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
            if (!isHovered(mouseX, mouseY)) return false
            if (button == 1) {
                draft.activeMoves[slotIndex] = null
                if (selectedSlotIndex == slotIndex) {
                    selectedSlotIndex = -1
                }
                return true
            }
            selectedSlotIndex = if (selectedSlotIndex == slotIndex) -1 else slotIndex
            return true
        }

        private fun isHovered(mouseX: Double, mouseY: Double): Boolean {
            return mouseX >= x && mouseY >= y && mouseX < x + width && mouseY < y + height
        }
    }

    private inner class MoveList(
        pX: Int,
        pY: Int,
        width: Int,
        height: Int
    ) : ScrollingWidget<MoveList.MoveEntry>(
        width = width,
        height = height,
        left = pX,
        top = pY - height,
        slotHeight = SLOT_HEIGHT + 2,
        scrollBarWidth = 3
    ) {
        private val listX = pX
        private var entries: List<MoveTemplate> = emptyList()

        fun setEntries(moves: List<MoveTemplate>) {
            clearEntries()
            entries = moves
            moves.forEach { move -> addEntry(MoveEntry(move)) }
            scrollAmount = 0.0
        }

        override fun getScrollbarPosition(): Int = listX + width - 3

        override fun renderScrollbar(context: GuiGraphics, mouseX: Int, mouseY: Int, delta: Float) {
            val xLeft = this.scrollbarPosition
            val xRight = xLeft + 3
            val barHeight = this.bottom - this.y

            var yBottom = ((barHeight * barHeight).toFloat() / this.maxPosition.toFloat()).toInt()
            yBottom = Mth.clamp(yBottom, 32, barHeight - 8)
            var yTop = scrollAmount.toInt() * (barHeight - yBottom) / this.maxScroll + this.y
            if (yTop < this.y) yTop = this.y

            context.fill(xLeft, this.y + 3, xRight, this.bottom - 3, 0xFF3A96B6.toInt())
            context.fill(xLeft, yTop + 3, xRight, yTop + yBottom - 3, 0xFFFCFCFC.toInt())
        }

        inner class MoveEntry(
            private val move: MoveTemplate
        ) : Slot<MoveEntry>() {
            private var x: Int = 0
            private var y: Int = 0

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
                this.x = x
                this.y = y + 1
                val matrices = context.pose()
                val rgb = move.elementalType.hue.toRGB()

                if (isActive(move)) {
                    context.fill(this.x - 1, this.y - 1, this.x + entryWidth + 1, this.y + SLOT_HEIGHT + 1, 0x442A7FFF)
                } else if (isBenched(move)) {
                    context.fill(this.x - 1, this.y - 1, this.x + entryWidth + 1, this.y + SLOT_HEIGHT + 1, 0x4436B33F)
                }

                blitk(
                    matrixStack = matrices,
                    texture = moveResource,
                    x = this.x,
                    y = this.y,
                    width = entryWidth,
                    height = SLOT_HEIGHT,
                    vOffset = if (hovered) SLOT_HEIGHT else 0,
                    textureHeight = SLOT_HEIGHT * 2,
                    red = rgb.first,
                    green = rgb.second,
                    blue = rgb.third
                )

                blitk(
                    matrixStack = matrices,
                    texture = moveOverlayResource,
                    x = this.x,
                    y = this.y,
                    width = entryWidth,
                    height = SLOT_HEIGHT
                )

                TypeIcon(
                    x = this.x - 9,
                    y = this.y,
                    type = move.elementalType
                ).render(context)

                MoveCategoryIcon(
                    x = this.x + 67,
                    y = this.y + 1.5,
                    category = move.damageCategory
                ).render(context)

                drawScaledText(
                    context = context,
                    font = CobblemonResources.DEFAULT_LARGE,
                    text = move.displayName,
                    x = this.x + 12,
                    y = this.y + 3.5,
                    scale = SCALE,
                    shadow = true
                )
            }

            override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
                if (isMouseOver(mouseX, mouseY)) {
                    if (selectedSlotIndex >= 0) {
                        assignMove(selectedSlotIndex, move)
                    } else {
                        toggleBenched(move)
                    }
                    return true
                }
                return false
            }

            override fun getNarration(): Component {
                return move.displayName
            }
        }
    }
}
