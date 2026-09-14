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

class FixedRotationEditor {
    private val yawBuf = ImFloat()
    private val pitchBuf = ImFloat()
    private var syncedData: JsonObject? = null

    fun render(scene: EditorScene, shotId: String, data: JsonObject) {
        if (data !== syncedData) {
            yawBuf.set(data.get("yaw").inspectorFloat(0f))
            pitchBuf.set(data.get("pitch").inspectorFloat(0f))
            syncedData = data
        }

        ImGui.text(I18n.get("cobblemon.cinematic_editor.yaw_degrees")); EditorWidgets.fullWidth()
        if (inputFiniteFloat("##frYaw$shotId", yawBuf)) applyValues(scene, shotId, data, mapOf("yaw" to yawBuf.get()))
        ImGui.text(I18n.get("cobblemon.cinematic_editor.pitch_degrees")); EditorWidgets.fullWidth()
        if (inputFiniteFloat("##frPitch$shotId", pitchBuf)) applyValues(scene, shotId, data, mapOf("pitch" to pitchBuf.get()))
        SetFromCameraButton.render("fr$shotId") { camPose ->
            yawBuf.set(camPose.yaw); pitchBuf.set(camPose.pitch)
            applyValues(scene, shotId, data, mapOf("yaw" to camPose.yaw, "pitch" to camPose.pitch))
        }
    }

    internal fun applyValues(scene: EditorScene, shotId: String, data: JsonObject, values: Map<String, Float>): Boolean {
        syncedData = null
        if (values.values.any { !it.isFinite() }) return false
        return editInspectorChannel(scene, shotId, ShotChannel.ROTATION, data) { updated ->
            values.forEach { (axis, value) -> updated.addProperty(axis, value) }
        }
    }
}
