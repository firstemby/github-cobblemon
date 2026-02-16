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
import com.cobblemon.mod.common.client.gui.TypeIcon
import com.cobblemon.mod.common.client.gui.pokeedit.PokeEditDraft
import com.cobblemon.mod.common.client.gui.summary.widgets.SoundlessWidget
import com.cobblemon.mod.common.client.gui.summary.widgets.common.SummaryScrollList
import com.cobblemon.mod.common.client.gui.summary.widgets.screens.moves.MoveDescriptionScrollList
import com.cobblemon.mod.common.client.gui.summary.widgets.screens.moves.ReorderMoveButton
import com.cobblemon.mod.common.client.render.drawScaledText
import com.cobblemon.mod.common.util.cobblemonResource
import com.cobblemon.mod.common.util.lang
import com.cobblemon.mod.common.util.math.toRGB
import java.math.RoundingMode
import java.text.DecimalFormat
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MutableComponent
import net.minecraft.util.Mth

class PokeEditMovesTab(
    pX: Int,
    pY: Int,
    private val draft: PokeEditDraft
) : SoundlessWidget(pX, pY, WIDTH, HEIGHT, lang("ui.moves")) {
    companion object {
        private const val WIDTH = 139
        private const val HEIGHT = 163
        private const val BASE_WIDTH = 134
        private const val BASE_HEIGHT = 148
        private const val BASE_SCALE = 0.80F
        private const val SLOT_SPACING = 3
        private const val SCALE = 0.5F

        private val decimalFormat = DecimalFormat("#.##").also {
            it.roundingMode = RoundingMode.CEILING
        }

        private val movesBaseResource = cobblemonResource("textures/gui/summary/summary_moves_base.png")
        private val moveResource = cobblemonResource("textures/gui/summary/summary_move.png")
        private val moveOverlayResource = cobblemonResource("textures/gui/summary/summary_move_overlay.png")
        private val moveSelectedOverlayResource = cobblemonResource("textures/gui/summary/summary_move_selected_overlay.png")
        private val moveSwapButtonResource = cobblemonResource("textures/gui/summary/summary_move_swap.png")
        private val moveAddButtonResource = cobblemonResource("textures/gui/summary/summary_move_add.png")

        private val movesPowerIconResource = cobblemonResource("textures/gui/summary/summary_moves_icon_power.png")
        private val movesAccuracyIconResource = cobblemonResource("textures/gui/summary/summary_moves_icon_accuracy.png")
        private val movesEffectIconResource = cobblemonResource("textures/gui/summary/summary_moves_icon_effect.png")

        private val moveListResource = cobblemonResource("textures/gui/summary/summary_move_condensed.png")
        private val moveListOverlayResource = cobblemonResource("textures/gui/summary/summary_move_overlay_condensed.png")

        private const val MOVE_WIDTH = 108
        private const val MOVE_HEIGHT = 22
        private const val MOVE_LIST_SLOT_HEIGHT = 18
        private const val MOVE_LIST_SLOT_SPACING = 3
        private const val MOVE_LIST_X_OFFSET = 0
        private const val MOVE_LIST_Y_OFFSET = 0
    }

    private val baseScaledWidth = (BASE_WIDTH * BASE_SCALE).toInt()
    private val baseScaledHeight = (BASE_HEIGHT * BASE_SCALE).toInt()
    private val moveWidthScaled = (MOVE_WIDTH * BASE_SCALE).toInt()
    private val moveHeightScaled = (MOVE_HEIGHT * BASE_SCALE).toInt()
    private val slotSpacingScaled = (SLOT_SPACING * BASE_SCALE).toInt().coerceAtLeast(1)
    private val baseX = x + (WIDTH - baseScaledWidth) / 2 - 10
    private val baseY = y + 7
    private val slotX = baseX + (13 * BASE_SCALE).toInt()
    private val slotY = baseY + (6 * BASE_SCALE).toInt()
    private val descriptionListX = (baseX + (69 * BASE_SCALE).toInt())
        .coerceAtMost(baseX + baseScaledWidth - 60)
    private val descriptionListY = baseY + (113 * BASE_SCALE).toInt()

    private val slots = (0 until 4).map { index ->
        MoveSlot(
            slotX,
            slotY + (moveHeightScaled + slotSpacingScaled) * index,
            index
        ).also { addWidget(it) }
    }

    private val descriptionList = MoveDescriptionScrollList(
        descriptionListX,
        descriptionListY,
        5
    )

    private val movePicker = MovePickerList(
        baseX + baseScaledWidth + MOVE_LIST_X_OFFSET,
        baseY + (11 * BASE_SCALE).toInt() + MOVE_LIST_Y_OFFSET
    )

    private var selectedSlotIndex = -1
    private var pickerSlotIndex = -1
    private var movePickerOpen = false
    private var lastDescriptionKey: String? = null

    init {
        refreshMoves()
    }

    override fun renderWidget(context: GuiGraphics, mouseX: Int, mouseY: Int, delta: Float) {
        val matrices = context.pose()

        blitk(
            matrixStack = matrices,
            texture = movesBaseResource,
            x = baseX,
            y = baseY,
            width = baseScaledWidth,
            height = baseScaledHeight
        )

        slots.forEach { it.render(context, mouseX, mouseY, delta) }

        renderMoveInfo(context)
        renderDescription(context, mouseX, mouseY, delta)

        if (movePickerOpen) {
            movePicker.renderWidget(context, mouseX, mouseY, delta)
        }
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (movePickerOpen) {
            val handled = movePicker.mouseClicked(mouseX, mouseY, button)
            if (handled) return true
            if (!movePicker.isMouseOver(mouseX, mouseY)) {
                closeMovePicker()
            }
        }

        if (descriptionList.isHovered) {
            descriptionList.mouseClicked(mouseX, mouseY, button)
        }
        return super.mouseClicked(mouseX, mouseY, button)
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, horizontalAmount: Double, verticalAmount: Double): Boolean {
        if (movePickerOpen) {
            if (movePicker.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount)) return true
        }
        if (descriptionList.isHovered) {
            descriptionList.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount)
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount)
    }

    override fun mouseDragged(mouseX: Double, mouseY: Double, button: Int, deltaX: Double, deltaY: Double): Boolean {
        if (movePickerOpen) {
            if (movePicker.mouseDragged(mouseX, mouseY, button, deltaX, deltaY)) return true
        }
        if (descriptionList.isHovered) {
            descriptionList.mouseDragged(mouseX, mouseY, button, deltaX, deltaY)
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY)
    }

    private fun refreshMoves() {
        val moves = Moves.all().sortedBy { it.displayName.string }
        movePicker.setEntries(moves)
    }

    private fun selectedMove(): MoveTemplate? {
        return if (selectedSlotIndex in draft.activeMoves.indices) draft.activeMoves[selectedSlotIndex] else null
    }

    private fun renderMoveInfo(context: GuiGraphics) {
        val move = selectedMove()
        val mcFont = Minecraft.getInstance().font

        // Power
        blitk(
            matrixStack = context.pose(),
            texture = movesPowerIconResource,
            x = (baseX + (7 * BASE_SCALE)) / SCALE,
            y = (baseY + (114.5 * BASE_SCALE)) / SCALE,
            width = 10,
            height = 10,
            scale = SCALE
        )
        drawScaledText(
            context = context,
            text = lang("ui.power"),
            x = baseX + (14 * BASE_SCALE),
            y = baseY + (115 * BASE_SCALE),
            scale = SCALE * BASE_SCALE,
            shadow = true
        )
        val powerText = if (move != null && move.power.toInt() > 0) move.power.toInt().toString() else "-"
        val powerComponent = Component.literal(powerText)
        drawScaledText(
            context = context,
            text = powerComponent,
            x = (baseX + (62.5 * BASE_SCALE)) - (mcFont.width(powerComponent) * SCALE * BASE_SCALE),
            y = baseY + (115 * BASE_SCALE),
            scale = SCALE * BASE_SCALE,
            shadow = true
        )

        // Accuracy
        blitk(
            matrixStack = context.pose(),
            texture = movesAccuracyIconResource,
            x = (baseX + (7 * BASE_SCALE)) / SCALE,
            y = (baseY + (125.5 * BASE_SCALE)) / SCALE,
            width = 10,
            height = 10,
            scale = SCALE
        )
        drawScaledText(
            context = context,
            text = lang("ui.accuracy"),
            x = baseX + (14 * BASE_SCALE),
            y = baseY + (126 * BASE_SCALE),
            scale = SCALE * BASE_SCALE,
            shadow = true
        )
        val accuracyText = if (move != null) formatPercent(move.accuracy) else "-"
        val accuracyComponent = Component.literal(accuracyText)
        drawScaledText(
            context = context,
            text = accuracyComponent,
            x = (baseX + (62.5 * BASE_SCALE)) - (mcFont.width(accuracyComponent) * SCALE * BASE_SCALE),
            y = baseY + (126 * BASE_SCALE),
            scale = SCALE * BASE_SCALE,
            shadow = true
        )

        // Category
        blitk(
            matrixStack = context.pose(),
            texture = movesEffectIconResource,
            x = (baseX + (7 * BASE_SCALE)) / SCALE,
            y = (baseY + (136.5 * BASE_SCALE)) / SCALE,
            width = 10,
            height = 10,
            scale = SCALE
        )
        drawScaledText(
            context = context,
            text = lang("ui.category"),
            x = baseX + (14 * BASE_SCALE),
            y = baseY + (137 * BASE_SCALE),
            scale = SCALE * BASE_SCALE,
            shadow = true
        )
        if (move != null) {
            MoveCategoryIcon(
                x = baseX + (51 * BASE_SCALE),
                y = baseY + (137 * BASE_SCALE),
                category = move.damageCategory
            ).render(context)
            drawScaledText(
                context = context,
                text = move.damageCategory.displayName.copy(),
                x = baseX + (61 * BASE_SCALE),
                y = baseY + (137 * BASE_SCALE),
                scale = SCALE * BASE_SCALE,
                shadow = true
            )
        }
    }

    private fun renderDescription(context: GuiGraphics, mouseX: Int, mouseY: Int, delta: Float) {
        val move = selectedMove()
        val nextKey = move?.name ?: ""
        if (nextKey != lastDescriptionKey) {
            descriptionList.setMoveDescription(move?.description ?: Component.literal(""))
            lastDescriptionKey = nextKey
        }
        if (move != null) {
            descriptionList.renderWidget(context, mouseX, mouseY, delta)
        }
    }

    private fun formatPercent(input: Double): String {
        if (input <= 0) return "-"
        return "${decimalFormat.format(input)}%"
    }

    private fun toggleMovePicker(slotIndex: Int) {
        selectedSlotIndex = slotIndex
        if (movePickerOpen && pickerSlotIndex == slotIndex) {
            closeMovePicker()
            return
        }
        movePickerOpen = true
        pickerSlotIndex = slotIndex
    }

    private fun closeMovePicker() {
        movePickerOpen = false
        pickerSlotIndex = -1
    }

    private fun assignMove(slotIndex: Int, move: MoveTemplate) {
        draft.activeMoves.indices.forEach { index ->
            if (index != slotIndex && draft.activeMoves[index] == move) {
                draft.activeMoves[index] = null
            }
        }
        draft.activeMoves[slotIndex] = move
    }

    private fun reorderMove(slotIndex: Int, up: Boolean) {
        val size = draft.activeMoves.size
        if (size == 0) return
        val target = if (up) {
            if (slotIndex > 0) slotIndex - 1 else size - 1
        } else {
            if (slotIndex < size - 1) slotIndex + 1 else 0
        }
        val temp = draft.activeMoves[target]
        draft.activeMoves[target] = draft.activeMoves[slotIndex]
        draft.activeMoves[slotIndex] = temp

        if (selectedSlotIndex == slotIndex) selectedSlotIndex = target
        else if (selectedSlotIndex == target) selectedSlotIndex = slotIndex

        if (pickerSlotIndex == slotIndex) pickerSlotIndex = target
        else if (pickerSlotIndex == target) pickerSlotIndex = slotIndex
    }

    private inner class MoveSlot(
        pX: Int,
        pY: Int,
        private val slotIndex: Int
    ) : SoundlessWidget(pX, pY, moveWidthScaled, moveHeightScaled, Component.literal("move-slot-$slotIndex")) {

        private val moveUpButton = ReorderMoveButton(x, y, true) {
            reorderMove(slotIndex, true)
        }.apply { addWidget(this) }

        private val moveDownButton = ReorderMoveButton(x, y, false) {
            reorderMove(slotIndex, false)
        }.apply { addWidget(this) }

        override fun renderWidget(context: GuiGraphics, mouseX: Int, mouseY: Int, delta: Float) {
            val move = draft.activeMoves[slotIndex]
            val matrices = context.pose()
            isHovered = mouseX >= x && mouseY >= y && mouseX < x + width && mouseY < y + height

            if (move != null) {
                val rgb = move.elementalType.hue.toRGB()

                if (selectedSlotIndex == slotIndex) {
                    blitk(
                        matrixStack = matrices,
                        texture = moveSelectedOverlayResource,
                        x = x - 1,
                        y = y - 1,
                        width = moveWidthScaled + 2,
                        height = moveHeightScaled + 2
                    )
                }

                blitk(
                    matrixStack = matrices,
                    texture = moveResource,
                    x = x,
                    y = y,
                    width = moveWidthScaled,
                    height = moveHeightScaled,
                    vOffset = if (isHovered) moveHeightScaled else 0,
                    textureHeight = moveHeightScaled * 2,
                    red = rgb.first,
                    green = rgb.second,
                    blue = rgb.third
                )

                blitk(
                    matrixStack = matrices,
                    texture = moveOverlayResource,
                    x = x,
                    y = y,
                    width = moveWidthScaled,
                    height = moveHeightScaled
                )

                val ppText = Component.literal("${move.pp}/${move.maxPp}").bold()
                drawScaledText(
                    context = context,
                    font = CobblemonResources.DEFAULT_LARGE,
                    text = ppText,
                    x = x + (93 * BASE_SCALE),
                    y = y + (13 * BASE_SCALE),
                    centered = true,
                    scale = BASE_SCALE
                )

                TypeIcon(
                    x = x + (2 * BASE_SCALE),
                    y = y + (2 * BASE_SCALE),
                    type = move.elementalType
                ).render(context)

                MoveCategoryIcon(
                    x = x + (66 * BASE_SCALE),
                    y = y + (13.5 * BASE_SCALE),
                    category = move.damageCategory
                ).render(context)

                drawScaledText(
                    context = context,
                    font = CobblemonResources.DEFAULT_LARGE,
                    text = move.displayName.bold(),
                    x = x + (28 * BASE_SCALE),
                    y = y + (2 * BASE_SCALE),
                    shadow = true,
                    scale = BASE_SCALE
                )

                moveUpButton.render(context, mouseX, mouseY, delta)
                moveDownButton.render(context, mouseX, mouseY, delta)
            }

            renderSwapButton(context, mouseX, mouseY, move)
        }

        override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
            if (isSwapHovered(mouseX, mouseY)) {
                toggleMovePicker(slotIndex)
                return true
            }
            if (isHovered) {
                selectedSlotIndex = slotIndex
            }
            return super.mouseClicked(mouseX, mouseY, button)
        }

        private fun renderSwapButton(context: GuiGraphics, mouseX: Int, mouseY: Int, move: MoveTemplate?) {
            val selected = movePickerOpen && pickerSlotIndex == slotIndex
            val swapOffsetX = 114.5F * BASE_SCALE
            val swapOffsetY = 6.5F * BASE_SCALE
            val swapWidth = 12F * BASE_SCALE
            val swapHeight = 18F * BASE_SCALE
            blitk(
                matrixStack = context.pose(),
                texture = if (move == null) moveAddButtonResource else moveSwapButtonResource,
                x = (x + swapOffsetX) / SCALE,
                y = (y + swapOffsetY) / SCALE,
                width = swapWidth,
                height = swapHeight,
                vOffset = if (isSwapHovered(mouseX.toDouble(), mouseY.toDouble()) || selected) swapHeight else 0F,
                textureHeight = swapHeight * 2,
                scale = SCALE
            )
        }

        private fun isSwapHovered(mouseX: Double, mouseY: Double): Boolean {
            val swapOffsetX = 114.5F * BASE_SCALE
            val swapOffsetY = 6.5F * BASE_SCALE
            val swapWidth = 12F * BASE_SCALE
            val swapHeight = 18F * BASE_SCALE
            val swapX = x + swapOffsetX
            val swapY = y + swapOffsetY
            return mouseX.toFloat() in (swapX..(swapX + (swapWidth * SCALE)))
                && mouseY.toFloat() in (swapY..(swapY + (swapHeight * SCALE) - 0.5F))
        }
    }

    private inner class MovePickerList(
        listX: Int,
        listY: Int
    ) : SummaryScrollList<MovePickerList.MoveEntry>(
        listX,
        listY,
        lang("ui.moves.switch"),
        MOVE_LIST_SLOT_HEIGHT + MOVE_LIST_SLOT_SPACING
    ) {
        private var entries: List<MoveTemplate> = emptyList()

        fun setEntries(moves: List<MoveTemplate>) {
            clearEntries()
            entries = moves
            moves.forEach { move -> addEntry(MoveEntry(move)) }
            scrollAmount = 0.0
        }

        override fun renderWidget(context: GuiGraphics, mouseX: Int, mouseY: Int, partialTicks: Float) {
            if (!movePickerOpen) return
            super.renderWidget(context, mouseX, mouseY, partialTicks)
        }

        override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
            if (!movePickerOpen) return false
            return super.mouseClicked(mouseX, mouseY, button)
        }

        override fun mouseScrolled(mouseX: Double, mouseY: Double, horizontalAmount: Double, verticalAmount: Double): Boolean {
            if (!movePickerOpen) return false
            return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount)
        }

        override fun mouseDragged(mouseX: Double, mouseY: Double, button: Int, deltaX: Double, deltaY: Double): Boolean {
            if (!movePickerOpen) return false
            return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY)
        }

        inner class MoveEntry(private val move: MoveTemplate) : Entry<MoveEntry>() {

            override fun getNarration(): MutableComponent = move.displayName.copy()

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
                val matrices = context.pose()
                val tweakedRowTop = rowTop - (MOVE_LIST_SLOT_SPACING / 2) + 1
                val rgb = move.elementalType.hue.toRGB()

                blitk(
                    matrixStack = matrices,
                    texture = moveListResource,
                    x = rowLeft,
                    y = tweakedRowTop,
                    height = MOVE_LIST_SLOT_HEIGHT,
                    width = rowWidth,
                    vOffset = if (isHovered) MOVE_LIST_SLOT_HEIGHT else 0,
                    textureHeight = MOVE_LIST_SLOT_HEIGHT * 2,
                    red = rgb.first,
                    green = rgb.second,
                    blue = rgb.third
                )

                blitk(
                    matrixStack = matrices,
                    texture = moveListOverlayResource,
                    x = rowLeft,
                    y = tweakedRowTop,
                    height = MOVE_LIST_SLOT_HEIGHT,
                    width = rowWidth
                )

                TypeIcon(
                    x = rowLeft - 9,
                    y = tweakedRowTop,
                    type = move.elementalType
                ).render(context)

                MoveCategoryIcon(
                    x = rowLeft + 77,
                    y = tweakedRowTop + 1.5,
                    category = move.damageCategory
                ).render(context)

                drawScaledText(
                    context = context,
                    text = move.displayName,
                    x = rowLeft + 14,
                    y = tweakedRowTop + 3.5,
                    scale = SCALE,
                    shadow = true
                )

                blitk(
                    matrixStack = matrices,
                    texture = movesPowerIconResource,
                    x = (rowLeft + 10) / SCALE,
                    y = (tweakedRowTop + 11) / SCALE,
                    width = 10,
                    height = 10,
                    scale = SCALE
                )

                blitk(
                    matrixStack = matrices,
                    texture = movesAccuracyIconResource,
                    x = (rowLeft + 30) / SCALE,
                    y = (tweakedRowTop + 11) / SCALE,
                    width = 10,
                    height = 10,
                    scale = SCALE
                )

                blitk(
                    matrixStack = matrices,
                    texture = movesEffectIconResource,
                    x = (rowLeft + 53.5) / SCALE,
                    y = (tweakedRowTop + 11) / SCALE,
                    width = 10,
                    height = 10,
                    scale = SCALE
                )

                val movePower = if (move.power.toInt() > 0) move.power.toInt().toString() else "-"
                drawScaledText(
                    context = context,
                    text = Component.literal(movePower),
                    x = rowLeft + 16.5,
                    y = tweakedRowTop + 12,
                    scale = SCALE,
                    shadow = true
                )

                drawScaledText(
                    context = context,
                    text = Component.literal(formatPercent(move.accuracy)),
                    x = rowLeft + 37,
                    y = tweakedRowTop + 12,
                    scale = SCALE,
                    shadow = true
                )

                drawScaledText(
                    context = context,
                    text = Component.literal(formatPercent(move.effectChances.firstOrNull() ?: 0.0)),
                    x = rowLeft + 60.5,
                    y = tweakedRowTop + 12,
                    scale = SCALE,
                    shadow = true
                )

                val pp = move.maxPp
                drawScaledText(
                    context = context,
                    text = lang("ui.moves.pp", pp),
                    x = rowLeft + 76,
                    y = tweakedRowTop + 12,
                    scale = SCALE,
                    shadow = true
                )
            }

            override fun mouseClicked(d: Double, e: Double, i: Int): Boolean {
                if (isMouseOver(d, e) && pickerSlotIndex >= 0) {
                    assignMove(pickerSlotIndex, move)
                    selectedSlotIndex = pickerSlotIndex
                    closeMovePicker()
                    return true
                }
                return false
            }
        }
    }
}
