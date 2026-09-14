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
import com.cobblemon.mod.common.client.editor.cinematic.EditorWidgets

class FixedPositionEditor {

    private val xBuf = ImFloat()
    private val yBuf = ImFloat()
    private val zBuf = ImFloat()
    private var syncedData: JsonObject? = null

    fun render(scene: EditorScene, shotId: String, data: JsonObject) {
        val pos = data.get("position").inspectorObject() ?: JsonObject()

        if (data !== syncedData) {
            xBuf.set(pos.get("x").inspectorFloat(0f))
            yBuf.set(pos.get("y").inspectorFloat(0f))
            zBuf.set(pos.get("z").inspectorFloat(0f))
            syncedData = data
        }

        ImGui.text(I18n.get("cobblemon.cinematic_editor.look_at.world_position"))
        ImGui.text("X"); EditorWidgets.fullWidth()
        if (inputFiniteFloat("##fpX$shotId", xBuf)) applyValues(scene, shotId, data, mapOf("x" to xBuf.get().toDouble()))
        ImGui.text("Y"); EditorWidgets.fullWidth()
        if (inputFiniteFloat("##fpY$shotId", yBuf)) applyValues(scene, shotId, data, mapOf("y" to yBuf.get().toDouble()))
        ImGui.text("Z"); EditorWidgets.fullWidth()
        if (inputFiniteFloat("##fpZ$shotId", zBuf)) applyValues(scene, shotId, data, mapOf("z" to zBuf.get().toDouble()))

        SetFromCameraButton.render("fp$shotId") { camPose ->
            xBuf.set(camPose.x.toFloat()); yBuf.set(camPose.y.toFloat()); zBuf.set(camPose.z.toFloat())
            applyValues(scene, shotId, data, mapOf("x" to camPose.x, "y" to camPose.y, "z" to camPose.z))
        }
    }

    internal fun applyValues(scene: EditorScene, shotId: String, data: JsonObject, values: Map<String, Double>): Boolean {
        syncedData = null
        if (values.values.any { !it.toFloat().isFinite() }) return false
        return editInspectorChannel(scene, shotId, ShotChannel.POSITION, data) { updated ->
            val pos = updated.get("position").inspectorObject() ?: JsonObject().also { updated.add("position", it) }
            values.forEach { (axis, value) -> pos.addProperty(axis, value) }
        }
    }
}
