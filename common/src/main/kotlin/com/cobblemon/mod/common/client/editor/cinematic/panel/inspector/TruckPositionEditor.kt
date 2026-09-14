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

class TruckPositionEditor {
    private val oxBuf = ImFloat(); private val oyBuf = ImFloat(); private val ozBuf = ImFloat()
    private val axBuf = ImFloat(1f); private val ayBuf = ImFloat(0f); private val azBuf = ImFloat(0f)
    private val velBuf = ImFloat(1f)
    private var syncedData: JsonObject? = null

    fun render(scene: EditorScene, shotId: String, data: JsonObject) {
        val origin = data.get("origin").inspectorObject() ?: JsonObject()
        val axis = data.get("axis").inspectorObject() ?: JsonObject()
        if (data !== syncedData) {
            oxBuf.set(origin.get("x").inspectorFloat(0f)); oyBuf.set(origin.get("y").inspectorFloat(64f)); ozBuf.set(origin.get("z").inspectorFloat(0f))
            axBuf.set(axis.get("x").inspectorFloat(1f)); ayBuf.set(axis.get("y").inspectorFloat(0f)); azBuf.set(axis.get("z").inspectorFloat(0f))
            velBuf.set(data.get("velocity").inspectorFloat(1f))
            syncedData = data
        }

        ImGui.text(I18n.get("cobblemon.cinematic_editor.motion.origin"))
        ImGui.setNextItemWidth(70f); if (inputFiniteFloat("##toX$shotId", oxBuf)) commitVector(scene, shotId, data, "origin", mapOf("x" to oxBuf.get().toDouble()))
        ImGui.sameLine(); ImGui.setNextItemWidth(70f); if (inputFiniteFloat("##toY$shotId", oyBuf)) commitVector(scene, shotId, data, "origin", mapOf("y" to oyBuf.get().toDouble()))
        ImGui.sameLine(); ImGui.setNextItemWidth(70f); if (inputFiniteFloat("##toZ$shotId", ozBuf)) commitVector(scene, shotId, data, "origin", mapOf("z" to ozBuf.get().toDouble()))
        ImGui.sameLine()
        SetFromCameraButton.render("to$shotId") capture@{ cam ->
            if (!cam.x.toFloat().isFinite() || !cam.y.toFloat().isFinite() || !cam.z.toFloat().isFinite()) return@capture
            oxBuf.set(cam.x.toFloat()); oyBuf.set(cam.y.toFloat()); ozBuf.set(cam.z.toFloat())
            commitVector(scene, shotId, data, "origin", mapOf("x" to cam.x, "y" to cam.y, "z" to cam.z))
        }

        ImGui.text(I18n.get("cobblemon.cinematic_editor.motion.axis"))
        ImGui.setNextItemWidth(70f); if (inputFiniteFloat("##taX$shotId", axBuf)) commitVector(scene, shotId, data, "axis", mapOf("x" to axBuf.get().toDouble()))
        ImGui.sameLine(); ImGui.setNextItemWidth(70f); if (inputFiniteFloat("##taY$shotId", ayBuf)) commitVector(scene, shotId, data, "axis", mapOf("y" to ayBuf.get().toDouble()))
        ImGui.sameLine(); ImGui.setNextItemWidth(70f); if (inputFiniteFloat("##taZ$shotId", azBuf)) commitVector(scene, shotId, data, "axis", mapOf("z" to azBuf.get().toDouble()))

        ImGui.text(I18n.get("cobblemon.cinematic_editor.motion.velocity"))
        ImGui.setNextItemWidth(100f)
        if (inputFiniteFloat("##tVel$shotId", velBuf)) {
            commit(scene, shotId, data) { it.addProperty("velocity", velBuf.get()) }
        }
    }

    private fun commit(scene: EditorScene, shotId: String, data: JsonObject, mutate: (JsonObject) -> Unit) {
        syncedData = null
        editInspectorChannel(scene, shotId, ShotChannel.POSITION, data, mutate)
    }

    private fun commitVector(scene: EditorScene, shotId: String, data: JsonObject, key: String, values: Map<String, Double>) {
        syncedData = null
        editInspectorPositionVector(scene, shotId, data, key, values)
    }
}
