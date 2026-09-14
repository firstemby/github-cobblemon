/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.editor.cinematic

import imgui.ImGui
import net.minecraft.client.resources.language.I18n
import imgui.flag.ImGuiCol
import imgui.flag.ImGuiSelectableFlags
import imgui.type.ImString

object EditorWidgets {
    fun fullWidth() = ImGui.setNextItemWidth(ImGui.getContentRegionAvailX().coerceAtLeast(1f))
    fun width(preferred: Float) = ImGui.setNextItemWidth(preferred.coerceAtMost(ImGui.getContentRegionAvailX()).coerceAtLeast(1f))

    fun text(value: String) = ImGui.textWrapped(value)

    fun nextInline(width: Float) {
        val right = ImGui.getWindowPosX() + ImGui.getWindowContentRegionMaxX()
        if (ImGui.getItemRectMaxX() + ImGui.getStyle().itemSpacingX + width <= right) ImGui.sameLine()
    }

    fun buttonWidth(label: String): Float = ImGui.calcTextSize(label).x + ImGui.getStyle().framePaddingX * 2

    fun createRow(id: String, value: ImString, label: String): Boolean {
        ImGui.pushID(id)
        try {
            fullWidth()
            ImGui.inputTextWithHint("##name", I18n.get("cobblemon.cinematic_editor.name"), value)
            EditorDiagnostics.item("$id/name")
            val valid = value.get().isNotBlank()
            ImGui.beginDisabled(!valid)
            try {
                val clicked = ImGui.button(label, ImGui.getContentRegionAvailX().coerceAtLeast(1f), 0f)
                EditorDiagnostics.item("$id/add")
                return clicked
            } finally {
                ImGui.endDisabled()
            }
        } finally {
            ImGui.popID()
        }
    }

    fun objectRow(id: String, label: String, selected: Boolean, onSelect: () -> Unit, onDelete: () -> Unit) {
        ImGui.pushID(id.replace("\\", "\\\\").replace("#", "\\#"))
        try {
            val removeWidth = buttonWidth("x")
            val width = (ImGui.getContentRegionAvailX() - removeWidth - ImGui.getStyle().itemSpacingX).coerceAtLeast(1f)
            val textX = ImGui.getCursorScreenPosX()
            val textY = ImGui.getCursorScreenPosY()
            val height = ImGui.getFrameHeight()
            if (ImGui.selectable("##select", selected, ImGuiSelectableFlags.None, width, height)) onSelect()
            val draw = ImGui.getWindowDrawList()
            draw.pushClipRect(textX, textY, textX + width, textY + height, true)
            try {
                draw.addText(textX, textY, ImGui.getColorU32(ImGuiCol.Text), label)
            } finally {
                draw.popClipRect()
            }
            EditorDiagnostics.item("$id/select", expandedSelection = true)
            if (ImGui.isItemHovered()) ImGui.setTooltip(label)
            ImGui.sameLine()
            if (ImGui.button("x")) onDelete()
            EditorDiagnostics.item("$id/remove")
        } finally {
            ImGui.popID()
        }
    }

}
