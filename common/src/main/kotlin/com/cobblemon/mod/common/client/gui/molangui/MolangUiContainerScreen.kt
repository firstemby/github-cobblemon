/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.gui.molangui

import com.cobblemon.mod.common.api.molangui.container.MolangContainerMenu
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.player.Inventory
import java.util.UUID

class MolangUiContainerScreen(
    menu: MolangContainerMenu,
    playerInventory: Inventory,
    val sessionId: UUID,
    val widgetTree: MolangUiWidgetTree
) : AbstractContainerScreen<MolangContainerMenu>(menu, playerInventory, Component.empty()) {
    private val widgetButtons = mutableSetOf<Int>()

    init {
        titleLabelX = Int.MAX_VALUE
        titleLabelY = Int.MAX_VALUE
        inventoryLabelX = Int.MAX_VALUE
        inventoryLabelY = Int.MAX_VALUE
    }

    override fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, delta: Float) {
        widgetTree.render(guiGraphics, width, height, mouseX.toDouble(), mouseY.toDouble(), delta)
        super.render(guiGraphics, mouseX, mouseY, delta)
    }

    override fun renderBg(guiGraphics: GuiGraphics, delta: Float, mouseX: Int, mouseY: Int) {
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (menu.slots.any { it.isActive && isHovering(it.x, it.y, 16, 16, mouseX, mouseY) }) {
            return super.mouseClicked(mouseX, mouseY, button)
        }
        if (widgetTree.onClick(mouseX, mouseY, button)) {
            widgetButtons.add(button)
            return true
        }
        return super.mouseClicked(mouseX, mouseY, button)
    }

    override fun mouseReleased(mouseX: Double, mouseY: Double, button: Int): Boolean =
        widgetButtons.remove(button) || super.mouseReleased(mouseX, mouseY, button)

    override fun mouseDragged(mouseX: Double, mouseY: Double, button: Int, dragX: Double, dragY: Double): Boolean =
        button in widgetButtons || super.mouseDragged(mouseX, mouseY, button, dragX, dragY)

    override fun mouseScrolled(mouseX: Double, mouseY: Double, horizontalAmount: Double, verticalAmount: Double): Boolean {
        val handled = widgetTree.onScroll(mouseX, mouseY, verticalAmount)
        return handled || super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount)
    }

    override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        val handled = widgetTree.onKeyPress(keyCode, scanCode, modifiers)
        if (handled) return true
        return super.keyPressed(keyCode, scanCode, modifiers)
    }

    override fun removed() {
        try {
            super.removed()
        } finally {
            widgetButtons.clear()
            MolangUiClientManager.onScreenRemoved(sessionId, this, widgetTree)
        }
    }

    override fun isPauseScreen(): Boolean = false
}
