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

class LookAlongPathEditor {
    private val epsBuf = ImFloat(1f / 60f)
    private var syncedData: JsonObject? = null

    fun render(scene: EditorScene, shotId: String, data: JsonObject) {
        if (data !== syncedData) {
            epsBuf.set(data.get("epsilon").inspectorFloat(1f / 60f))
            syncedData = data
        }

        ImGui.text(I18n.get("cobblemon.cinematic_editor.motion.epsilon"))
        ImGui.setNextItemWidth(100f)
        if (inputFiniteFloat("##lapEps$shotId", epsBuf)) {
            val v = epsBuf.get().coerceAtLeast(1e-4f); epsBuf.set(v)
            commit(scene, shotId, data) { it.addProperty("epsilon", v) }
        }

    }

    private fun commit(scene: EditorScene, shotId: String, data: JsonObject, mutate: (JsonObject) -> Unit) {
        editInspectorChannel(scene, shotId, ShotChannel.ROTATION, data, mutate)
        syncedData = null
    }
}
