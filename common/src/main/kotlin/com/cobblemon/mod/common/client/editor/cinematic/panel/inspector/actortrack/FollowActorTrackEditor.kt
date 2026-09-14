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
import com.google.gson.JsonObject
import imgui.ImGui
import net.minecraft.client.resources.language.I18n
import imgui.type.ImFloat
import imgui.type.ImString

class FollowActorTrackEditor {
    private val targetBuf = ImString(64).apply { inputData.isResizable = true }
    private val oxBuf = ImFloat(); private val oyBuf = ImFloat(); private val ozBuf = ImFloat()
    private val lagBuf = ImFloat(0f)
    private var syncedData: JsonObject? = null
    private var syncedGeneration = -1

    fun render(scene: EditorScene, beatId: String, actorId: String, trackIndex: Int, track: EditorTrack) {
        val data = track.data
        val offset = data.get("offset").inspectorObject()
        if (data !== syncedData || scene.editGeneration != syncedGeneration) {
            targetBuf.set(data.get("targetActorId").inspectorString())
            oxBuf.set(offset?.get("x").inspectorFloat(0f))
            oyBuf.set(offset?.get("y").inspectorFloat(0f))
            ozBuf.set(offset?.get("z").inspectorFloat(0f))
            lagBuf.set(data.get("lag").inspectorFloat().coerceAtLeast(0f))
            syncedData = data
            syncedGeneration = scene.editGeneration
        }

        val id = "${beatId}_${actorId}_${trackIndex}"
        ImGui.text(I18n.get("cobblemon.cinematic_editor.actor_track.target_actor")); ImGui.setNextItemWidth(180f)
        if (ImGui.inputText("##fatTarget_$id", targetBuf)) editActorTrack(scene, beatId, actorId, trackIndex, track) { it.addProperty("targetActorId", targetBuf.get()) }

        ImGui.text(I18n.get("cobblemon.cinematic_editor.actor_track.offset"))
        ImGui.setNextItemWidth(60f); if (inputFiniteFloat("##fatOX_$id", oxBuf)) editActorTrackVector(scene, beatId, actorId, trackIndex, track, "offset", mapOf("x" to oxBuf.get().toDouble()))
        ImGui.sameLine(); ImGui.setNextItemWidth(60f); if (inputFiniteFloat("##fatOY_$id", oyBuf)) editActorTrackVector(scene, beatId, actorId, trackIndex, track, "offset", mapOf("y" to oyBuf.get().toDouble()))
        ImGui.sameLine(); ImGui.setNextItemWidth(60f); if (inputFiniteFloat("##fatOZ_$id", ozBuf)) editActorTrackVector(scene, beatId, actorId, trackIndex, track, "offset", mapOf("z" to ozBuf.get().toDouble()))

        ImGui.text(I18n.get("cobblemon.cinematic_editor.actor_track.lag")); ImGui.setNextItemWidth(80f)
        if (inputFiniteFloat("##fatLag_$id", lagBuf)) {
            val v = lagBuf.get().coerceAtLeast(0f); lagBuf.set(v)
            editActorTrack(scene, beatId, actorId, trackIndex, track) { it.addProperty("lag", v) }
        }
    }

}
