/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.editor.cinematic.panel.inspector

import net.minecraft.client.Minecraft
import com.cobblemon.mod.common.client.editor.model.cinematic.EditorScene
import com.cobblemon.mod.common.client.editor.model.cinematic.actions.ShotChannel
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import imgui.ImGui
import net.minecraft.client.resources.language.I18n
import imgui.type.ImFloat

class KeyframeRotationEditor {

    private var lastData: JsonObject? = null
    private var lastSeenGeneration = -1
    private val timeBufs = mutableListOf<ImFloat>()
    private val yawBufs = mutableListOf<ImFloat>()
    private val pitchBufs = mutableListOf<ImFloat>()

    fun render(scene: EditorScene, shotId: String, data: JsonObject) {
        val draft = KeyframeChannelDraft(scene, shotId, ShotChannel.ROTATION, data)
        val kfArray = data.keyframeRows() ?: JsonArray()

        if (data !== lastData || scene.editGeneration != lastSeenGeneration) {
            lastData = data
            lastSeenGeneration = scene.editGeneration
            resetBuffers()
        }
        syncBuffers(kfArray)

        ImGui.text(I18n.get("cobblemon.cinematic_editor.keyframes.count", kfArray.size()))
        ImGui.separator()
        ImGui.text("#")
        ImGui.sameLine(40f); ImGui.text(I18n.get("cobblemon.cinematic_editor.keyframes.time"))
        ImGui.sameLine(120f); ImGui.text(I18n.get("cobblemon.cinematic_editor.keyframes.yaw"))
        ImGui.sameLine(200f); ImGui.text(I18n.get("cobblemon.cinematic_editor.keyframes.pitch"))

        var rowToRemove = -1
        for (i in 0 until kfArray.size()) {
            ImGui.pushID("kfrotrow$shotId$i")
            try {
                ImGui.text(i.toString())

                ImGui.sameLine(40f); ImGui.setNextItemWidth(70f)
                if (inputFiniteFloat("##t", timeBufs[i])) {
                    timeBufs[i].set(timeBufs[i].get().coerceAtLeast(0f))
                    draft.editValues(i, mapOf("time" to timeBufs[i].get()))
                }

                ImGui.sameLine(120f); ImGui.setNextItemWidth(70f)
                if (inputFiniteFloat("##yaw", yawBufs[i])) draft.editValues(i, mapOf("yaw" to yawBufs[i].get()))

                ImGui.sameLine(200f); ImGui.setNextItemWidth(70f)
                if (inputFiniteFloat("##pitch", pitchBufs[i])) draft.editValues(i, mapOf("pitch" to pitchBufs[i].get()))

                ImGui.sameLine()
                SetFromCameraButton.render("kfrot$i") { cam ->
                    draft.editValues(i, mapOf("yaw" to cam.yaw, "pitch" to cam.pitch))
                }

                ImGui.sameLine()
                if (ImGui.button("\u2715##rm")) rowToRemove = i

            } finally {
                ImGui.popID()
            }
        }

        if (rowToRemove >= 0) {
            draft.remove(rowToRemove)
            resetBuffers()
        }

        ImGui.spacing()
        if (ImGui.button(I18n.get("cobblemon.cinematic_editor.keyframes.add_blank") + "##add_blank_kfrot$shotId")) {
            draft.edit { appendKeyframe(it, 0f, 0f, 0f) }
            resetBuffers()
        }
        ImGui.sameLine()
        if (ImGui.button(I18n.get("cobblemon.cinematic_editor.keyframes.add_camera") + "##add_camera_kfrot$shotId")) {
            val mc = Minecraft.getInstance()
            val camera = mc.gameRenderer.mainCamera
            val yaw = camera.yRot
            val pitch = camera.xRot
            draft.edit { appendKeyframe(it, 0f, yaw, pitch) }
            resetBuffers()
        }
    }

    private fun syncBuffers(array: JsonArray) {
        while (timeBufs.size < array.size()) {
            val i = timeBufs.size
            val kf = array[i].inspectorObject()
            timeBufs += ImFloat(kf?.get("time").inspectorFloat().coerceAtLeast(0f))
            yawBufs += ImFloat(kf?.get("yaw").inspectorFloat())
            pitchBufs += ImFloat(kf?.get("pitch").inspectorFloat())
        }
        while (timeBufs.size > array.size()) {
            timeBufs.removeAt(timeBufs.size - 1); yawBufs.removeAt(yawBufs.size - 1); pitchBufs.removeAt(pitchBufs.size - 1)
        }
    }

    private fun resetBuffers() { timeBufs.clear(); yawBufs.clear(); pitchBufs.clear() }

    companion object {
        fun appendKeyframe(data: JsonObject, time: Float, yaw: Float, pitch: Float) {
            if (!time.isFinite() || time < 0f || !yaw.isFinite() || !pitch.isFinite()) return
            val arr = data.keyframeRows() ?: JsonArray().also { data.add("keyframes", it) }
            arr.add(JsonObject().apply {
                addProperty("time", time); addProperty("yaw", yaw); addProperty("pitch", pitch)
            })
            val list = arr.toList().sortedBy { it.inspectorObject()?.get("time").inspectorFloat() }
            while (arr.size() > 0) arr.remove(0)
            list.forEach { arr.add(it) }
        }

    }
}
