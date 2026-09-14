/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.editor.cinematic.viewport

import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.client.resources.language.I18n
import com.cobblemon.mod.common.client.imgui.CobblemonImGui
import net.minecraft.client.Minecraft
import com.cobblemon.mod.common.client.editor.cinematic.EditorPlayState
import com.cobblemon.mod.common.client.editor.cinematic.EditorTheme
import imgui.ImGui

object ViewportHud {
    fun render(playState: EditorPlayState, beatLabel: String, timeSeconds: Float) {
        if (!EditorViewportState.active) return
        val dl = ImGui.getBackgroundDrawList()
        val x = EditorViewportState.frameX.toFloat()
        val y = EditorViewportState.frameY.toFloat()
        val x1 = (EditorViewportState.frameX + EditorViewportState.frameWidth).toFloat()
        val pad = 6f
        val bottom = y + EditorViewportState.frameHeight
        dl.pushClipRect(x, y, x1, bottom, true)
        val txt = EditorTheme.imguiPacked(0xFF_DFE7F2.toInt())
        val mode = if (playState.viewportCameraMode == ViewportCameraMode.SCENE_CAM) I18n.get("cobblemon.cinematic_editor.transport.scene_camera") else I18n.get("cobblemon.cinematic_editor.transport.free_camera")
        val modeWidth = ImGui.calcTextSize(mode).x
        val label = I18n.get("cobblemon.cinematic_editor.viewport.beat_time", beatLabel.take(24), "%.2f".format(timeSeconds))
        if (ImGui.calcTextSize(label).x + modeWidth + pad * 4 < x1 - x) {
            dl.addText(x + pad, y + pad, txt, label)
        }
        dl.addText(x1 - modeWidth - pad, y + pad, txt, mode)

        val navigating = CobblemonImGui.gamePassthrough
        val tab = InputConstants.Type.KEYSYM.getOrCreate(InputConstants.KEY_TAB).displayName.string
        val options = Minecraft.getInstance().options
        val hint = if (navigating) I18n.get("cobblemon.cinematic_editor.viewport.return", tab) else
            I18n.get("cobblemon.cinematic_editor.viewport.navigate", tab, options.keyUp.translatedKeyMessage.string, options.keyLeft.translatedKeyMessage.string, options.keyDown.translatedKeyMessage.string, options.keyRight.translatedKeyMessage.string)
        val hintColor = EditorTheme.imguiPacked(if (navigating) 0xFF_FBBF24.toInt() else 0xFF_9FB0C8.toInt())
        val hudClearance = 24f * Minecraft.getInstance().window.guiScale.toFloat()
        val hintY = bottom - ImGui.getTextLineHeight() - pad - hudClearance
        val hintWidth = ImGui.calcTextSize(hint).x
        if (hintWidth + pad * 2 < x1 - x && bottom - y > 80f) {
            dl.addText(x + (EditorViewportState.frameWidth - hintWidth) / 2f, hintY, hintColor, hint)
        }
        dl.popClipRect()
    }
}
