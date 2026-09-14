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

class FixedRollEditor {
    private val buf = ImFloat(0f)
    private var syncedData: JsonObject? = null

    fun render(scene: EditorScene, shotId: String, data: JsonObject) {
        if (data !== syncedData) { buf.set(data.get("value").inspectorFloat(0f)); syncedData = data }

        ImGui.text(I18n.get("cobblemon.cinematic_editor.roll_degrees"))
        ImGui.setNextItemWidth(100f)
        if (inputFiniteFloat("##froll$shotId", buf)) { applyValue(scene, shotId, data, buf.get()) }
    }

    internal fun applyValue(scene: EditorScene, shotId: String, data: JsonObject, value: Float): Boolean {
        syncedData = null
        if (!value.isFinite()) return false
        buf.set(value)
        return editInspectorChannel(scene, shotId, ShotChannel.ROLL, data) { it.addProperty("value", buf.get()) }
    }
}
