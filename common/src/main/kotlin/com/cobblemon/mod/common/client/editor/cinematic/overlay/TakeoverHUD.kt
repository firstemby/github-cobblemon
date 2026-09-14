/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.editor.cinematic.overlay

import com.cobblemon.mod.common.client.editor.cinematic.EditorWidgets
import net.minecraft.client.resources.language.I18n
import imgui.flag.ImGuiCol
import com.cobblemon.mod.common.client.editor.cinematic.EditorPlayMode
import com.cobblemon.mod.common.client.editor.cinematic.EditorPlayState
import imgui.ImGui
import imgui.flag.ImGuiCond
import imgui.flag.ImGuiWindowFlags

// Keep an explicit exit control available when another UI consumes Escape.
object TakeoverHUD {

    private const val EXIT_BTN_COLOR = 0xFF4444EFL.toInt()
    private const val EXIT_BTN_HOVER_COLOR = 0xFF6666FFL.toInt()

    fun render(playState: EditorPlayState) {
        if (playState.mode != EditorPlayMode.TAKEOVER) return

        val viewport = ImGui.getMainViewport()
        val status = I18n.get("cobblemon.cinematic_editor.takeover.active")
        val exit = I18n.get("cobblemon.cinematic_editor.takeover.exit")
        val contentWidth = ImGui.calcTextSize(status).x + ImGui.getStyle().itemSpacingX + EditorWidgets.buttonWidth(exit)
        val windowWidth = (contentWidth + ImGui.getStyle().windowPaddingX * 2f)
            .coerceAtMost((viewport.sizeX - 8f).coerceAtLeast(1f))
        val posX = viewport.posX + (viewport.sizeX - windowWidth) / 2f
        val posY = viewport.posY + 4f

        ImGui.setNextWindowPos(posX, posY, ImGuiCond.Always)
        ImGui.setNextWindowSize(windowWidth, 0f, ImGuiCond.Always)

        val flags = ImGuiWindowFlags.NoTitleBar or
            ImGuiWindowFlags.NoResize or
            ImGuiWindowFlags.NoMove or
            ImGuiWindowFlags.NoCollapse or
            ImGuiWindowFlags.NoScrollbar or
            ImGuiWindowFlags.NoSavedSettings or
            ImGuiWindowFlags.AlwaysAutoResize or
            ImGuiWindowFlags.NoFocusOnAppearing

        if (ImGui.begin("##takeoverHud", flags)) {
            ImGui.textWrapped(status)
            EditorWidgets.nextInline(EditorWidgets.buttonWidth(exit))
            ImGui.pushStyleColor(ImGuiCol.Button, EXIT_BTN_COLOR)
            ImGui.pushStyleColor(ImGuiCol.ButtonHovered, EXIT_BTN_HOVER_COLOR)
            if (ImGui.button(exit)) {
                playState.exitTakeover()
            }
            ImGui.popStyleColor(2)
        }
        ImGui.end()
    }
}
