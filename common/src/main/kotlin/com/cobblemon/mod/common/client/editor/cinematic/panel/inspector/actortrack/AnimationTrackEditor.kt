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
import com.cobblemon.mod.common.client.editor.model.cinematic.EditorScene
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import imgui.ImGui
import net.minecraft.client.resources.language.I18n
import imgui.type.ImFloat
import imgui.type.ImString

class AnimationTrackEditor {

    private var syncedData: JsonObject? = null
    private var syncedGeneration = -1
    private val timeBufs = mutableListOf<ImFloat>()
    private val nameBufs = mutableListOf<ImString>()

    fun render(scene: EditorScene, beatId: String, actorId: String, trackIndex: Int, track: EditorTrack) {
        val data = track.data
        val arr = data.get("cues").trackArray() ?: JsonArray()
        if (data !== syncedData || scene.editGeneration != syncedGeneration) {
            timeBufs.clear(); nameBufs.clear()
            syncedData = data
            syncedGeneration = scene.editGeneration
        }
        sync(arr)

        val idBase = "${beatId}_${actorId}_${trackIndex}"
        ImGui.text(I18n.get("cobblemon.cinematic_editor.actor_track.cues", arr.size()))
        ImGui.separator()
        ImGui.text("#"); ImGui.sameLine(40f); ImGui.text(I18n.get("cobblemon.cinematic_editor.actor_track.time")); ImGui.sameLine(110f); ImGui.text(I18n.get("cobblemon.cinematic_editor.actor_track.animation_name"))

        var rowToRemove = -1
        for (i in 0 until arr.size()) {
            ImGui.pushID("animrow_${idBase}_$i")
            try {
                ImGui.text(i.toString())
                ImGui.sameLine(40f); ImGui.setNextItemWidth(60f)
                if (inputFiniteFloat("##t", timeBufs[i])) editActorTrack(scene, beatId, actorId, trackIndex, track) { it.get("cues").trackArray()?.trackRow(i)?.addProperty("time", timeBufs[i].get().coerceAtLeast(0f)) }
                ImGui.sameLine(110f); ImGui.setNextItemWidth(200f)
                if (ImGui.inputText("##n", nameBufs[i])) editActorTrack(scene, beatId, actorId, trackIndex, track) { it.get("cues").trackArray()?.trackRow(i)?.addProperty("name", nameBufs[i].get()) }
                ImGui.sameLine()
                if (ImGui.button("\u2715##rm")) rowToRemove = i
            } finally {
                ImGui.popID()
            }
        }

        if (rowToRemove >= 0) {
            editActorTrack(scene, beatId, actorId, trackIndex, track) {
                it.get("cues").trackArray()?.let { cues ->
                    if (rowToRemove in 0 until cues.size()) cues.remove(rowToRemove)
                }
            }
            timeBufs.clear(); nameBufs.clear()
        }

        ImGui.spacing()
        if (ImGui.button(I18n.get("cobblemon.cinematic_editor.actor_track.add_cue") + "##anim_$idBase")) {
            editActorTrack(scene, beatId, actorId, trackIndex, track) { appendCue(it, time = 0f, name = "") }
            timeBufs.clear(); nameBufs.clear()
        }
    }

    private fun sync(arr: JsonArray) {
        while (timeBufs.size < arr.size()) {
            val i = timeBufs.size
            val cue = arr[i].inspectorObject()
            timeBufs += ImFloat(cue?.get("time").inspectorFloat().coerceAtLeast(0f))
            nameBufs += ImString(256).apply {
                inputData.isResizable = true
                set(cue?.get("name").inspectorString())
            }
        }
        while (timeBufs.size > arr.size()) {
            timeBufs.removeAt(timeBufs.size - 1); nameBufs.removeAt(nameBufs.size - 1)
        }
    }

    companion object {
        fun appendCue(data: JsonObject, time: Float, name: String) {
            if (!time.isFinite() || time < 0f) return
            val arr = data.get("cues").trackArray() ?: JsonArray().also { data.add("cues", it) }
            arr.add(JsonObject().apply { addProperty("time", time); addProperty("name", name) })
        }
    }
}
