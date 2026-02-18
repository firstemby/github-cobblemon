/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.gui.scripted

import com.cobblemon.mod.common.net.messages.server.gui.ScriptedGuiActionPacket
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import java.util.UUID

/**
 * Renders a scripted GUI as an overlay on top of the game.
 * Does not capture input unless [blocksInput] is true.
 */
class ScriptedGuiOverlay(
    val sessionId: UUID,
    val runtime: ScriptedGuiRuntime,
    val blocksInput: Boolean,
    val exclusive: Boolean
) {
    private var initDone = false

    fun render(guiGraphics: GuiGraphics, delta: Float) {
        val mc = Minecraft.getInstance()
        val mouseX = (mc.mouseHandler.xpos() * mc.window.guiScaledWidth / mc.window.screenWidth).toInt()
        val mouseY = (mc.mouseHandler.ypos() * mc.window.guiScaledHeight / mc.window.screenHeight).toInt()

        val drawingContext = DrawingContext(guiGraphics, sessionId, mouseX, mouseY)

        if (!initDone) {
            runtime.callInit(drawingContext)
            initDone = true
        }

        runtime.callRender(drawingContext, delta)

        // Process pending actions
        processPendingActions(drawingContext)

        // Check close request
        if (drawingContext.isCloseRequested()) {
            ScriptedGuiClientManager.close(sessionId)
        }
    }

    fun close() {
        val drawingContext = DrawingContext(null, sessionId, 0, 0)
        runtime.callOnClose(drawingContext)
        runtime.cleanup()
    }

    private fun processPendingActions(drawingContext: DrawingContext) {
        for (pending in drawingContext.pendingActions) {
            ScriptedGuiActionPacket(sessionId, pending.action, pending.data).sendToServer()
        }
    }
}
