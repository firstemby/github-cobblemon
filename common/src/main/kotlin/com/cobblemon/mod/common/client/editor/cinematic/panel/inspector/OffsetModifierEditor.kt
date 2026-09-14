/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.editor.cinematic.panel.inspector

import com.cobblemon.mod.common.client.editor.model.cinematic.EditorScene
import com.google.gson.JsonObject
import imgui.ImGui
import net.minecraft.client.resources.language.I18n
import imgui.type.ImFloat

class OffsetModifierEditor {
    private val pxBuf = ImFloat(); private val pyBuf = ImFloat(); private val pzBuf = ImFloat()
    private val yawBuf = ImFloat(); private val pitchBuf = ImFloat(); private val fovBuf = ImFloat(); private val rollBuf = ImFloat()
    private var syncedData: JsonObject? = null

    fun render(scene: EditorScene, shotId: String, modifierIndex: Int, data: JsonObject) {
        val pos = data.get("position").inspectorObject() ?: JsonObject()
        if (data !== syncedData) {
            pxBuf.set(pos.get("x").inspectorFloat(0f)); pyBuf.set(pos.get("y").inspectorFloat(0f)); pzBuf.set(pos.get("z").inspectorFloat(0f))
            yawBuf.set(data.get("yaw").inspectorFloat(0f)); pitchBuf.set(data.get("pitch").inspectorFloat(0f))
            fovBuf.set(data.get("fov").inspectorFloat(0f)); rollBuf.set(data.get("roll").inspectorFloat(0f))
            syncedData = data
        }

        ImGui.text(I18n.get("cobblemon.cinematic_editor.motion.position_offset"))
        ImGui.setNextItemWidth(70f); if (inputFiniteFloat("##offPX$modifierIndex", pxBuf)) commitPosition(scene, shotId, modifierIndex, data, "x", pxBuf.get().toDouble())
        ImGui.sameLine(); ImGui.setNextItemWidth(70f); if (inputFiniteFloat("##offPY$modifierIndex", pyBuf)) commitPosition(scene, shotId, modifierIndex, data, "y", pyBuf.get().toDouble())
        ImGui.sameLine(); ImGui.setNextItemWidth(70f); if (inputFiniteFloat("##offPZ$modifierIndex", pzBuf)) commitPosition(scene, shotId, modifierIndex, data, "z", pzBuf.get().toDouble())

        ImGui.text(I18n.get("cobblemon.cinematic_editor.motion.rotation_offset"))
        ImGui.setNextItemWidth(70f); if (inputFiniteFloat("##offYaw$modifierIndex", yawBuf)) commit(scene, shotId, modifierIndex, data) { it.addProperty("yaw", yawBuf.get()) }
        ImGui.sameLine(); ImGui.setNextItemWidth(70f); if (inputFiniteFloat("##offPitch$modifierIndex", pitchBuf)) commit(scene, shotId, modifierIndex, data) { it.addProperty("pitch", pitchBuf.get()) }

        ImGui.text(I18n.get("cobblemon.cinematic_editor.motion.lens_offset"))
        ImGui.setNextItemWidth(70f); if (inputFiniteFloat("##offFov$modifierIndex", fovBuf)) commit(scene, shotId, modifierIndex, data) { it.addProperty("fov", fovBuf.get()) }
        ImGui.sameLine(); ImGui.setNextItemWidth(70f); if (inputFiniteFloat("##offRoll$modifierIndex", rollBuf)) commit(scene, shotId, modifierIndex, data) { it.addProperty("roll", rollBuf.get()) }
    }

    private fun commit(scene: EditorScene, shotId: String, index: Int, data: JsonObject, mutate: (JsonObject) -> Unit) {
        syncedData = null
        editInspectorModifier(scene, shotId, index, data, mutate)
    }

    private fun commitPosition(scene: EditorScene, shotId: String, index: Int, data: JsonObject, axis: String, value: Double) {
        if (!value.toFloat().isFinite()) return
        commit(scene, shotId, index, data) { updated ->
            val position = updated.get("position").inspectorObject() ?: JsonObject().also { updated.add("position", it) }
            position.addProperty(axis, value)
        }
    }
}
