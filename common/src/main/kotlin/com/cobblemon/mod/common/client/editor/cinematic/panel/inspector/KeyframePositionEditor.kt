/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.editor.cinematic.panel.inspector

import net.minecraft.client.Minecraft
import com.cobblemon.mod.common.api.cinematic.model.sources.builtin.KeyframePosition
import com.cobblemon.mod.common.client.editor.model.cinematic.EditorScene
import com.cobblemon.mod.common.client.editor.model.cinematic.actions.ShotChannel
import com.cobblemon.mod.common.client.editor.widget.EasingDropdown
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import imgui.ImGui
import net.minecraft.client.resources.language.I18n
import imgui.type.ImFloat
import net.minecraft.world.phys.Vec3

class KeyframePositionEditor {

    private val easingDropdown = EasingDropdown("##kfeasing")
    private val timeBufs = mutableListOf<ImFloat>()
    private val xBufs = mutableListOf<ImFloat>()
    private val yBufs = mutableListOf<ImFloat>()
    private val zBufs = mutableListOf<ImFloat>()
    private var selectedRow: Int = -1
    private var selectionScene: EditorScene? = null
    private var selectionShotId: String? = null
    private var pendingSelection: Int? = null
    private var lastSeenGeneration: Int = -1
    private var lastData: JsonObject? = null

    val currentSelectionRow: Int get() = selectedRow

    fun setSelectedRow(row: Int) { pendingSelection = row }

    internal fun syncSelection(scene: EditorScene, shotId: String, rowCount: Int) {
        if (selectionScene !== scene || selectionShotId != shotId) selectedRow = -1
        selectionScene = scene
        selectionShotId = shotId
        pendingSelection?.let { selectedRow = it }
        pendingSelection = null
        if (selectedRow !in 0 until rowCount) selectedRow = -1
    }

    private fun clearSelection() { selectedRow = -1; pendingSelection = null }

    fun render(
        scene: EditorScene,
        shotId: String,
        data: JsonObject,
        playheadSeconds: Float
    ) {
        val draft = KeyframeChannelDraft(scene, shotId, ShotChannel.POSITION, data)
        val kfArray = data.keyframeRows() ?: JsonArray()

        if (data !== lastData || scene.editGeneration != lastSeenGeneration) {
            lastData = data
            resetBuffers()
            lastSeenGeneration = scene.editGeneration
        }
        syncBuffers(kfArray)
        syncSelection(scene, shotId, kfArray.size())

        ImGui.text(I18n.get("cobblemon.cinematic_editor.keyframes.count", kfArray.size()))
        ImGui.separator()

        ImGui.text("#")
        ImGui.sameLine(40f); ImGui.text(I18n.get("cobblemon.cinematic_editor.keyframes.time"))
        ImGui.sameLine(100f); ImGui.text("x")
        ImGui.sameLine(160f); ImGui.text("y")
        ImGui.sameLine(220f); ImGui.text("z")
        ImGui.sameLine(280f); ImGui.text(I18n.get("cobblemon.cinematic_editor.keyframes.easing"))

        var rowToRemove = -1
        for (i in 0 until kfArray.size()) {
            ImGui.pushID("kfrow$shotId$i")
            try {
                val kf = kfArray[i].inspectorObject()

                if (ImGui.radioButton("##sel", selectedRow == i)) selectedRow = i
                ImGui.sameLine(20f); ImGui.text(i.toString())

                ImGui.sameLine(40f); ImGui.setNextItemWidth(50f)
                if (inputFiniteFloat("##t", timeBufs[i])) {
                    timeBufs[i].set(timeBufs[i].get().coerceAtLeast(0f))
                    draft.editValues(i, mapOf("time" to timeBufs[i].get()))
                }

                ImGui.sameLine(100f); ImGui.setNextItemWidth(50f)
                if (inputFiniteFloat("##x", xBufs[i])) draft.editPosition(i, mapOf("x" to xBufs[i].get().toDouble()))

                ImGui.sameLine(160f); ImGui.setNextItemWidth(50f)
                if (inputFiniteFloat("##y", yBufs[i])) draft.editPosition(i, mapOf("y" to yBufs[i].get().toDouble()))

                ImGui.sameLine(220f); ImGui.setNextItemWidth(50f)
                if (inputFiniteFloat("##z", zBufs[i])) draft.editPosition(i, mapOf("z" to zBufs[i].get().toDouble()))

                ImGui.sameLine(280f); ImGui.setNextItemWidth(100f)
                val currentEasing = kf?.get("easing").inspectorString("linear")
                val newEasing = easingDropdown.render(currentEasing)
                if (newEasing != currentEasing) draft.editRow(i) { it.addProperty("easing", newEasing) }

                ImGui.sameLine()
                SetFromCameraButton.render("kf$i") { cam ->
                    draft.editPosition(i, mapOf("x" to cam.x, "y" to cam.y, "z" to cam.z))
                }

                ImGui.sameLine()
                if (ImGui.button("\u2715##rm")) rowToRemove = i
            } finally {
                ImGui.popID()
            }
        }

        if (rowToRemove >= 0) {
            if (draft.remove(rowToRemove)) {
                clearSelection()
                resetBuffers()
            }
        }

        ImGui.spacing()

        if (ImGui.button(I18n.get("cobblemon.cinematic_editor.keyframes.add_playhead") + "##add_playhead")) addAt(draft, playheadSeconds, evaluateAt(draft.data, playheadSeconds))
        ImGui.sameLine()
        if (ImGui.button(I18n.get("cobblemon.cinematic_editor.keyframes.add_camera") + "##add_camera")) {
            val mc = Minecraft.getInstance()
            val cam = mc.gameRenderer.mainCamera
            addAt(draft, playheadSeconds, cam.position)
        }
        ImGui.sameLine()
        if (ImGui.button(I18n.get("cobblemon.cinematic_editor.keyframes.add_blank") + "##add_blank")) addAt(draft, playheadSeconds, Vec3.ZERO)
    }

