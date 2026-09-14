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

class CranePositionEditor {
    private val pxBuf = ImFloat(); private val pyBuf = ImFloat(); private val pzBuf = ImFloat()
    private val armBuf = ImFloat(10f)
    private val startBuf = ImFloat(0f)
    private val velBuf = ImFloat(30f)
    private val heightBuf = ImFloat(0f)
    private var syncedData: JsonObject? = null

    fun render(scene: EditorScene, shotId: String, data: JsonObject) {
        val pivot = data.get("pivot").inspectorObject() ?: JsonObject()
        if (data !== syncedData) {
            pxBuf.set(pivot.get("x").inspectorFloat(0f)); pyBuf.set(pivot.get("y").inspectorFloat(64f)); pzBuf.set(pivot.get("z").inspectorFloat(0f))
            armBuf.set(data.get("armLength").inspectorFloat(10f))
            startBuf.set(data.get("startAngle").inspectorFloat(0f))
            velBuf.set(data.get("angularVelocity").inspectorFloat(30f))
            heightBuf.set(data.get("height").inspectorFloat(0f))
            syncedData = data
        }

        ImGui.text(I18n.get("cobblemon.cinematic_editor.motion.pivot"))
        ImGui.setNextItemWidth(70f); if (inputFiniteFloat("##cpX$shotId", pxBuf)) commitVector(scene, shotId, data, "pivot", mapOf("x" to pxBuf.get().toDouble()))
        ImGui.sameLine(); ImGui.setNextItemWidth(70f); if (inputFiniteFloat("##cpY$shotId", pyBuf)) commitVector(scene, shotId, data, "pivot", mapOf("y" to pyBuf.get().toDouble()))
        ImGui.sameLine(); ImGui.setNextItemWidth(70f); if (inputFiniteFloat("##cpZ$shotId", pzBuf)) commitVector(scene, shotId, data, "pivot", mapOf("z" to pzBuf.get().toDouble()))
        ImGui.sameLine()
        SetFromCameraButton.render("cp$shotId") capture@{ cam ->
            if (!cam.x.toFloat().isFinite() || !cam.y.toFloat().isFinite() || !cam.z.toFloat().isFinite()) return@capture
            pxBuf.set(cam.x.toFloat()); pyBuf.set(cam.y.toFloat()); pzBuf.set(cam.z.toFloat())
            commitVector(scene, shotId, data, "pivot", mapOf("x" to cam.x, "y" to cam.y, "z" to cam.z))
        }

        ImGui.text(I18n.get("cobblemon.cinematic_editor.motion.arm_length")); ImGui.setNextItemWidth(100f)
        if (inputFiniteFloat("##cArm$shotId", armBuf)) {
            val v = armBuf.get().coerceAtLeast(0.01f); armBuf.set(v)
            commit(scene, shotId, data) { it.addProperty("armLength", v) }
        }
        ImGui.text(I18n.get("cobblemon.cinematic_editor.motion.start_angle")); ImGui.setNextItemWidth(100f)
        if (inputFiniteFloat("##cStart$shotId", startBuf)) commit(scene, shotId, data) { it.addProperty("startAngle", startBuf.get()) }
        ImGui.text(I18n.get("cobblemon.cinematic_editor.motion.angular_velocity")); ImGui.setNextItemWidth(100f)
        if (inputFiniteFloat("##cVel$shotId", velBuf)) commit(scene, shotId, data) { it.addProperty("angularVelocity", velBuf.get()) }
        ImGui.text(I18n.get("cobblemon.cinematic_editor.motion.height")); ImGui.setNextItemWidth(100f)
        if (inputFiniteFloat("##cH$shotId", heightBuf)) commit(scene, shotId, data) { it.addProperty("height", heightBuf.get()) }
    }

    private fun commit(scene: EditorScene, shotId: String, data: JsonObject, mutate: (JsonObject) -> Unit) {
        syncedData = null
        editInspectorChannel(scene, shotId, ShotChannel.POSITION, data, mutate)
    }

    private fun commitVector(scene: EditorScene, shotId: String, data: JsonObject, key: String, values: Map<String, Double>) {
        syncedData = null
        editInspectorPositionVector(scene, shotId, data, key, values)
    }
}
