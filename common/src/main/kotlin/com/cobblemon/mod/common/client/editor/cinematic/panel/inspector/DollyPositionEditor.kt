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

class DollyPositionEditor {
    private val fxBuf = ImFloat(); private val fyBuf = ImFloat(); private val fzBuf = ImFloat()
    private val txBuf = ImFloat(); private val tyBuf = ImFloat(); private val tzBuf = ImFloat()
    private val durBuf = ImFloat(5f)
    private var syncedData: JsonObject? = null

    fun render(scene: EditorScene, shotId: String, data: JsonObject) {
        val from = data.get("from").inspectorObject() ?: JsonObject()
        val to = data.get("to").inspectorObject() ?: JsonObject()
        if (data !== syncedData) {
            fxBuf.set(from.get("x").inspectorFloat(0f)); fyBuf.set(from.get("y").inspectorFloat(64f)); fzBuf.set(from.get("z").inspectorFloat(0f))
            txBuf.set(to.get("x").inspectorFloat(10f)); tyBuf.set(to.get("y").inspectorFloat(64f)); tzBuf.set(to.get("z").inspectorFloat(0f))
            durBuf.set(data.get("duration").inspectorFloat(5f))
            syncedData = data
        }

        ImGui.text(I18n.get("cobblemon.cinematic_editor.motion.from"))
        ImGui.setNextItemWidth(70f); if (inputFiniteFloat("##dfX$shotId", fxBuf)) commitVector(scene, shotId, data, "from", mapOf("x" to fxBuf.get().toDouble()))
        ImGui.sameLine(); ImGui.setNextItemWidth(70f); if (inputFiniteFloat("##dfY$shotId", fyBuf)) commitVector(scene, shotId, data, "from", mapOf("y" to fyBuf.get().toDouble()))
        ImGui.sameLine(); ImGui.setNextItemWidth(70f); if (inputFiniteFloat("##dfZ$shotId", fzBuf)) commitVector(scene, shotId, data, "from", mapOf("z" to fzBuf.get().toDouble()))
        ImGui.sameLine()
        SetFromCameraButton.render("df$shotId") capture@{ cam ->
            if (!cam.x.toFloat().isFinite() || !cam.y.toFloat().isFinite() || !cam.z.toFloat().isFinite()) return@capture
            fxBuf.set(cam.x.toFloat()); fyBuf.set(cam.y.toFloat()); fzBuf.set(cam.z.toFloat())
            commitVector(scene, shotId, data, "from", mapOf("x" to cam.x, "y" to cam.y, "z" to cam.z))
        }

        ImGui.text(I18n.get("cobblemon.cinematic_editor.motion.to"))
        ImGui.setNextItemWidth(70f); if (inputFiniteFloat("##dtX$shotId", txBuf)) commitVector(scene, shotId, data, "to", mapOf("x" to txBuf.get().toDouble()))
        ImGui.sameLine(); ImGui.setNextItemWidth(70f); if (inputFiniteFloat("##dtY$shotId", tyBuf)) commitVector(scene, shotId, data, "to", mapOf("y" to tyBuf.get().toDouble()))
        ImGui.sameLine(); ImGui.setNextItemWidth(70f); if (inputFiniteFloat("##dtZ$shotId", tzBuf)) commitVector(scene, shotId, data, "to", mapOf("z" to tzBuf.get().toDouble()))
        ImGui.sameLine()
        SetFromCameraButton.render("dt$shotId") capture@{ cam ->
            if (!cam.x.toFloat().isFinite() || !cam.y.toFloat().isFinite() || !cam.z.toFloat().isFinite()) return@capture
            txBuf.set(cam.x.toFloat()); tyBuf.set(cam.y.toFloat()); tzBuf.set(cam.z.toFloat())
            commitVector(scene, shotId, data, "to", mapOf("x" to cam.x, "y" to cam.y, "z" to cam.z))
        }

        ImGui.text(I18n.get("cobblemon.cinematic_editor.motion.duration"))
        ImGui.setNextItemWidth(100f)
        if (inputFiniteFloat("##dDur$shotId", durBuf)) {
            val v = durBuf.get().coerceAtLeast(0.01f); durBuf.set(v)
            commit(scene, shotId, data) { it.addProperty("duration", v) }
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
