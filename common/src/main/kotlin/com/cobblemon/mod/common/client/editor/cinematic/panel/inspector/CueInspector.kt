/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.editor.cinematic.panel.inspector

import com.cobblemon.mod.common.api.cinematic.model.SceneCue
import com.cobblemon.mod.common.client.editor.cinematic.EditorDiagnostics
import com.cobblemon.mod.common.client.editor.cinematic.EditorWidgets
import com.cobblemon.mod.common.client.editor.cinematic.SceneEditorOverlay
import com.cobblemon.mod.common.client.editor.model.cinematic.EditorBeat
import com.cobblemon.mod.common.client.editor.model.cinematic.EditorScene
import com.cobblemon.mod.common.client.editor.model.cinematic.actions.ChangeBeatProperty
import imgui.ImGui
import net.minecraft.client.resources.language.I18n
import imgui.type.ImFloat
import imgui.type.ImString

class CueInspector {
    private data class Fields(val id: ImString, val time: ImFloat)
    private var sceneRef: EditorScene? = null
    private var beatRef: EditorBeat? = null
    private var generation = -1
    private var fields = emptyList<Fields>()

    fun render(scene: EditorScene, beat: EditorBeat) {
        if (sceneRef !== scene || beatRef !== beat || generation != scene.editGeneration) {
            sceneRef = scene
            beatRef = beat
            generation = scene.editGeneration
            fields = beat.cues.map { Fields(idBuffer(it.id), ImFloat(it.time)) }
        }
        ImGui.textWrapped(I18n.get("cobblemon.cinematic_editor.cue.help"))
        if (ImGui.button(I18n.get("cobblemon.cinematic_editor.cue.add"), ImGui.getContentRegionAvailX(), 0f)) {
            addCue(scene, beat, SceneEditorOverlay.currentPlayheadSeconds)
        }
        EditorDiagnostics.item("cues/add")
        val cues = beat.cues.toList()
        for ((index, cue) in cues.withIndex()) {
            val input = fields.getOrNull(index) ?: continue
            ImGui.pushID("cue/$index")
            try {
                ImGui.separator()
                ImGui.textWrapped(cue.id)
                ImGui.text(I18n.get("cobblemon.cinematic_editor.cue.id"))
                EditorWidgets.fullWidth()
                ImGui.inputText("##id", input.id)
                EditorDiagnostics.item("cue/$index/id")
                ImGui.text(I18n.get("cobblemon.cinematic_editor.cue.time"))
                EditorWidgets.fullWidth()
                inputFiniteFloat("##time", input.time)
                EditorDiagnostics.item("cue/$index/time")
                val id = input.id.get().trim()
                val time = input.time.get()
                val duplicate = scene.beats.values.any { candidate ->
                    candidate.cues.withIndex().any { (otherIndex, other) ->
                        (candidate !== beat || otherIndex != index) && other.id == id
                    }
                }
                val issue = when {
                    id.isBlank() -> I18n.get("cobblemon.cinematic_editor.cue.id_required")
                    id.length > 128 -> I18n.get("cobblemon.cinematic_editor.cue.id_too_long")
                    duplicate -> I18n.get("cobblemon.cinematic_editor.cue.id_duplicate")
                    !time.isFinite() || time < 0f -> I18n.get("cobblemon.cinematic_editor.cue.time_invalid")
                    beat.duration != null && time > beat.duration!! -> I18n.get("cobblemon.cinematic_editor.cue.time_after_beat")
                    else -> null
                }
                ImGui.beginDisabled(issue != null)
                try {
                    if (ImGui.button(I18n.get("cobblemon.cinematic_editor.cue.apply")) && issue == null) {
                        applyCue(scene, beat, index, id, time)
                    }
                    EditorDiagnostics.item("cue/$index/apply")
                } finally {
                    ImGui.endDisabled()
                }
                EditorWidgets.nextInline(EditorWidgets.buttonWidth(I18n.get("cobblemon.cinematic_editor.cue.seek")))
                if (ImGui.button(I18n.get("cobblemon.cinematic_editor.cue.seek"))) SceneEditorOverlay.seek(cue.time)
                EditorDiagnostics.item("cue/$index/seek")
                EditorWidgets.nextInline(EditorWidgets.buttonWidth(I18n.get("cobblemon.cinematic_editor.cue.remove")))
                val remove = ImGui.button(I18n.get("cobblemon.cinematic_editor.cue.remove"))
                EditorDiagnostics.item("cue/$index/remove")
                if (issue != null) ImGui.textWrapped(issue)
                if (remove) { update(scene, beat, beat.cues.filterIndexed { i, _ -> i != index }); break }
            } finally {
                ImGui.popID()
            }
        }
    }

    internal fun idBuffer(value: String) = ImString(129).apply {
        inputData.isResizable = true
        set(value)
    }

    internal fun addCue(scene: EditorScene, beat: EditorBeat, playhead: Float): Boolean {
        if (!playhead.isFinite()) return false
        val duration = beat.duration ?: Float.MAX_VALUE
        if (!duration.isFinite() || duration < 0f) return false
        val used = scene.beats.values.flatMap { it.cues }.map { it.id }.toSet()
        val id = generateSequence(1) { it + 1 }.map { "cue_$it" }.first { it !in used }
        return update(scene, beat, beat.cues + SceneCue(id, playhead.coerceIn(0f, duration)))
    }

    internal fun applyCue(scene: EditorScene, beat: EditorBeat, index: Int, id: String, time: Float): Boolean {
        if (index !in beat.cues.indices || id.isBlank() || id.length > 128 || !time.isFinite() || time < 0f) return false
        if (beat.duration?.let { !it.isFinite() || time > it } == true) return false
        if (scene.beats.values.any { candidate -> candidate.cues.withIndex().any { (otherIndex, other) ->
                (candidate !== beat || otherIndex != index) && other.id == id
            } }) return false
        return update(scene, beat, beat.cues.mapIndexed { i, old -> if (i == index) SceneCue(id, time) else old })
    }

    private fun update(scene: EditorScene, beat: EditorBeat, cues: List<SceneCue>): Boolean {
        if (scene.beats[beat.id] !== beat || cues == beat.cues) return false
        scene.execute(ChangeBeatProperty(beat.id, { b, value -> b.cues = value }, beat.cues, cues))
        return true
    }
}
