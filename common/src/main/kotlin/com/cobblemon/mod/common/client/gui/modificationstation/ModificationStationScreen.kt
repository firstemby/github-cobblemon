/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.gui.modificationstation

import com.cobblemon.mod.common.block.modificationstation.ModificationStationMenu
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.client.resources.sounds.SimpleSoundInstance
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.sounds.SoundEvents
import net.minecraft.util.Mth
import net.minecraft.world.entity.player.Inventory

class ModificationStationScreen(
    menu: ModificationStationMenu,
    playerInventory: Inventory,
    title: Component
) : AbstractContainerScreen<ModificationStationMenu>(menu, playerInventory, title) {

    private var scrollOffset = 0.0F
    private var isScrolling = false
    private var startIndex = 0
    private var hasOutputs = false

    init {
        menu.registerUpdateListener { onOutputsChanged() }
        titleLabelY -= 1
    }

    override fun render(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        super.render(graphics, mouseX, mouseY, partialTick)
        renderTooltip(graphics, mouseX, mouseY)
    }

    override fun renderBg(graphics: GuiGraphics, partialTick: Float, mouseX: Int, mouseY: Int) {
        graphics.blit(BACKGROUND, leftPos, topPos, 0, 0, imageWidth, imageHeight)

        val scrollerY = (SCROLLER_TRAVEL * scrollOffset).toInt()
        val scrollerSprite = if (isScrollBarActive()) SCROLLER_SPRITE else SCROLLER_DISABLED_SPRITE
        graphics.blitSprite(scrollerSprite, leftPos + SCROLLER_X, topPos + SCROLLER_Y + scrollerY, SCROLLER_WIDTH, SCROLLER_HEIGHT)

        renderOutputButtons(graphics, mouseX, mouseY)
        renderOutputItems(graphics)
    }

    override fun renderTooltip(graphics: GuiGraphics, x: Int, y: Int) {
        super.renderTooltip(graphics, x, y)
        if (!hasOutputs) {
            return
        }
        val hoveredIndex = outputIndexAt(x.toDouble(), y.toDouble()) ?: return
        graphics.renderTooltip(font, menu.getOutputs()[hoveredIndex], x, y)
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        isScrolling = false
        val client = minecraft ?: return super.mouseClicked(mouseX, mouseY, button)
        if (!hasOutputs) {
            return super.mouseClicked(mouseX, mouseY, button)
        }

        val clickedIndex = outputIndexAt(mouseX, mouseY)
        val player = client.player
        if (clickedIndex != null && player != null && menu.clickMenuButton(player, clickedIndex)) {
            client.soundManager.play(SimpleSoundInstance.forUI(SoundEvents.UI_STONECUTTER_SELECT_RECIPE, 1.0F))
            client.gameMode?.handleInventoryButtonClick(menu.containerId, clickedIndex)
            return true
        }

        val scrollerLeft = leftPos + SCROLLER_X
        val scrollerTop = topPos + SCROLLER_TRACK_Y
        if (mouseX >= scrollerLeft && mouseX < scrollerLeft + SCROLLER_WIDTH && mouseY >= scrollerTop && mouseY < scrollerTop + SCROLLER_TRACK_HEIGHT) {
            isScrolling = true
        }
        return super.mouseClicked(mouseX, mouseY, button)
    }

    override fun mouseDragged(mouseX: Double, mouseY: Double, button: Int, dragX: Double, dragY: Double): Boolean {
        if (isScrolling && isScrollBarActive()) {
            val trackTop = topPos + SCROLLER_TRACK_Y
            scrollOffset = Mth.clamp((mouseY.toFloat() - trackTop - SCROLLER_HEIGHT / 2F) / (SCROLLER_TRACK_HEIGHT - SCROLLER_HEIGHT), 0.0F, 1.0F)
            startIndex = (scrollOffset * offscreenRows() + 0.5F).toInt() * GRID_COLUMNS
            return true
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY)
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, scrollX: Double, scrollY: Double): Boolean {
        if (isScrollBarActive()) {
            val rows = offscreenRows()
            scrollOffset = Mth.clamp(scrollOffset - scrollY.toFloat() / rows, 0.0F, 1.0F)
            startIndex = (scrollOffset * rows + 0.5F).toInt() * GRID_COLUMNS
        }
        return true
    }

    private fun renderOutputButtons(graphics: GuiGraphics, mouseX: Int, mouseY: Int) {
        visibleOutputIndices().forEach { index ->
            val cellX = cellX(index)
            val cellY = cellY(index)
            val isHovered = mouseX in cellX until cellX + CELL_WIDTH && mouseY in cellY until cellY + CELL_HEIGHT
            val sprite = when {
                index == menu.getSelectedOutputIndex() -> SELECTED_CELL_SPRITE
                isHovered -> HIGHLIGHTED_CELL_SPRITE
                else -> CELL_SPRITE
            }
            graphics.blitSprite(sprite, cellX, cellY - 1, CELL_WIDTH, CELL_HEIGHT)
        }
    }

    private fun renderOutputItems(graphics: GuiGraphics) {
        val outputs = menu.getOutputs()
        visibleOutputIndices().forEach { index -> graphics.renderItem(outputs[index], cellX(index), cellY(index)) }
    }

    private fun visibleOutputIndices(): IntRange {
        val lastVisible = minOf(startIndex + GRID_COLUMNS * GRID_ROWS, menu.getOutputs().size)
        return startIndex until lastVisible
    }

    private fun outputIndexAt(mouseX: Double, mouseY: Double): Int? {
        return visibleOutputIndices().firstOrNull { index ->
            val cellX = cellX(index)
            val cellY = cellY(index)
            mouseX >= cellX && mouseX < cellX + CELL_WIDTH && mouseY >= cellY && mouseY < cellY + CELL_HEIGHT
        }
    }

    private fun cellX(index: Int): Int = leftPos + GRID_X + (index - startIndex) % GRID_COLUMNS * CELL_WIDTH

    private fun cellY(index: Int): Int = topPos + GRID_Y + (index - startIndex) / GRID_COLUMNS * CELL_HEIGHT + 2

    private fun isScrollBarActive(): Boolean = hasOutputs && menu.getOutputs().size > GRID_COLUMNS * GRID_ROWS

    private fun offscreenRows(): Int = (menu.getOutputs().size + GRID_COLUMNS - 1) / GRID_COLUMNS - GRID_ROWS

    private fun onOutputsChanged() {
        hasOutputs = menu.hasInputItem()
        if (!hasOutputs) {
            scrollOffset = 0.0F
            startIndex = 0
        }
    }

    companion object {
        private val SCROLLER_SPRITE = ResourceLocation.withDefaultNamespace("container/stonecutter/scroller")
        private val SCROLLER_DISABLED_SPRITE = ResourceLocation.withDefaultNamespace("container/stonecutter/scroller_disabled")
        private val SELECTED_CELL_SPRITE = ResourceLocation.withDefaultNamespace("container/stonecutter/recipe_selected")
        private val HIGHLIGHTED_CELL_SPRITE = ResourceLocation.withDefaultNamespace("container/stonecutter/recipe_highlighted")
        private val CELL_SPRITE = ResourceLocation.withDefaultNamespace("container/stonecutter/recipe")
        private val BACKGROUND = ResourceLocation.withDefaultNamespace("textures/gui/container/stonecutter.png")

        private const val GRID_COLUMNS = 4
        private const val GRID_ROWS = 3
        private const val CELL_WIDTH = 16
        private const val CELL_HEIGHT = 18
        private const val GRID_X = 52
        private const val GRID_Y = 14
        private const val SCROLLER_X = 119
        private const val SCROLLER_Y = 15
        private const val SCROLLER_WIDTH = 12
        private const val SCROLLER_HEIGHT = 15
        private const val SCROLLER_TRAVEL = 41.0F
        private const val SCROLLER_TRACK_Y = 9
        private const val SCROLLER_TRACK_HEIGHT = 54
    }
}