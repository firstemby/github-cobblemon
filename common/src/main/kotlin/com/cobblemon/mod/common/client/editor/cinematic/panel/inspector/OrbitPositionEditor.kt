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
import com.google.gson.JsonArray
import imgui.ImGui
import net.minecraft.client.resources.language.I18n
import imgui.type.ImFloat

class OrbitPositionEditor {
    private val cxBuf = ImFloat(); private val cyBuf = ImFloat(); private val czBuf = ImFloat()
    private val radiusBuf = ImFloat(10f)
    private val angVelBuf = ImFloat(30f)
    private val startAngleBuf = ImFloat(0f)
    private val heightBuf = ImFloat(0f)
    private val paddingBuf = ImFloat(2f)
    private val framingFovBuf = ImFloat(60f)
    private val avoidanceSmoothingBuf = ImFloat(1f)
    private var syncedData: JsonObject? = null

    fun render(scene: EditorScene, shotId: String, data: JsonObject) {
        if (data !== syncedData) {
            val c = data.get("center").inspectorObject() ?: JsonObject()
            cxBuf.set(c.get("x").inspectorFloat(0f)); cyBuf.set(c.get("y").inspectorFloat(64f)); czBuf.set(c.get("z").inspectorFloat(0f))
            radiusBuf.set(data.get("radius").inspectorFloat(10f))
            angVelBuf.set(data.get("angularVelocity").inspectorFloat(30f))
            startAngleBuf.set(data.get("startAngle").inspectorFloat(0f))
            heightBuf.set(data.get("height").inspectorFloat(0f))
            paddingBuf.set(data.get("padding").inspectorFloat(2f))
            framingFovBuf.set(data.get("framingFov").inspectorFloat(60f))
            avoidanceSmoothingBuf.set(data.get("avoidanceSmoothing").inspectorFloat(0f))
            syncedData = data
        }

        val fitArena = data.get("fitArena").inspectorBoolean()
        if (ImGui.checkbox(I18n.get("cobblemon.cinematic_editor.orbit.fit_arena") + "##orbit$shotId", fitArena)) {
            commit(scene, shotId, data) { it.addProperty("fitArena", !fitArena) }
        }
        if (fitArena) {
            val width = data.get("arenaWidth").inspectorFloat(Float.NaN)
            val depth = data.get("arenaDepth").inspectorFloat(Float.NaN)
            ImGui.textWrapped(if (width.isFinite() && depth.isFinite()) I18n.get("cobblemon.cinematic_editor.orbit.arena_size", width, depth)
                else I18n.get("cobblemon.cinematic_editor.orbit.arena_help"))
        }
        ImGui.text(if (fitArena) I18n.get("cobblemon.cinematic_editor.orbit.arena_center") else I18n.get("cobblemon.cinematic_editor.orbit.fallback_center"))
        val coordinateWidth = ((ImGui.getContentRegionAvailX() - 16f) / 3f).coerceAtLeast(1f)
        ImGui.setNextItemWidth(coordinateWidth); if (inputFiniteFloat("##oX$shotId", cxBuf)) commitVector(scene, shotId, data, "center", mapOf("x" to cxBuf.get().toDouble()))
        ImGui.sameLine(); ImGui.setNextItemWidth(coordinateWidth); if (inputFiniteFloat("##oY$shotId", cyBuf)) commitVector(scene, shotId, data, "center", mapOf("y" to cyBuf.get().toDouble()))
        ImGui.sameLine(); ImGui.setNextItemWidth(coordinateWidth); if (inputFiniteFloat("##oZ$shotId", czBuf)) commitVector(scene, shotId, data, "center", mapOf("z" to czBuf.get().toDouble()))
        SetFromCameraButton.render("o$shotId") capture@{ cam ->
            if (!cam.x.toFloat().isFinite() || !cam.y.toFloat().isFinite() || !cam.z.toFloat().isFinite()) return@capture
            cxBuf.set(cam.x.toFloat()); cyBuf.set(cam.y.toFloat()); czBuf.set(cam.z.toFloat())
            commitVector(scene, shotId, data, "center", mapOf("x" to cam.x, "y" to cam.y, "z" to cam.z))
        }

        ImGui.text(I18n.get("cobblemon.cinematic_editor.orbit.fit_actors"))
        val actorIds = data.get("actorIds")?.takeIf { it.isJsonArray }?.asJsonArray
            ?.filter { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.map { it.asString }.orEmpty()
        ActorGroupPicker.render("orbit$shotId", scene, actorIds) { selected ->
            commit(scene, shotId, data) { json -> json.add("actorIds", JsonArray().apply { selected.forEach { add(it) } }) }
        }
        ImGui.text(I18n.get("cobblemon.cinematic_editor.orbit.bearing_actor"))
        val bearing = data.get("bearingActorId").inspectorString().takeIf { it.isNotBlank() }
        ImGui.setNextItemWidth(-1f)
        if (ImGui.beginCombo("##orbitBearing$shotId", bearing ?: I18n.get("cobblemon.cinematic_editor.orbit.world_axes"))) {
            try {
                if (ImGui.selectable(I18n.get("cobblemon.cinematic_editor.orbit.world_axes"), bearing == null)) commit(scene, shotId, data) { it.remove("bearingActorId") }
                scene.actors.keys.sorted().forEach { actor ->
                    if (ImGui.selectable(actor, bearing == actor)) commit(scene, shotId, data) { it.addProperty("bearingActorId", actor) }
                }
            } finally {
                ImGui.endCombo()
            }
        }
        ImGui.text(I18n.get("cobblemon.cinematic_editor.orbit.padding"))
        ImGui.setNextItemWidth(80f)
        if (inputFiniteFloat("##orbitPadding$shotId", paddingBuf)) commit(scene, shotId, data) { it.addProperty("padding", paddingBuf.get().coerceIn(0f, 64f)) }
        ImGui.textWrapped(I18n.get("cobblemon.cinematic_editor.orbit.fov_fallback"))
        ImGui.setNextItemWidth(80f)
        if (inputFiniteFloat("##orbitFraming$shotId", framingFovBuf)) commit(scene, shotId, data) { it.addProperty("framingFov", framingFovBuf.get().coerceIn(10f, 120f)) }
        ImGui.text(I18n.get("cobblemon.cinematic_editor.orbit.radius"))
        ImGui.setNextItemWidth(100f)
        if (inputFiniteFloat("##oRad$shotId", radiusBuf)) {
            applyRadius(scene, shotId, data, radiusBuf.get())
        }

        val avoidBlocks = data.get("avoidBlocks").inspectorBoolean(true)
        if (ImGui.checkbox(I18n.get("cobblemon.cinematic_editor.orbit.avoid_blocks") + "##orbit$shotId", avoidBlocks)) {
            commit(scene, shotId, data) { it.addProperty("avoidBlocks", !avoidBlocks) }
        }
        if (ImGui.isItemHovered()) ImGui.setTooltip(I18n.get("cobblemon.cinematic_editor.orbit.avoid_help"))
        if (avoidBlocks) {
            val smoothAvoidance = (data.get("avoidanceSmoothing").inspectorFloat(0f)) > 0f
            if (ImGui.checkbox(I18n.get("cobblemon.cinematic_editor.orbit.smooth_avoidance") + "##orbit$shotId", smoothAvoidance)) {
                commit(scene, shotId, data) { it.addProperty("avoidanceSmoothing", if (smoothAvoidance) 0f else 1f) }
            }
            if (smoothAvoidance) {
                ImGui.text(I18n.get("cobblemon.cinematic_editor.orbit.curve_size"))
                ImGui.setNextItemWidth(100f)
                if (inputFiniteFloat("##orbitCurve$shotId", avoidanceSmoothingBuf)) {
                    val size = avoidanceSmoothingBuf.get().coerceIn(0.1f, 8f)
                    avoidanceSmoothingBuf.set(size)
                    commit(scene, shotId, data) { it.addProperty("avoidanceSmoothing", size) }
                }
                if (ImGui.isItemHovered()) ImGui.setTooltip(I18n.get("cobblemon.cinematic_editor.orbit.curve_help"))
            }
        }

        ImGui.text(I18n.get("cobblemon.cinematic_editor.orbit.angular_velocity"))
        ImGui.setNextItemWidth(100f)
        if (inputFiniteFloat("##oVel$shotId", angVelBuf)) {
            commit(scene, shotId, data) { it.addProperty("angularVelocity", angVelBuf.get()) }
        }

        val rememberProgress = data.get("rememberProgress").inspectorBoolean()
        if (ImGui.checkbox(I18n.get("cobblemon.cinematic_editor.orbit.remember_progress") + "##orbit$shotId", rememberProgress)) {
            commit(scene, shotId, data) { it.addProperty("rememberProgress", !rememberProgress) }
        }
        if (ImGui.isItemHovered()) ImGui.setTooltip(I18n.get("cobblemon.cinematic_editor.orbit.remember_help"))

        ImGui.text(I18n.get("cobblemon.cinematic_editor.orbit.start_angle"))
        ImGui.setNextItemWidth(100f)
        if (inputFiniteFloat("##oStart$shotId", startAngleBuf)) {
            commit(scene, shotId, data) { it.addProperty("startAngle", startAngleBuf.get()) }
        }

        ImGui.text(I18n.get("cobblemon.cinematic_editor.orbit.height"))
        ImGui.setNextItemWidth(100f)
        if (inputFiniteFloat("##oH$shotId", heightBuf)) {
            commit(scene, shotId, data) { it.addProperty("height", heightBuf.get()) }
        }
    }

    private fun commit(scene: EditorScene, shotId: String, data: JsonObject, mutate: (JsonObject) -> Unit) {
        syncedData = null
        editInspectorChannel(scene, shotId, ShotChannel.POSITION, data, mutate)
    }

    private fun commitVector(scene: EditorScene, shotId: String, data: JsonObject, key: String, values: Map<String, Double>) {
        syncedData = null
        editInspectorPositionVector(scene, shotId, data, key, values)
    }

    internal fun applyRadius(scene: EditorScene, shotId: String, data: JsonObject, radius: Float): Boolean {
        syncedData = null
        if (!radius.isFinite()) return false
        val value = radius.coerceAtLeast(0.01f)
        radiusBuf.set(value)
        return editInspectorChannel(scene, shotId, ShotChannel.POSITION, data) { it.addProperty("radius", value) }
    }
}
