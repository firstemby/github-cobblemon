/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.gui.molangui

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import java.util.UUID

class MolangUiOverlay(
    val sessionId: UUID,
    val widgetTree: MolangUiWidgetTree,
    val blocksInput: Boolean,
    val exclusive: Boolean
) {
    fun render(guiGraphics: GuiGraphics, delta: Float) {
        val mc = Minecraft.getInstance()
        val screenWidth = mc.window.guiScaledWidth
        val screenHeight = mc.window.guiScaledHeight
        val mouseX = (mc.mouseHandler.xpos() * screenWidth / mc.window.screenWidth)
        val mouseY = (mc.mouseHandler.ypos() * screenHeight / mc.window.screenHeight)

        widgetTree.render(guiGraphics, screenWidth, screenHeight, mouseX, mouseY, delta)
    }

    fun close() {
        widgetTree.cleanup()
    }
}
