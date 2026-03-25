/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.gui.tmmachine

import com.cobblemon.mod.common.CobblemonSounds
import com.cobblemon.mod.common.api.gui.blitk
import com.cobblemon.mod.common.api.reactive.SettableObservable
import com.cobblemon.mod.common.api.text.bold
import com.cobblemon.mod.common.api.tms.TechnicalMachine
import com.cobblemon.mod.common.client.CobblemonResources
import com.cobblemon.mod.common.client.gui.MoveCategoryIcon
import com.cobblemon.mod.common.client.gui.ScrollingWidget
import com.cobblemon.mod.common.client.gui.TypeIcon
import com.cobblemon.mod.common.client.gui.summary.widgets.screens.moves.MoveSlotWidget
import com.cobblemon.mod.common.client.gui.tmmachine.MovesScrollingWidget.ScrollSlot
import com.cobblemon.mod.common.client.render.drawScaledText
import com.cobblemon.mod.common.util.cobblemonResource
import com.cobblemon.mod.common.util.lang
import com.cobblemon.mod.common.util.math.toRGB
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.resources.sounds.SimpleSoundInstance
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.util.FastColor
import net.minecraft.util.Mth

class MovesScrollingWidget(val pX: Int, val pY: Int, val tmList: SettableObservable<MutableList<TechnicalMachine>>, val setTM: (TechnicalMachine?, Boolean) -> (Unit)): ScrollingWidget<ScrollSlot>(
    width = WIDTH,
    height = HEIGHT,
    left = pX,
    top = pY - HEIGHT,
    slotHeight = SLOT_HEIGHT + SLOT_SPACING
) {
    companion object {
        const val WIDTH = 115
        const val HEIGHT = 82
        const val SLOT_HEIGHT = 22
        const val SLOT_SPACING = 4

        val searchBar = cobblemonResource("textures/gui/tmmachine/search_bar.png")
    }

    var highlightedMoveId: ResourceLocation? = null

    init {
        tmList.subscribeIncludingCurrent { tmList ->
            replaceEntries(tmList.map { tm -> ScrollSlot(tm, setTM) })
        }
    }

    fun setSlotHighlighted(id: ResourceLocation?) {
        highlightedMoveId = id
        for (child in children()) {
            if (child is ScrollSlot) {
                child.highlighted = child.tm.id == highlightedMoveId
            }
        }
    }

    override fun getScrollbarPosition(): Int = rowLeft + width - 3

    override fun renderWidget(context: GuiGraphics, mouseX: Int, mouseY: Int, delta: Float) {
        blitk(
            matrixStack = context.pose(),
            texture = searchBar,
            x = left - 4,
            y = y - 19,
            width = 120,
            height = 19
        )
        super.renderWidget(context, mouseX, mouseY, delta)
    }

    override fun renderScrollbar(context: GuiGraphics, mouseX: Int, mouseY: Int, delta: Float) {
        val xLeft = this.scrollbarPosition
        val xRight = xLeft + 3

        val barHeight = this.bottom - this.y

        var yBottom = ((barHeight * barHeight).toFloat() / this.maxPosition.toFloat()).toInt()
        yBottom = Mth.clamp(yBottom, 32, barHeight - 8)
        var yTop = scrollAmount.toInt() * (barHeight - yBottom) / this.maxScroll + this.y
        if (yTop < this.y) {
            yTop = this.y
        }

        context.fill(xLeft, this.y, xRight, this.bottom, FastColor.ARGB32.color(255, 75, 75, 75)) // background
        context.fill(xLeft,yTop, xRight, yTop + yBottom, FastColor.ARGB32.color(255, 141, 141, 141)) // base
    }

    override fun getEntry(index: Int): ScrollSlot = children()[index] as ScrollSlot

    class ScrollSlot(val tm: TechnicalMachine, val setTM : (TechnicalMachine?, clicked: Boolean) -> Unit): Slot<ScrollSlot>() {
        var posX: Int = 0
        var posY: Int = 0
        var highlighted = false

        override fun render(context: GuiGraphics, index: Int, y: Int, x: Int, entryWidth: Int, entryHeight: Int, mouseX: Int, mouseY: Int, hovered: Boolean, tickDelta: Float) {
            posX = x
            posY = y
            val moveTemplate = tm.moveName
            val rgb = moveTemplate.elementalType.hue.toRGB()
            val isHovered = hovered && (mouseX < x + (108)) // Prevent hover when over scrollbar
            val startPosY = y + SLOT_SPACING
            val startPosX = x + 1 // Hover border for move renders at x, so render move element to the right by 1

            val matrices = context.pose()
            blitk(
                matrixStack = matrices,
                texture = MoveSlotWidget.moveResource,
                x = startPosX,
                y = startPosY,
                width = MoveSlotWidget.MOVE_WIDTH,
                height = MoveSlotWidget.MOVE_HEIGHT,
                vOffset = if (isHovered) MoveSlotWidget.MOVE_HEIGHT else 0,
                textureHeight = MoveSlotWidget.MOVE_HEIGHT * 2,
                red = rgb.first,
                green = rgb.second,
                blue = rgb.third
            )

            blitk(
                matrixStack = matrices,
                texture = MoveSlotWidget.moveOverlayResource,
                x = startPosX,
                y = startPosY,
                width = MoveSlotWidget.MOVE_WIDTH,
                height = MoveSlotWidget.MOVE_HEIGHT
            )

            if (highlighted || isHovered) {
                blitk(
                    matrixStack = matrices,
                    texture = MoveSlotWidget.moveSelectedOverlayResource,
                    x = startPosX - 1,
                    y = startPosY - 1,
                    width = MoveSlotWidget.MOVE_WIDTH + 2,
                    height = MoveSlotWidget.MOVE_HEIGHT + 2
                )
            }

            drawScaledText(
                context = context,
                font = CobblemonResources.DEFAULT_LARGE,
                text = lang("ui.moves.pp", moveTemplate.pp).bold(),
                x = startPosX + 93,
                y = startPosY + 13,
                centered = true
            )

            // Type Icon
            TypeIcon(
                x = x + 2,
                y = startPosY + 2,
                type = moveTemplate.elementalType
            ).render(context)

            // Move Category
            MoveCategoryIcon(
                x = x + 66,
                y = startPosY + 13.5,
                category = moveTemplate.damageCategory
            ).render(context)

            // Move Name
            drawScaledText(
                context = context,
                font = CobblemonResources.DEFAULT_LARGE,
                text = moveTemplate.displayName.bold(),
                x = x + 28,
                y = startPosY + 2,
                shadow = true
            )

            if (isHovered) {
                setTM(tm, false)
            }
        }

        override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
            // Prevent clicking through scroll bar
            if (mouseX < posX + (108)) {
                setTM(tm, true)
                Minecraft.getInstance().soundManager.play(SimpleSoundInstance.forUI(CobblemonSounds.GUI_CLICK, 1.0F))
            }
            return super.mouseClicked(mouseX, mouseY, button)
        }

        override fun getNarration(): Component {
            return tm.moveName.displayName
        }
    }
}
