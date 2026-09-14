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

class ShakeModifierEditor {
    private val posAmp = ImFloat(); private val rotAmp = ImFloat()
    private val freq = ImFloat(); private val decay = ImFloat()
    private var syncedData: JsonObject? = null

    fun render(scene: EditorScene, shotId: String, modifierIndex: Int, data: JsonObject) {
        if (data !== syncedData) {
            posAmp.set(data.get("positionAmplitude").inspectorFloat(0f))
            rotAmp.set(data.get("rotationAmplitude").inspectorFloat(0f))
            freq.set(data.get("frequency").inspectorFloat(8f))
            decay.set(data.get("decay").inspectorFloat(0f))
            syncedData = data
        }

        ImGui.text(I18n.get("cobblemon.cinematic_editor.motion.position_amplitude"))
        ImGui.setNextItemWidth(100f); if (inputFiniteFloat("##shakePos$modifierIndex", posAmp)) {
            val v = posAmp.get().coerceAtLeast(0f); posAmp.set(v); applyField(scene, shotId, modifierIndex, data, "positionAmplitude", v)
        }
        ImGui.text(I18n.get("cobblemon.cinematic_editor.motion.rotation_amplitude"))
        ImGui.setNextItemWidth(100f); if (inputFiniteFloat("##shakeRot$modifierIndex", rotAmp)) {
            val v = rotAmp.get().coerceAtLeast(0f); rotAmp.set(v); applyField(scene, shotId, modifierIndex, data, "rotationAmplitude", v)
        }
        ImGui.text(I18n.get("cobblemon.cinematic_editor.motion.frequency"))
        ImGui.setNextItemWidth(100f); if (inputFiniteFloat("##shakeFreq$modifierIndex", freq)) {
            val v = freq.get().coerceIn(0f, 60f); freq.set(v); applyField(scene, shotId, modifierIndex, data, "frequency", v)
        }
        ImGui.text(I18n.get("cobblemon.cinematic_editor.motion.decay"))
        ImGui.setNextItemWidth(100f); if (inputFiniteFloat("##shakeDecay$modifierIndex", decay)) {
            val v = decay.get().coerceAtLeast(0f); decay.set(v); applyField(scene, shotId, modifierIndex, data, "decay", v)
        }
    }

    internal fun applyField(scene: EditorScene, shotId: String, index: Int, data: JsonObject, key: String, value: Float): Boolean {
        syncedData = null
        if (!value.isFinite()) return false
        val bounded = when (key) {
            "frequency" -> value.coerceIn(0f, 60f)
            "positionAmplitude", "rotationAmplitude", "decay" -> value.coerceAtLeast(0f)
            else -> return false
        }
        return editInspectorModifier(scene, shotId, index, data) { it.addProperty(key, bounded) }
    }
}
