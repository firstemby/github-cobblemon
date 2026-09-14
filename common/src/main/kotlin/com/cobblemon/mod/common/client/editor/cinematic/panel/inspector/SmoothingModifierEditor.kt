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

class SmoothingModifierEditor {
    private val posBuf = FloatArray(1)
    private val rotBuf = FloatArray(1)
    private val fovBuf = FloatArray(1)
    private var syncedData: JsonObject? = null

    fun render(scene: EditorScene, shotId: String, modifierIndex: Int, data: JsonObject) {
        if (data !== syncedData) {
            posBuf[0] = data.get("position").inspectorFloat(0f).coerceIn(0f, 1f)
            rotBuf[0] = data.get("rotation").inspectorFloat(0f).coerceIn(0f, 1f)
            fovBuf[0] = data.get("fov").inspectorFloat(0f).coerceIn(0f, 1f)
            syncedData = data
        }

        ImGui.text(I18n.get("cobblemon.cinematic_editor.motion.smoothing"))
        ImGui.setNextItemWidth(100f)
        if (sliderFiniteFloat(I18n.get("cobblemon.cinematic_editor.position") + "##pos_sm$modifierIndex", posBuf, 0f, 1f)) commit(scene, shotId, modifierIndex, data, "position", posBuf[0])
        ImGui.setNextItemWidth(100f)
        if (sliderFiniteFloat(I18n.get("cobblemon.cinematic_editor.rotation") + "##rot_sm$modifierIndex", rotBuf, 0f, 1f)) commit(scene, shotId, modifierIndex, data, "rotation", rotBuf[0])
        ImGui.setNextItemWidth(100f)
        if (sliderFiniteFloat(I18n.get("cobblemon.cinematic_editor.fov") + "##fov_sm$modifierIndex", fovBuf, 0f, 1f)) commit(scene, shotId, modifierIndex, data, "fov", fovBuf[0])
    }

    private fun commit(scene: EditorScene, shotId: String, index: Int, data: JsonObject, key: String, value: Float) {
        syncedData = null
        if (!value.isFinite()) return
        editInspectorModifier(scene, shotId, index, data) { it.addProperty(key, value.coerceIn(0f, 1f)) }
    }
}