    private fun syncBuffers(array: JsonArray) {
        while (timeBufs.size < array.size()) {
            val i = timeBufs.size
            val kf = array[i].inspectorObject()
            val pos = kf?.get("position").inspectorObject()
            timeBufs += ImFloat(kf?.get("time").inspectorFloat().coerceAtLeast(0f))
            xBufs += ImFloat(pos?.get("x").inspectorFloat())
            yBufs += ImFloat(pos?.get("y").inspectorFloat())
            zBufs += ImFloat(pos?.get("z").inspectorFloat())
        }
        while (timeBufs.size > array.size()) {
            timeBufs.removeAt(timeBufs.size - 1); xBufs.removeAt(xBufs.size - 1)
            yBufs.removeAt(yBufs.size - 1); zBufs.removeAt(zBufs.size - 1)
        }
    }

    private fun resetBuffers() { timeBufs.clear(); xBufs.clear(); yBufs.clear(); zBufs.clear() }

    private fun addAt(draft: KeyframeChannelDraft, time: Float, pos: Vec3) {
        if (draft.edit { appendKeyframe(it, time, pos, null) }) {
            clearSelection()
            resetBuffers()
        }
    }

    companion object {
        fun appendKeyframe(data: JsonObject, time: Float, pos: Vec3, easing: String?) {
            if (!time.isFinite() || time < 0f || listOf(pos.x, pos.y, pos.z).any { !it.toFloat().isFinite() }) return
            val arr = data.keyframeRows() ?: JsonArray().also { data.add("keyframes", it) }
            val kf = JsonObject().apply {
                addProperty("time", time)
                add("position", JsonObject().apply {
                    addProperty("x", pos.x); addProperty("y", pos.y); addProperty("z", pos.z)
                })
                if (easing != null) addProperty("easing", easing)
            }
            arr.add(kf)

            val list = arr.toList().sortedBy { it.inspectorObject()?.get("time").inspectorFloat() }
            while (arr.size() > 0) arr.remove(0)
            list.forEach { arr.add(it) }
        }

        fun evaluateAt(data: JsonObject, time: Float): Vec3 {
            if (!time.isFinite()) return Vec3.ZERO
            val arr = data.keyframeRows() ?: return Vec3.ZERO
            if (arr.size() == 0) return Vec3.ZERO
            return try {
                KeyframePosition.fromJson(data).evaluate(time).takeIf {
                    it.x.toFloat().isFinite() && it.y.toFloat().isFinite() && it.z.toFloat().isFinite()
                } ?: Vec3.ZERO
            } catch (_: Exception) {
                Vec3.ZERO
            }
        }
    }
}
