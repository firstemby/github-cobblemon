/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */


package com.cobblemon.mod.common.client.editor.cinematic.panel.inspector.actortrack

import com.cobblemon.mod.common.client.editor.cinematic.EditorWidgets
import com.cobblemon.mod.common.client.editor.cinematic.EditorRoutePreview
import com.cobblemon.mod.common.client.editor.cinematic.SceneEditorOverlay
import com.cobblemon.mod.common.client.editor.cinematic.panel.inspector.SplinePathEditor
import com.cobblemon.mod.common.client.editor.cinematic.panel.inspector.inputFiniteFloat
import com.cobblemon.mod.common.client.editor.cinematic.panel.inspector.inspectorFloat
import com.cobblemon.mod.common.client.editor.cinematic.panel.inspector.inspectorString
import com.cobblemon.mod.common.client.editor.model.EditorTrack
import com.cobblemon.mod.common.client.editor.model.cinematic.ActorSplinePointTarget
import com.cobblemon.mod.common.client.editor.model.cinematic.EditorScene
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import imgui.ImGui
import net.minecraft.client.resources.language.I18n
import imgui.type.ImFloat
import net.minecraft.client.Minecraft
import net.minecraft.world.level.ClipContext
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3

class WaypointPathfindingEditor {
    fun render(scene: EditorScene, beatId: String, actorId: String, trackIndex: Int, track: EditorTrack) {
        fun commit(after: JsonObject) {
            editActorTrack(scene, beatId, actorId, trackIndex, track) { updated ->
                updated.keySet().toList().forEach { updated.remove(it) }
                after.entrySet().forEach { (key, value) -> updated.add(key, value.deepCopy()) }
            }
        }
        ImGui.textWrapped(I18n.get("cobblemon.cinematic_editor.actor_track.navigation_help"))
        val matches = EditorRoutePreview.matches(scene, beatId, actorId, trackIndex)
        ImGui.beginDisabled(matches && EditorRoutePreview.pending)
        try {
            if (ImGui.button(I18n.get("cobblemon.cinematic_editor.actor_track.compute_route"), ImGui.getContentRegionAvailX(), 0f)) {
                EditorRoutePreview.compute(scene, beatId, actorId, trackIndex)
            }
        } finally {
            ImGui.endDisabled()
        }
        if (matches && EditorRoutePreview.message.isNotEmpty()) ImGui.textWrapped(EditorRoutePreview.message)
        EditorRoutePreview.result(scene, beatId, actorId, trackIndex)?.let { route ->
            ImGui.textWrapped(I18n.get(if (route.finished) "cobblemon.cinematic_editor.actor_track.arrived" else "cobblemon.cinematic_editor.actor_track.partial", "%.2f".format(route.seconds), route.jumps))
            val beatDuration = scene.beats[beatId]?.duration
            if (beatDuration != null && route.seconds > beatDuration) ImGui.textWrapped(I18n.get("cobblemon.cinematic_editor.actor_track.exceeds_beat", "%.2f".format(beatDuration)))
            ImGui.textWrapped(I18n.get("cobblemon.cinematic_editor.actor_track.route_legend"))
            if (ImGui.button(I18n.get("cobblemon.cinematic_editor.actor_track.inspect_start"), ImGui.getContentRegionAvailX(), 0f)) SceneEditorOverlay.seek(0f)
            ImGui.textWrapped(I18n.get("cobblemon.cinematic_editor.actor_track.scrub_help"))
        }
        ImGui.text(I18n.get("cobblemon.cinematic_editor.actor_track.travel_mode"))
        EditorWidgets.fullWidth()
        val mode = track.data.get("travelMode").inspectorString("auto")
        if (ImGui.beginCombo("##travelMode", I18n.get("cobblemon.cinematic_editor.actor_track.travel.$mode"))) {
            try {
                for (option in listOf("auto", "ground", "flight")) {
                    if (ImGui.selectable(I18n.get("cobblemon.cinematic_editor.actor_track.travel.$option") + "###$option", option == mode) && option != mode) commit(track.data.deepCopy().apply { addProperty("travelMode", option) })
                }
            } finally {
                ImGui.endCombo()
            }
        }
        ImGui.textWrapped(I18n.get("cobblemon.cinematic_editor.actor_track.speed_multiplier"))
        EditorWidgets.fullWidth()
        val speed = ImFloat(track.data.get("speedMultiplier").inspectorFloat(1f).coerceIn(0.1f, 4f))
        if (inputFiniteFloat("##waypointSpeed", speed)) {
            commit(track.data.deepCopy().apply { addProperty("speedMultiplier", speed.get().coerceIn(0.1f, 4f)) })
        }
        SplinePathEditor.render(track.data, { ActorSplinePointTarget(beatId, actorId, trackIndex, it) }, ::commit, waypoints = true)
        val groundPoint = lookedAtGround()
        ImGui.beginDisabled(groundPoint == null || (track.data.get("controlPoints").trackArray()?.size() ?: 0) >= 64)
        try {
            if (ImGui.button(I18n.get("cobblemon.cinematic_editor.actor_track.add_on_surface"), ImGui.getContentRegionAvailX(), 0f) && groundPoint != null) {
                commit(track.data.deepCopy().apply { SplinePathEditor.appendControlPoint(this, groundPoint) })
            }
        } finally {
            ImGui.endDisabled()
        }
        if (ImGui.collapsingHeader(I18n.get("cobblemon.cinematic_editor.actor_track.path_help"))) {
            ImGui.textWrapped(I18n.get("cobblemon.cinematic_editor.actor_track.waypoint_help"))
            ImGui.textWrapped(I18n.get("cobblemon.cinematic_editor.actor_track.preview_help"))
        }
    }

    companion object {
        fun placeInitialPoints(data: JsonObject) {
            val minecraft = Minecraft.getInstance()
            val start = lookedAtGround() ?: minecraft.player?.position() ?: minecraft.gameRenderer.mainCamera.position
            data.add("controlPoints", JsonArray())
            SplinePathEditor.appendControlPoint(data, start)
            SplinePathEditor.appendControlPoint(data, start.add(4.0, 0.0, 0.0))
        }

        private fun lookedAtGround(): Vec3? {
            val minecraft = Minecraft.getInstance()
            val level = minecraft.level ?: return null
            val player = minecraft.player ?: return null
            val camera = minecraft.gameRenderer.mainCamera
            val direction = camera.lookVector
            val hit = level.clip(ClipContext(camera.position,
                camera.position.add(direction.x() * 32.0, direction.y() * 32.0, direction.z() * 32.0),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player))
            return hit.location.takeIf { hit.type == HitResult.Type.BLOCK && hit.direction.stepY == 1 }
        }
    }
}
