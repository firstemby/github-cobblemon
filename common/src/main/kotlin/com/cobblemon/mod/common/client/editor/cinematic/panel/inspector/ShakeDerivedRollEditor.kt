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

class ShakeDerivedRollEditor {
    private val ampBuf = ImFloat(0f)
    private val freqBuf = ImFloat(8f)
    private val decayBuf = ImFloat(0f)
    private var syncedData: JsonObject? = null

    fun render(scene: EditorScene, shotId: String, data: JsonObject) {
        if (data !== syncedData) {
            ampBuf.set(data.get("amplitude").inspectorFloat(0f))
            freqBuf.set(data.get("frequency").inspectorFloat(8f))
            decayBuf.set(data.get("decay").inspectorFloat(0f))
            syncedData = data
        }

        ImGui.text(I18n.get("cobblemon.cinematic_editor.motion.amplitude"))
        ImGui.setNextItemWidth(100f)
        if (inputFiniteFloat("##sdrAmp$shotId", ampBuf)) {
            val v = ampBuf.get().coerceAtLeast(0f); ampBuf.set(v)
            commit(scene, shotId, data) { it.addProperty("amplitude", v) }
        }

        ImGui.text(I18n.get("cobblemon.cinematic_editor.motion.frequency"))
        ImGui.setNextItemWidth(100f)
        if (inputFiniteFloat("##sdrFreq$shotId", freqBuf)) {
            val v = freqBuf.get().coerceIn(0f, 60f); freqBuf.set(v)
            commit(scene, shotId, data) { it.addProperty("frequency", v) }
        }

        ImGui.text(I18n.get("cobblemon.cinematic_editor.motion.decay"))
        ImGui.setNextItemWidth(100f)
        if (inputFiniteFloat("##sdrDecay$shotId", decayBuf)) {
            val v = decayBuf.get().coerceAtLeast(0f); decayBuf.set(v)
            commit(scene, shotId, data) { it.addProperty("decay", v) }
        }
    }

    private fun commit(scene: EditorScene, shotId: String, data: JsonObject, mutate: (JsonObject) -> Unit) {
        editInspectorChannel(scene, shotId, ShotChannel.ROLL, data, mutate)
        syncedData = null
    }
}
