/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.editor.cinematic.panel.inspector

import com.cobblemon.mod.common.client.editor.model.cinematic.EditorScene
import com.cobblemon.mod.common.client.editor.model.cinematic.actions.ShotChannel
import com.google.gson.JsonObject
import imgui.ImGui
import net.minecraft.client.resources.language.I18n
import imgui.type.ImFloat
import imgui.type.ImString

class FollowActorPositionEditor {
    private val actorIdBuf = ImString(64).apply { inputData.isResizable = true }
    private val oxBuf = ImFloat(); private val oyBuf = ImFloat(); private val ozBuf = ImFloat()
    private val fxBuf = ImFloat(); private val fyBuf = ImFloat(); private val fzBuf = ImFloat()
    private var syncedData: JsonObject? = null

    fun render(scene: EditorScene, shotId: String, data: JsonObject) {
        val offset = data.get("offset").inspectorObject() ?: JsonObject()
        val fallback = data.get("fallback").inspectorObject() ?: JsonObject()
        if (data !== syncedData) {
            actorIdBuf.set(data.get("actorId").inspectorString())
            oxBuf.set(offset.get("x").inspectorFloat(0f)); oyBuf.set(offset.get("y").inspectorFloat(2f)); ozBuf.set(offset.get("z").inspectorFloat(-3f))
            fxBuf.set(fallback.get("x").inspectorFloat(0f)); fyBuf.set(fallback.get("y").inspectorFloat(64f)); fzBuf.set(fallback.get("z").inspectorFloat(0f))
            syncedData = data
        }

        ImGui.text(I18n.get("cobblemon.cinematic_editor.motion.actor_id"))
        ImGui.setNextItemWidth(200f)
        if (ImGui.inputText("##faActor$shotId", actorIdBuf)) {
            commit(scene, shotId, data) { it.addProperty("actorId", actorIdBuf.get()) }
        }

        ImGui.text(I18n.get("cobblemon.cinematic_editor.motion.world_offset"))
        ImGui.setNextItemWidth(70f); if (inputFiniteFloat("##faoX$shotId", oxBuf)) commitVector(scene, shotId, data, "offset", mapOf("x" to oxBuf.get().toDouble()))
        ImGui.sameLine(); ImGui.setNextItemWidth(70f); if (inputFiniteFloat("##faoY$shotId", oyBuf)) commitVector(scene, shotId, data, "offset", mapOf("y" to oyBuf.get().toDouble()))
        ImGui.sameLine(); ImGui.setNextItemWidth(70f); if (inputFiniteFloat("##faoZ$shotId", ozBuf)) commitVector(scene, shotId, data, "offset", mapOf("z" to ozBuf.get().toDouble()))

        ImGui.text(I18n.get("cobblemon.cinematic_editor.motion.fallback"))
        ImGui.setNextItemWidth(70f); if (inputFiniteFloat("##fafX$shotId", fxBuf)) commitVector(scene, shotId, data, "fallback", mapOf("x" to fxBuf.get().toDouble()))
        ImGui.sameLine(); ImGui.setNextItemWidth(70f); if (inputFiniteFloat("##fafY$shotId", fyBuf)) commitVector(scene, shotId, data, "fallback", mapOf("y" to fyBuf.get().toDouble()))
        ImGui.sameLine(); ImGui.setNextItemWidth(70f); if (inputFiniteFloat("##fafZ$shotId", fzBuf)) commitVector(scene, shotId, data, "fallback", mapOf("z" to fzBuf.get().toDouble()))
        ImGui.sameLine()
        SetFromCameraButton.render("faf$shotId") capture@{ cam ->
            if (!cam.x.toFloat().isFinite() || !cam.y.toFloat().isFinite() || !cam.z.toFloat().isFinite()) return@capture
            fxBuf.set(cam.x.toFloat()); fyBuf.set(cam.y.toFloat()); fzBuf.set(cam.z.toFloat())
            commitVector(scene, shotId, data, "fallback", mapOf("x" to cam.x, "y" to cam.y, "z" to cam.z))
        }
    }

    private fun commit(scene: EditorScene, shotId: String, data: JsonObject, mutate: (JsonObject) -> Unit) {
        editInspectorChannel(scene, shotId, ShotChannel.POSITION, data, mutate)
        syncedData = null
    }

    private fun commitVector(scene: EditorScene, shotId: String, data: JsonObject, key: String, values: Map<String, Double>) {
        syncedData = null
        editInspectorPositionVector(scene, shotId, data, key, values)
    }
}
