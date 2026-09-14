/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.editor.cinematic.panel.inspector

import com.cobblemon.mod.common.client.editor.cinematic.EditorDiagnostics
import com.cobblemon.mod.common.client.editor.cinematic.EditorWidgets
import com.cobblemon.mod.common.client.editor.cinematic.SceneEditorOverlay
import com.cobblemon.mod.common.client.editor.cinematic.viewport.ViewportGizmoInteraction
import com.cobblemon.mod.common.client.editor.model.cinematic.EditorGrabTarget
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import imgui.ImGui
import net.minecraft.client.resources.language.I18n
import imgui.flag.ImGuiSelectableFlags
import imgui.type.ImFloat
import net.minecraft.client.Minecraft
import net.minecraft.world.phys.Vec3

object SplinePathEditor {
    fun render(data: JsonObject, targetAt: (Int) -> EditorGrabTarget, commit: (JsonObject) -> Unit) =
        render(data, targetAt, commit, waypoints = false)

    fun render(data: JsonObject, targetAt: (Int) -> EditorGrabTarget, commit: (JsonObject) -> Unit, waypoints: Boolean) {
        val duration = ImFloat(data.get("duration").inspectorFloat(5f))
        ImGui.textWrapped(if (waypoints) I18n.get("cobblemon.cinematic_editor.path.time_budget") else I18n.get("cobblemon.cinematic_editor.path.duration"))
        EditorWidgets.fullWidth()
        if (inputFiniteFloat("##pathDuration", duration)) {
            commit(data.deepCopy().apply { addProperty("duration", if (waypoints) duration.get().coerceIn(0.05f, 600f) else duration.get().coerceAtLeast(0.01f)) })
        }
        EditorDiagnostics.item("path/duration")
        val points = data.get("controlPoints")?.takeIf { it.isJsonArray }?.asJsonArray ?: return
        ImGui.separator()
        ImGui.textWrapped(if (waypoints) I18n.get("cobblemon.cinematic_editor.path.waypoints") else I18n.get("cobblemon.cinematic_editor.path.control_points"))
        ImGui.textWrapped(I18n.get("cobblemon.cinematic_editor.path.point_help"))
        for (index in 0 until points.size()) {
            val target = targetAt(index)
            val selected = target == ViewportGizmoInteraction.selected
            if (ImGui.selectable(I18n.get("cobblemon.cinematic_editor.path.point", index + 1) + "##pathPoint$index", selected,
                    ImGuiSelectableFlags.None, ImGui.getContentRegionAvailX(), 0f)) {
                ViewportGizmoInteraction.selectTarget(target, SceneEditorOverlay.selection)
            }
            EditorDiagnostics.item("path/point/$index", expandedSelection = true)
            if (!selected) continue
            ImGui.pushID(index)
            try {
                val point = points[index].inspectorObject()
                for (axis in listOf("x", "y", "z")) {
                    ImGui.text(axis.uppercase())
                    EditorWidgets.fullWidth()
                    val value = ImFloat(point?.get(axis).inspectorFloat())
                    if (inputFiniteFloat("##$axis", value)) {
                        commit(data.deepCopy().apply {
                            val currentPoints = getAsJsonArray("controlPoints")
                            val current = currentPoints[index].inspectorObject() ?: JsonObject().also { currentPoints.set(index, it) }
                            current.addProperty(axis, value.get().toDouble())
                        })
                    }
                    EditorDiagnostics.item("path/point/$index/$axis")
                }
                SetFromCameraButton.render("point$index") { position ->
                    val cameraPosition = Vec3(position.x, position.y, position.z)
                    if (finitePosition(cameraPosition)) {
                        commit(data.deepCopy().apply { getAsJsonArray("controlPoints").set(index, vector(cameraPosition)) })
                    }
                }
                ImGui.beginDisabled(points.size() <= 2)
                try {
                    if (ImGui.button(I18n.get("cobblemon.cinematic_editor.path.remove_point"))) {
                        commit(data.deepCopy().apply { removeControlPoint(this, index) })
                        ViewportGizmoInteraction.reset()
                        break
                    }
                } finally {
                    ImGui.endDisabled()
                }
            } finally {
                ImGui.popID()
            }
        }
        val currentPoints = data.get("controlPoints")?.takeIf { it.isJsonArray }?.asJsonArray ?: return
        ImGui.beginDisabled(waypoints && currentPoints.size() >= 64)
        try {
            if (ImGui.button(I18n.get("cobblemon.cinematic_editor.path.add_at_camera"), ImGui.getContentRegionAvailX(), 0f)) {
                commit(data.deepCopy().apply { appendControlPoint(this, Minecraft.getInstance().gameRenderer.mainCamera.position) })
            }
            EditorDiagnostics.item("path/addAtCamera")
            ImGui.beginDisabled(currentPoints.size() < 2)
            try {
                if (ImGui.button(I18n.get("cobblemon.cinematic_editor.path.extend"), ImGui.getContentRegionAvailX(), 0f)) {
                    extendedPoint(currentPoints)?.let { position ->
                        commit(data.deepCopy().apply { appendControlPoint(this, position) })
                    }
                }
            } finally {
                ImGui.endDisabled()
            }
            EditorDiagnostics.item("path/extend")
        } finally {
            ImGui.endDisabled()
        }
    }

    fun appendControlPoint(data: JsonObject, position: Vec3) {
        if (!finitePosition(position)) return
        val points = data.get("controlPoints")?.takeIf { it.isJsonArray }?.asJsonArray
            ?: JsonArray().also { data.add("controlPoints", it) }
        points.add(vector(position))
    }

    fun removeControlPoint(data: JsonObject, index: Int) {
        val points = data.get("controlPoints")?.takeIf { it.isJsonArray }?.asJsonArray ?: return
        if (points.size() > 2 && index in 0 until points.size()) points.remove(index)
    }

    internal fun extendedPoint(points: JsonArray): Vec3? {
        if (points.size() < 2) return null
        val last = points[points.size() - 1].inspectorObject() ?: return null
        val previous = points[points.size() - 2].inspectorObject() ?: return null
        fun coordinate(axis: String): Double? {
            val a = last.get(axis)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asDouble ?: return null
            val b = previous.get(axis)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asDouble ?: return null
            return (a * 2 - b).takeIf { it.toFloat().isFinite() }
        }
        return Vec3(coordinate("x") ?: return null, coordinate("y") ?: return null, coordinate("z") ?: return null)
    }

    private fun finitePosition(position: Vec3): Boolean =
        position.x.toFloat().isFinite() && position.y.toFloat().isFinite() && position.z.toFloat().isFinite()

    private fun vector(position: Vec3) = JsonObject().apply {
        addProperty("x", position.x); addProperty("y", position.y); addProperty("z", position.z)
    }
}
