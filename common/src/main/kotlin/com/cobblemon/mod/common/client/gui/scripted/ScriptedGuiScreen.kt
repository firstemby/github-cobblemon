/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.gui.scripted

import com.cobblemon.mod.common.net.messages.server.gui.ScriptedGuiActionPacket
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
    val runtime: ScriptedGuiRuntime
) : Screen(Component.empty()) {

    private var initDone = false

    override fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, delta: Float) {
        val drawingContext = DrawingContext(guiGraphics, sessionId, mouseX, mouseY)

        if (!initDone) {
            runtime.callInit(drawingContext)
            initDone = true
        }

        runtime.callRender(drawingContext, delta)

        // Process pending actions
        for (pending in drawingContext.pendingActions) {
            ScriptedGuiActionPacket(sessionId, pending.action, pending.data).sendToServer()
        }

        if (drawingContext.isCloseRequested()) {
            onClose()
        }
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        val drawingContext = DrawingContext(null, sessionId, mouseX.toInt(), mouseY.toInt())
        val handled = runtime.callOnClick(drawingContext, mouseX, mouseY, button)

        for (pending in drawingContext.pendingActions) {
            ScriptedGuiActionPacket(sessionId, pending.action, pending.data).sendToServer()
        }

        if (drawingContext.isCloseRequested()) {
            onClose()
            return true
        }

        return handled || super.mouseClicked(mouseX, mouseY, button)
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, horizontalAmount: Double, verticalAmount: Double): Boolean {
        val drawingContext = DrawingContext(null, sessionId, mouseX.toInt(), mouseY.toInt())
        val handled = runtime.callOnScroll(drawingContext, mouseX, mouseY, verticalAmount)

        for (pending in drawingContext.pendingActions) {
            ScriptedGuiActionPacket(sessionId, pending.action, pending.data).sendToServer()
        }

        return handled || super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount)
    }

    override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        val drawingContext = DrawingContext(null, sessionId, 0, 0)
        val handled = runtime.callOnKeyPress(drawingContext, keyCode, scanCode, modifiers)

        for (pending in drawingContext.pendingActions) {
            ScriptedGuiActionPacket(sessionId, pending.action, pending.data).sendToServer()
        }

        if (handled) return true
        return super.keyPressed(keyCode, scanCode, modifiers)
    }

    override fun onClose() {
        val drawingContext = DrawingContext(null, sessionId, 0, 0)
        runtime.callOnClose(drawingContext)
        runtime.cleanup()
        ScriptedGuiClosedPacket(sessionId).sendToServer()
        ScriptedGuiClientManager.onScreenClosed(sessionId)
        super.onClose()
    }

    override fun isPauseScreen(): Boolean = false
}
