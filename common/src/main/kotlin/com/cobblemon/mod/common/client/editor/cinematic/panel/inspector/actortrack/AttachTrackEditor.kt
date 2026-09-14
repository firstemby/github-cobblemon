/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.editor.cinematic.panel.inspector.actortrack

import com.cobblemon.mod.common.client.editor.cinematic.panel.inspector.inputFiniteFloat
import com.cobblemon.mod.common.client.editor.cinematic.panel.inspector.inspectorFloat
import com.cobblemon.mod.common.client.editor.cinematic.panel.inspector.inspectorObject
import com.cobblemon.mod.common.client.editor.cinematic.panel.inspector.inspectorString
import com.cobblemon.mod.common.client.editor.model.EditorTrack
import com.cobblemon.mod.common.client.editor.model.cinematic.EditorBeat
import com.cobblemon.mod.common.client.editor.model.cinematic.EditorScene
import com.google.gson.JsonObject
import imgui.ImGui
import net.minecraft.client.resources.language.I18n
import imgui.type.ImFloat
import imgui.type.ImString

class AttachTrackEditor {
    private val parentBuf = ImString(64).apply { inputData.isResizable = true }
    private val oxBuf = ImFloat(); private val oyBuf = ImFloat(); private val ozBuf = ImFloat()
    private var syncedData: JsonObject? = null
    private var syncedGeneration = -1

    fun render(scene: EditorScene, beatId: String, actorId: String, trackIndex: Int, track: EditorTrack) {
        val data = track.data
        val offset = data.get("offset").inspectorObject()
        if (data !== syncedData || scene.editGeneration != syncedGeneration) {
            parentBuf.set(data.get("parentActorId").inspectorString())
            oxBuf.set(offset?.get("x").inspectorFloat(0f))
            oyBuf.set(offset?.get("y").inspectorFloat(0f))
            ozBuf.set(offset?.get("z").inspectorFloat(0f))
            syncedData = data
            syncedGeneration = scene.editGeneration
        }

        val id = "${beatId}_${actorId}_${trackIndex}"
        ImGui.text(I18n.get("cobblemon.cinematic_editor.actor_track.parent")); ImGui.setNextItemWidth(180f)
        if (ImGui.inputText("##attParent_$id", parentBuf)) {
            editActorTrack(scene, beatId, actorId, trackIndex, track) { it.addProperty("parentActorId", parentBuf.get()) }
        }
        if (scene.beats[beatId]?.let { detectsCycle(it, actorId, parentBuf.get()) } == true) {
            ImGui.textColored(1f, 0.4f, 0.3f, 1f, I18n.get("cobblemon.cinematic_editor.actor_track.parent_cycle"))
        }

        ImGui.text(I18n.get("cobblemon.cinematic_editor.actor_track.offset"))
        ImGui.setNextItemWidth(60f); if (inputFiniteFloat("##attOX_$id", oxBuf)) editActorTrackVector(scene, beatId, actorId, trackIndex, track, "offset", mapOf("x" to oxBuf.get().toDouble()))
        ImGui.sameLine(); ImGui.setNextItemWidth(60f); if (inputFiniteFloat("##attOY_$id", oyBuf)) editActorTrackVector(scene, beatId, actorId, trackIndex, track, "offset", mapOf("y" to oyBuf.get().toDouble()))
        ImGui.sameLine(); ImGui.setNextItemWidth(60f); if (inputFiniteFloat("##attOZ_$id", ozBuf)) editActorTrackVector(scene, beatId, actorId, trackIndex, track, "offset", mapOf("z" to ozBuf.get().toDouble()))
    }

    companion object {
        fun detectsCycle(beat: EditorBeat, actorId: String, proposedParent: String): Boolean {
            if (proposedParent.isBlank()) return false
            if (proposedParent == actorId) return true
            val visited = mutableSetOf(actorId)
            var current: String? = proposedParent
            while (current != null && current !in visited) {
                visited += current
                val direction = beat.actorDirections[current] ?: return false
                val attachTrack = direction.tracks.firstOrNull { it.trackType == "attach" } ?: return false
                val next = attachTrack.data.get("parentActorId").inspectorString().takeIf { it.isNotBlank() } ?: return false
                if (next == actorId) return true
                current = next
            }
            return current in visited
        }
    }
}
