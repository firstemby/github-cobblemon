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
import imgui.type.ImBoolean
import imgui.type.ImFloat

class ConstraintModifierEditor {
    private val axes = listOf("minX", "maxX", "minY", "maxY", "minZ", "maxZ")
    private val enabledBufs = axes.associateWith { ImBoolean(false) }
    private val valueBufs = axes.associateWith { ImFloat(0f) }
    private var syncedData: JsonObject? = null

    fun render(scene: EditorScene, shotId: String, modifierIndex: Int, data: JsonObject) {
        if (data !== syncedData) {
            for (k in axes) {
                val hasKey = data.has(k) && !data.get(k).isJsonNull
                enabledBufs[k]!!.set(hasKey)
                valueBufs[k]!!.set(data.get(k).inspectorFloat())
            }
            syncedData = data
        }

        ImGui.text(I18n.get("cobblemon.cinematic_editor.motion.bounds"))
        for (k in axes) {
            val on = enabledBufs[k]!!
            if (ImGui.checkbox(I18n.get("cobblemon.cinematic_editor.motion.bound.$k") + "##$k$modifierIndex", on)) {
                commit(scene, shotId, modifierIndex, data) {
                    if (on.get()) it.addProperty(k, valueBufs[k]!!.get().toDouble())
                    else it.remove(k)
                }
            }
            if (on.get()) {
                ImGui.sameLine(); ImGui.setNextItemWidth(80f)
                if (inputFiniteFloat("##$k$modifierIndex", valueBufs[k]!!)) {
                    commit(scene, shotId, modifierIndex, data) { it.addProperty(k, valueBufs[k]!!.get().toDouble()) }
                }
            }
        }
    }

    private fun commit(scene: EditorScene, shotId: String, index: Int, data: JsonObject, mutate: (JsonObject) -> Unit) {
        syncedData = null
        editInspectorModifier(scene, shotId, index, data, mutate)
    }
}
