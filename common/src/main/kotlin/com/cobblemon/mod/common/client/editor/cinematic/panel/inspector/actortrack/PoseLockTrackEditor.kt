/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.editor.cinematic.panel.inspector.actortrack

import com.cobblemon.mod.common.client.editor.cinematic.panel.inspector.inspectorString
import com.cobblemon.mod.common.client.editor.model.EditorTrack
import com.cobblemon.mod.common.client.editor.model.cinematic.EditorScene
import com.cobblemon.mod.common.entity.PoseType
import imgui.ImGui
import net.minecraft.client.resources.language.I18n

class PoseLockTrackEditor {

    fun render(scene: EditorScene, beatId: String, actorId: String, trackIndex: Int, track: EditorTrack) {
        val data = track.data
        val currentName = data.get("pose").inspectorString(PoseType.entries.first().name)
        val id = "${beatId}_${actorId}_${trackIndex}"

        ImGui.text(I18n.get("cobblemon.cinematic_editor.actor_track.pose"))
        if (ImGui.beginCombo("##poselock_$id", I18n.get("cobblemon.cinematic_editor.pose.$currentName"))) {
            try {
                for (p in PoseType.entries) {
                    val selected = p.name == currentName
                    if (ImGui.selectable(I18n.get("cobblemon.cinematic_editor.pose.${p.name}") + "###${p.name}", selected)) {
                        if (p.name != currentName) {
                            editActorTrack(scene, beatId, actorId, trackIndex, track) { it.addProperty("pose", p.name) }
                        }
                    }
                    if (selected) ImGui.setItemDefaultFocus()
                }
            } finally {
                ImGui.endCombo()
            }
        }
    }
}
