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
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import imgui.ImGui
import net.minecraft.client.resources.language.I18n
import imgui.type.ImFloat

class KeyframeFovEditor {
    private var lastData: JsonObject? = null
    private var lastSeenGeneration = -1
    private val timeBufs = mutableListOf<ImFloat>()
    private val fovBufs = mutableListOf<ImFloat>()

    fun render(scene: EditorScene, shotId: String, data: JsonObject) {
        val draft = KeyframeChannelDraft(scene, shotId, ShotChannel.FOV, data)
        val kfArray = data.keyframeRows() ?: JsonArray()
        if (data !== lastData || scene.editGeneration != lastSeenGeneration) {
            lastData = data
            lastSeenGeneration = scene.editGeneration
            timeBufs.clear(); fovBufs.clear()
        }
        sync(kfArray)

        ImGui.text(I18n.get("cobblemon.cinematic_editor.keyframes.count", kfArray.size()))
        ImGui.separator()
        ImGui.text("#"); ImGui.sameLine(40f); ImGui.text(I18n.get("cobblemon.cinematic_editor.keyframes.time")); ImGui.sameLine(120f); ImGui.text(I18n.get("cobblemon.cinematic_editor.keyframes.fov"))

        var rowToRemove = -1
        for (i in 0 until kfArray.size()) {
            ImGui.pushID("kffovrow$shotId$i")
            try {
                ImGui.text(i.toString())

                ImGui.sameLine(40f); ImGui.setNextItemWidth(70f)
                if (inputFiniteFloat("##t", timeBufs[i])) {
                    timeBufs[i].set(timeBufs[i].get().coerceAtLeast(0f))
                    draft.editValues(i, mapOf("time" to timeBufs[i].get()))
                }

                ImGui.sameLine(120f); ImGui.setNextItemWidth(70f)
                if (inputFiniteFloat("##fov", fovBufs[i])) {
                    val v = fovBufs[i].get().coerceIn(1f, 179f)
                    fovBufs[i].set(v)
                    draft.editValues(i, mapOf("fov" to v))
                }
                ImGui.sameLine(); if (ImGui.button("\u2715##rm")) rowToRemove = i
            } finally {
                ImGui.popID()
            }
        }

        if (rowToRemove >= 0) {
            draft.remove(rowToRemove)
            timeBufs.clear(); fovBufs.clear()
        }

        ImGui.spacing()
        if (ImGui.button(I18n.get("cobblemon.cinematic_editor.keyframes.add") + "##add_kffov$shotId")) {
            draft.append(JsonObject().apply { addProperty("time", 0f); addProperty("fov", 70f) })
            timeBufs.clear(); fovBufs.clear()
        }
    }

    private fun sync(array: JsonArray) {
        while (timeBufs.size < array.size()) {
            val i = timeBufs.size
            val kf = array[i].inspectorObject()
            timeBufs += ImFloat(kf?.get("time").inspectorFloat().coerceAtLeast(0f))
            fovBufs += ImFloat(kf?.get("fov").inspectorFloat(70f).coerceIn(1f, 179f))
        }
        while (timeBufs.size > array.size()) { timeBufs.removeAt(timeBufs.size - 1); fovBufs.removeAt(fovBufs.size - 1) }
    }
}
