/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.gui.molangui

import com.cobblemon.mod.common.api.molangui.container.MolangContainerMenu
import com.cobblemon.mod.common.mixin.invoker.AbstractContainerScreenInvoker
import com.cobblemon.mod.common.net.messages.server.molangui.MolangUiClosedPacket
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.player.Inventory
import java.util.UUID

/**
 * A container screen that renders Molang UI widgets alongside vanilla inventory slots.
 * Vanilla handles all slot interaction (drag, split, shift-click, number keys).
 * The Molang widget tree handles everything else (labels, images, buttons, etc.).
 */
class MolangUiContainerScreen(
    menu: MolangContainerMenu,
    playerInventory: Inventory,
    val sessionId: UUID,
    val widgetTree: MolangUiWidgetTree
) : AbstractContainerScreen<MolangContainerMenu>(menu, playerInventory, Component.empty()) {

    init {
        // Hide the default title and inventory labels
        titleLabelX = Int.MAX_VALUE
        titleLabelY = Int.MAX_VALUE
        inventoryLabelX = Int.MAX_VALUE
        inventoryLabelY = Int.MAX_VALUE
    }

    override fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, delta: Float) {
        // 1. Render Molang widget tree (background panels, labels, images, decorations)
        widgetTree.render(guiGraphics, width, height, mouseX.toDouble(), mouseY.toDouble(), delta)
        // 2. Render vanilla slots (items, slot highlights, cursor item)
        super.render(guiGraphics, mouseX, mouseY, delta)
    }

    override fun renderBg(guiGraphics: GuiGraphics, delta: Float, mouseX: Int, mouseY: Int) {
        // No default background — the Molang widget tree handles all background rendering
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        // If the click is over a vanilla slot, let vanilla handle it (drag, pick up, etc.).
        // Otherwise, let Molang handle it (buttons, toggles, etc.).
        val slot = (this as AbstractContainerScreenInvoker).callFindSlot(mouseX, mouseY)
        if (slot != null) {
            return super.mouseClicked(mouseX, mouseY, button)
        }
        widgetTree.onClick(mouseX, mouseY, button)
        return super.mouseClicked(mouseX, mouseY, button)
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, horizontalAmount: Double, verticalAmount: Double): Boolean {
        val handled = widgetTree.onScroll(mouseX, mouseY, verticalAmount)
        return handled || super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount)
    }

    override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        val handled = widgetTree.onKeyPress(keyCode, scanCode, modifiers)
        if (handled) return true
        return super.keyPressed(keyCode, scanCode, modifiers)
    }

    override fun onClose() {
        widgetTree.cleanup()
        MolangUiClosedPacket(sessionId).sendToServer()
        MolangUiClientManager.onScreenClosed(sessionId)
        super.onClose()
    }

    override fun isPauseScreen(): Boolean = false
}
