/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.editor.cinematic.panel.timeline

import com.cobblemon.mod.common.client.editor.model.cinematic.EditorScene
import imgui.ImGui
import net.minecraft.client.resources.language.I18n

object ShotPickerPopup {

    fun open(popupId: String) { ImGui.openPopup(popupId) }

    fun render(popupId: String, scene: EditorScene, onPick: (String) -> Unit) {
        if (!ImGui.beginPopup(popupId)) return
        if (scene.shots.isEmpty()) {
            ImGui.textDisabled(I18n.get("cobblemon.cinematic_editor.timeline.no_shots"))
        } else {
            for (id in scene.shots.keys.sorted()) {
                if (ImGui.selectable(id)) {
                    onPick(id)
                    ImGui.closeCurrentPopup()
                }
            }
        }
        ImGui.endPopup()
    }
}
