/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.gui.modificationstation

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.client.resources.sounds.SimpleSoundInstance
import com.cobblemon.mod.common.block.modificationstation.ModificationStationMenu
import net.minecraft.resources.ResourceLocation
import net.minecraft.sounds.SoundEvents
import net.minecraft.util.Mth
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.player.Inventory

class ModificationStationScreen(
    menu: ModificationStationMenu,
    playerInventory: Inventory,
    title: Component
) : AbstractContainerScreen<ModificationStationMenu>(menu, playerInventory, title) {

    private var scrollOffs = 0.0F
    private var scrolling = false
    private var startIndex = 0
    private var displayVariants = false

    init {
        menu.registerUpdateListener { containerChanged() }
        titleLabelY -= 1
    }

    override fun render(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        super.render(graphics, mouseX, mouseY, partialTick)
        renderTooltip(graphics, mouseX, mouseY)
    }

    override fun renderBg(graphics: GuiGraphics, partialTick: Float, mouseX: Int, mouseY: Int) {
        val left = leftPos
        val top = topPos

        graphics.blit(BG_LOCATION, left, top, 0, 0, imageWidth, imageHeight)

        val yOffset = (41.0F * scrollOffs).toInt()
        val scrollerSprite = if (isScrollBarActive()) SCROLLER_SPRITE else SCROLLER_DISABLED_SPRITE
        graphics.blitSprite(scrollerSprite, left + 119, top + 15 + yOffset, 12, 15)

        val variantsLeft = leftPos + RECIPES_X
        val variantsTop = topPos + RECIPES_Y
        val variantsEnd = startIndex + (RECIPES_COLUMNS * RECIPES_ROWS)

        renderButtons(graphics, mouseX, mouseY, variantsLeft, variantsTop, variantsEnd)
        renderVariants(graphics, variantsLeft, variantsTop, variantsEnd)
    }

    override fun renderTooltip(graphics: GuiGraphics, x: Int, y: Int) {
        super.renderTooltip(graphics, x, y)

        if (!displayVariants) {
            return
        }

        val variantsLeft = leftPos + RECIPES_X
        val variantsTop = topPos + RECIPES_Y
        val variantsEnd = startIndex + (RECIPES_COLUMNS * RECIPES_ROWS)
        val outputs = menu.getVariantOutputs()

        for (index in startIndex until variantsEnd) {
            if (index >= menu.getNumVariants()) {
                break
            }

            val relativeIndex = index - startIndex
            val xPos = variantsLeft + relativeIndex % RECIPES_COLUMNS * RECIPES_IMAGE_SIZE_WIDTH
            val yPos = variantsTop + relativeIndex / RECIPES_COLUMNS * RECIPES_IMAGE_SIZE_HEIGHT + 2

            if (x >= xPos && x < xPos + RECIPES_IMAGE_SIZE_WIDTH && y >= yPos && y < yPos + RECIPES_IMAGE_SIZE_HEIGHT) {
                graphics.renderTooltip(font, outputs[index], x, y)
                break
            }
        }
    }

    private fun renderButtons(graphics: GuiGraphics, mouseX: Int, mouseY: Int, x: Int, y: Int, endIndex: Int) {
        for (index in startIndex until endIndex) {
            if (index >= menu.getNumVariants()) {
                break
            }

            val relativeIndex = index - startIndex
            val xPos = x + relativeIndex % RECIPES_COLUMNS * RECIPES_IMAGE_SIZE_WIDTH
            val row = relativeIndex / RECIPES_COLUMNS
            val yPos = y + row * RECIPES_IMAGE_SIZE_HEIGHT + 2

            val sprite = if (index == menu.getSelectedVariantIndex()) {
                RECIPE_SELECTED_SPRITE
            } else if (mouseX in xPos until (xPos + RECIPES_IMAGE_SIZE_WIDTH) && mouseY in yPos until (yPos + RECIPES_IMAGE_SIZE_HEIGHT)) {
                RECIPE_HIGHLIGHTED_SPRITE
            } else {
                RECIPE_SPRITE
            }

            graphics.blitSprite(sprite, xPos, yPos - 1, RECIPES_IMAGE_SIZE_WIDTH, RECIPES_IMAGE_SIZE_HEIGHT)
        }
    }

    private fun renderVariants(graphics: GuiGraphics, x: Int, y: Int, endIndex: Int) {
        val outputs = menu.getVariantOutputs()

        for (index in startIndex until endIndex) {
            if (index >= menu.getNumVariants()) {
                break
            }

            val relativeIndex = index - startIndex
            val xPos = x + relativeIndex % RECIPES_COLUMNS * RECIPES_IMAGE_SIZE_WIDTH
            val row = relativeIndex / RECIPES_COLUMNS
            val yPos = y + row * RECIPES_IMAGE_SIZE_HEIGHT + 2

            graphics.renderItem(outputs[index], xPos, yPos)
        }
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        scrolling = false
        val minecraftClient = minecraft ?: return super.mouseClicked(mouseX, mouseY, button)

        if (displayVariants) {
            val variantsLeft = leftPos + RECIPES_X
            val variantsTop = topPos + RECIPES_Y
            val variantsEnd = startIndex + (RECIPES_COLUMNS * RECIPES_ROWS)

            for (index in startIndex until variantsEnd) {
                val relativeIndex = index - startIndex
                val xPos = mouseX - (variantsLeft + relativeIndex % RECIPES_COLUMNS * RECIPES_IMAGE_SIZE_WIDTH)
                val yPos = mouseY - (variantsTop + relativeIndex / RECIPES_COLUMNS * RECIPES_IMAGE_SIZE_HEIGHT)
                if (xPos >= 0.0 && yPos >= 0.0 && xPos < RECIPES_IMAGE_SIZE_WIDTH.toDouble() && yPos < RECIPES_IMAGE_SIZE_HEIGHT.toDouble()) {
                    val player = minecraftClient.player
                    if (player != null && menu.clickMenuButton(player, index)) {
                        Minecraft.getInstance().soundManager.play(SimpleSoundInstance.forUI(SoundEvents.UI_STONECUTTER_SELECT_RECIPE, 1.0F))
                        minecraftClient.gameMode?.handleInventoryButtonClick(menu.containerId, index)
                        return true
                    }
                }
            }

            val scrollX = leftPos + 119
            val scrollY = topPos + 9
            if (mouseX >= scrollX && mouseX < (scrollX + 12) && mouseY >= scrollY && mouseY < (scrollY + 54)) {
                scrolling = true
            }
        }

        return super.mouseClicked(mouseX, mouseY, button)
    }

    override fun mouseDragged(mouseX: Double, mouseY: Double, button: Int, dragX: Double, dragY: Double): Boolean {
        if (scrolling && isScrollBarActive()) {
            val minY = topPos + 14
            val maxY = minY + 54
            scrollOffs = ((mouseY.toFloat() - minY.toFloat()) - 7.5F) / ((maxY - minY).toFloat() - 15.0F)
            scrollOffs = Mth.clamp(scrollOffs, 0.0F, 1.0F)
            startIndex = (scrollOffs * getOffscreenRows().toFloat() + 0.5).toInt() * RECIPES_COLUMNS
            return true
        }

        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY)
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, scrollX: Double, scrollY: Double): Boolean {
        if (isScrollBarActive()) {
            val offscreenRows = getOffscreenRows()
            val offset = scrollY.toFloat() / offscreenRows.toFloat()
            scrollOffs = Mth.clamp(scrollOffs - offset, 0.0F, 1.0F)
            startIndex = (scrollOffs * offscreenRows.toFloat() + 0.5).toInt() * RECIPES_COLUMNS
        }

        return true
    }

    private fun isScrollBarActive(): Boolean {
        return displayVariants && menu.getNumVariants() > RECIPES_COLUMNS * RECIPES_ROWS
    }

    private fun getOffscreenRows(): Int {
        return (menu.getNumVariants() + RECIPES_COLUMNS - 1) / RECIPES_COLUMNS - RECIPES_ROWS
    }

    private fun containerChanged() {
        displayVariants = menu.hasInputItem()
        if (!displayVariants) {
            scrollOffs = 0.0F
            startIndex = 0
        }
    }

    companion object {
        private val SCROLLER_SPRITE = ResourceLocation.withDefaultNamespace("container/stonecutter/scroller")
        private val SCROLLER_DISABLED_SPRITE = ResourceLocation.withDefaultNamespace("container/stonecutter/scroller_disabled")
        private val RECIPE_SELECTED_SPRITE = ResourceLocation.withDefaultNamespace("container/stonecutter/recipe_selected")
        private val RECIPE_HIGHLIGHTED_SPRITE = ResourceLocation.withDefaultNamespace("container/stonecutter/recipe_highlighted")
        private val RECIPE_SPRITE = ResourceLocation.withDefaultNamespace("container/stonecutter/recipe")
        private val BG_LOCATION = ResourceLocation.withDefaultNamespace("textures/gui/container/stonecutter.png")

        private const val RECIPES_COLUMNS = 4
        private const val RECIPES_ROWS = 3
        private const val RECIPES_IMAGE_SIZE_WIDTH = 16
        private const val RECIPES_IMAGE_SIZE_HEIGHT = 18
        private const val RECIPES_X = 52
        private const val RECIPES_Y = 14
    }
}
