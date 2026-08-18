/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.gui.molangui

import com.cobblemon.mod.common.net.messages.server.molangui.MolangUiClosedPacket
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import java.util.UUID

class MolangUiScreen(
    val sessionId: UUID,
    val widgetTree: MolangUiWidgetTree
) : Screen(Component.empty()) {

    override fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, delta: Float) {
        widgetTree.render(guiGraphics, width, height, mouseX.toDouble(), mouseY.toDouble(), delta)
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        val handled = widgetTree.onClick(mouseX, mouseY, button)
        return handled || super.mouseClicked(mouseX, mouseY, button)
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
