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
import com.cobblemon.mod.common.client.editor.widget.ExpressionLikeField
import com.google.gson.JsonObject
import imgui.ImGui
import net.minecraft.client.resources.language.I18n
import imgui.type.ImFloat

class ExpressionFovEditor {
    private var exprField: ExpressionLikeField? = null
    private val fallbackBuf = ImFloat(70f)
    private var syncedData: JsonObject? = null
    private var lastExpr = ""

    fun render(scene: EditorScene, shotId: String, data: JsonObject) {
        val current = data.get("expression").inspectorString("70.0")
        if (data !== syncedData) {
            exprField = ExpressionLikeField("##expfov$shotId", current)
            fallbackBuf.set(data.get("fallback").inspectorFloat(70f))
            lastExpr = current
            syncedData = data
        }

        ImGui.text(I18n.get("cobblemon.cinematic_editor.motion.fov_expression"))
        val field = exprField!!
        val edited = field.render()
        if (edited != lastExpr) {
            lastExpr = edited
            commit(scene, shotId, data) { it.addProperty("expression", edited) }
        }

        ImGui.text(I18n.get("cobblemon.cinematic_editor.motion.fov_fallback"))
        ImGui.setNextItemWidth(100f)
        if (inputFiniteFloat("##expfovFallback$shotId", fallbackBuf)) {
            val v = fallbackBuf.get().coerceIn(1f, 179f); fallbackBuf.set(v)
            commit(scene, shotId, data) { it.addProperty("fallback", v) }
        }
    }

    private fun commit(scene: EditorScene, shotId: String, data: JsonObject, mutate: (JsonObject) -> Unit) {
        editInspectorChannel(scene, shotId, ShotChannel.FOV, data, mutate)
        syncedData = scene.shots[shotId]?.fovData
    }
}
