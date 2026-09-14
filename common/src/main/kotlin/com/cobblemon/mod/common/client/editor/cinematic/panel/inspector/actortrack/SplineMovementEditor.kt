/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.editor.cinematic.panel.inspector.actortrack

import com.cobblemon.mod.common.client.editor.cinematic.EditorPathCache
import com.cobblemon.mod.common.client.editor.cinematic.EditorWidgets
import com.cobblemon.mod.common.client.editor.cinematic.panel.inspector.SplinePathEditor
import com.cobblemon.mod.common.client.editor.cinematic.panel.inspector.inputFiniteFloat
import com.cobblemon.mod.common.client.editor.cinematic.panel.inspector.inspectorFloat
import com.cobblemon.mod.common.client.editor.cinematic.panel.inspector.inspectorString
import com.cobblemon.mod.common.client.editor.model.EditorTrack
import com.cobblemon.mod.common.client.editor.model.cinematic.ActorSplinePointTarget
import com.cobblemon.mod.common.client.editor.model.cinematic.EditorScene
import com.google.gson.JsonObject
import imgui.ImGui
import net.minecraft.client.resources.language.I18n
import imgui.type.ImFloat

class SplineMovementEditor {
    fun render(scene: EditorScene, beatId: String, actorId: String, trackIndex: Int, track: EditorTrack) {
        fun commit(after: JsonObject) {
            editActorTrack(scene, beatId, actorId, trackIndex, track) { updated ->
                updated.keySet().toList().forEach { updated.remove(it) }
                after.entrySet().forEach { (key, value) -> updated.add(key, value.deepCopy()) }
            }
        }
        ImGui.pushID("actorPath/$beatId/$actorId/$trackIndex")
        try {
            ImGui.text(I18n.get("cobblemon.cinematic_editor.actor_track.facing"))
            EditorWidgets.fullWidth()
            val facing = track.data.get("facing").inspectorString("ground").lowercase()
            if (ImGui.beginCombo("##facing", I18n.get("cobblemon.cinematic_editor.actor_track.facing.$facing"))) {
                try {
                    for (mode in listOf("ground", "flight", "fixed")) {
                        if (ImGui.selectable(I18n.get("cobblemon.cinematic_editor.actor_track.facing.$mode") + "###$mode", mode == facing) && mode != facing) commit(track.data.deepCopy().apply { addProperty("facing", mode) })
                    }
                } finally {
                    ImGui.endCombo()
                }
            }
            ImGui.text(I18n.get("cobblemon.cinematic_editor.actor_track.heading"))
            EditorWidgets.fullWidth()
            val heading = ImFloat(track.data.get("headingOffset").inspectorFloat())
            if (inputFiniteFloat("##heading", heading)) commit(track.data.deepCopy().apply { addProperty("headingOffset", heading.get()) })
            try {
                val path = EditorPathCache.actor("$beatId/$actorId/$trackIndex", track.data)
                ImGui.textWrapped(I18n.get("cobblemon.cinematic_editor.actor_track.path_stats", "%.2f".format(path.pathLength), "%.2f".format(path.pathLength / path.duration)))
                ImGui.text(I18n.get("cobblemon.cinematic_editor.actor_track.speed"))
                EditorWidgets.fullWidth()
                val speed = ImFloat((path.pathLength / path.duration).toFloat().takeIf { it.isFinite() } ?: 0f)
                if (inputFiniteFloat("##speed", speed) && speed.get() > 0f && path.pathLength > 0.0) {
                    val duration = (path.pathLength / speed.get()).toFloat()
                    if (duration.isFinite()) commit(track.data.deepCopy().apply { addProperty("duration", duration.coerceAtLeast(0.01f)) })
                }
            } catch (_: Exception) {
                ImGui.textWrapped(I18n.get("cobblemon.cinematic_editor.actor_track.path_invalid"))
            }
            SplinePathEditor.render(track.data, { ActorSplinePointTarget(beatId, actorId, trackIndex, it) }, ::commit)
        } finally {
            ImGui.popID()
        }
    }
}
