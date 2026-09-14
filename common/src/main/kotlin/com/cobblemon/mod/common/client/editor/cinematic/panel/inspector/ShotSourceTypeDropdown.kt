/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.editor.cinematic.panel.inspector

import com.cobblemon.mod.common.client.editor.cinematic.EditorWidgets
import imgui.ImGui
import net.minecraft.client.resources.language.I18n

object ShotSourceTypeDropdown {

    fun render(label: String, channel: SourceChannel, currentTypeId: String?, onPick: (SourceTypeEntry) -> Unit) {
        val entries = ShotSourceTypes.forChannel(channel)
        val selectedLabel = entries.firstOrNull { it.typeId == currentTypeId }?.label
            ?: currentTypeId?.takeIf { it.isNotBlank() } ?: I18n.get("cobblemon.cinematic_editor.none")

        EditorWidgets.fullWidth()
        if (ImGui.beginCombo("##$label", selectedLabel)) {
            try {
                for (entry in entries) {
                    val isSelected = entry.typeId == currentTypeId
                    if (ImGui.selectable("${entry.label}###${entry.typeId}", isSelected) && !isSelected) onPick(entry)
                    if (isSelected) ImGui.setItemDefaultFocus()
                }
            } finally {
                ImGui.endCombo()
            }
        }
    }
}
