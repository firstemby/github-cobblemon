/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.gui.scripted

import com.cobblemon.mod.common.net.messages.server.gui.ScriptedGuiClosedPacket
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import java.util.UUID

/**
 * Renders a scripted GUI as a full Minecraft Screen with input capture.
 * ESC closes the screen and notifies the server.
 */
class ScriptedGuiScreen(
    val sessionId: UUID,
    val widgetTree: ScriptedGuiWidgetTree
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
        ScriptedGuiClosedPacket(sessionId).sendToServer()
        ScriptedGuiClientManager.onScreenClosed(sessionId)
        super.onClose()
    }

    override fun isPauseScreen(): Boolean = false
}
