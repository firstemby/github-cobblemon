/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.editor.cinematic.overlay

import com.cobblemon.mod.common.api.camera.model.CoordinateValue
import com.cobblemon.mod.common.api.camera.model.PositionExpression
import com.cobblemon.mod.common.api.cinematic.model.tracks.builtin.KeyframeMovement
import com.cobblemon.mod.common.api.cinematic.model.orderedKeyframes
import com.cobblemon.mod.common.api.cinematic.model.tracks.builtin.LookAtTrack
import com.cobblemon.mod.common.api.cinematic.model.tracks.builtin.WaypointPathfinding
import com.cobblemon.mod.common.entity.PoseType
import kotlin.math.sin
import kotlin.math.cos
import com.cobblemon.mod.common.client.editor.cinematic.*
import com.cobblemon.mod.common.client.editor.cinematic.viewport.ViewportGizmoInteraction
import com.cobblemon.mod.common.client.editor.model.cinematic.ActorKeyframeTarget
import com.cobblemon.mod.common.client.editor.model.cinematic.ActorPositionTarget
import com.cobblemon.mod.common.client.editor.model.cinematic.ActorSplinePointTarget
import com.cobblemon.mod.common.client.imgui.CobblemonImGui
import com.cobblemon.mod.common.client.render.cinematic.CinematicWorldRenderer
import com.cobblemon.mod.common.platform.events.PlatformEvents
import com.cobblemon.mod.common.platform.events.RenderEvent
import net.minecraft.world.phys.Vec3

object ActorOverlays {
    private val fluidAndFlightPoses = setOf(PoseType.FLY, PoseType.HOVER, PoseType.SWIM, PoseType.FLOAT)
    init {
        PlatformEvents.RENDER.subscribe { event ->
            if (event.stage != RenderEvent.Stage.TRANSLUCENT || !CobblemonImGui.isActive() || !SceneEditorOverlay.viewState.actors) return@subscribe
            val scene = SceneEditorOverlay.activeScene ?: return@subscribe
            val beatId = SceneEditorOverlay.selection.getSelectedBeatId() ?: scene.startBeatId
            val beat = scene.beats[beatId] ?: return@subscribe
            val time = SceneEditorOverlay.currentPlayheadSeconds
            val handles = SceneEditorOverlay.viewState.keyframesAndGizmos
            beat.actorDirections.forEach { (actorId, direction) ->
                var position = direction.startPose?.position?.literalPosition()
                var yaw = direction.startPose?.rotation?.yaw ?: 0f
                direction.tracks.forEachIndexed { trackIndex, track ->
                    try {
                        when (track.trackType) {
                            "pathfinding" -> {
                                val path = WaypointPathfinding.fromJson(track.data)
                                position = path.controlPoints.first()
                                path.controlPoints.zipWithNext().forEach { (a, b) -> line(a, b, EditorTheme.LOOK_AT_GREY) }
                                EditorRoutePreview.result(scene, beatId, actorId, trackIndex)?.let { route ->
                                    for (index in 1 until route.frames.size) {
                                        val a = route.frames[index - 1]
                                        val b = route.frames[index]
                                        val color = when {
                                            b.pose in fluidAndFlightPoses -> EditorTheme.SMOOTHING
                                            !b.onGround -> EditorTheme.PATH_ORANGE
                                            else -> EditorTheme.ACTOR
                                        }
                                        line(a.position.add(0.0, 0.06, 0.0), b.position.add(0.0, 0.06, 0.0), color)
                                    }
                                    route.searchNodes.zipWithNext().forEach { (a, b) -> line(a.add(0.0, 0.1, 0.0), b.add(0.0, 0.1, 0.0), EditorTheme.MODIFIER) }
                                    if (!route.finished) route.target?.let { CinematicWorldRenderer.queueDiamond(it, 0.25f, EditorTheme.MODIFIER) }
                                    route.frames.getOrNull((time * 20).toInt().coerceIn(0, (route.frames.size - 1).coerceAtLeast(0)))?.let {
                                        position = it.position
                                        yaw = it.yaw
                                    }
                                }
                                if (handles) path.controlPoints.forEachIndexed { index, point ->
                                    val selected = ViewportGizmoInteraction.selected == ActorSplinePointTarget(beatId, actorId, trackIndex, index)
                                    CinematicWorldRenderer.queueDiamond(point, 0.18f,
                                        if (selected) EditorTheme.PATH_SELECTED_BLUE else EditorTheme.ACTOR)
                                }
                            }
                            "spline_movement" -> {
                                val path = EditorPathCache.actor("$beatId/$actorId/$trackIndex", track.data)
                                position = path.evaluate(time)
                                yaw = path.facingAt(time)?.first ?: yaw
                                val steps = (path.controlPoints.size * 24).coerceIn(24, 1024)
                                var previous = path.evaluate(0f)
                                for (step in 1..steps) {
                                    val point = path.evaluate(path.duration * step / steps)
                                    line(previous, point, EditorTheme.ACTOR)
                                    previous = point
                                }
                                if (handles) path.controlPoints.forEachIndexed { index, point ->
                                    val selected = ViewportGizmoInteraction.selected == ActorSplinePointTarget(beatId, actorId, trackIndex, index)
                                    CinematicWorldRenderer.queueDiamond(point, 0.18f,
                                        if (selected) EditorTheme.PATH_SELECTED_BLUE else EditorTheme.ACTOR)
                                }
                                for (step in 0..10) CinematicWorldRenderer.queueDiamond(path.evaluate(path.duration * step / 10), 0.06f, EditorTheme.PATH_ORANGE)
                            }
                            "keyframe_movement" -> {
                                val path = KeyframeMovement.fromJson(track.data)
                                position = path.evaluate(time)
                                orderedKeyframes(path.keyframes) { it.time }
                                    .zipWithNext()
                                    .forEach { (a, b) -> line(a.position, b.position, EditorTheme.ACTOR) }
                                if (handles) path.keyframes.forEachIndexed { index, point ->
                                    val selected = ViewportGizmoInteraction.selected == ActorKeyframeTarget(beatId, actorId, trackIndex, index)
                                    CinematicWorldRenderer.queueDiamond(point.position, 0.15f,
                                        if (selected) EditorTheme.PATH_SELECTED_BLUE else EditorTheme.ACTOR)
                                }
                            }
                        }
                    } catch (_: Exception) {
                        // Incomplete property edits must not interrupt the world render.
                    }
                }
                position?.let { point ->
                    direction.tracks.lastOrNull { it.trackType == "look_at" }?.let { track ->
                        try { yaw = LookAtTrack.fromJson(track.data).computeYRot(point) } catch (_: Exception) { }
                    }
                }
                if (SceneEditorOverlay.playState.mode == EditorPlayMode.EDIT && direction.visible != false) position?.let { point ->
                    CinematicWorldRenderer.queueCuboid(point.add(-0.4, 0.0, -0.4), point.add(0.4, 1.8, 0.4), EditorTheme.ACTOR)
                    val angle = Math.toRadians(yaw.toDouble())
                    val start = point.add(0.0, 0.9, 0.0)
                    line(start, start.add(-sin(angle), 0.0, cos(angle)), EditorTheme.PATH_ORANGE)
                }
            }
            if (handles) (ViewportGizmoInteraction.selected as? ActorPositionTarget)?.position(scene)?.let {
                CinematicWorldRenderer.queueAxisGizmo(it, ViewportGizmoInteraction.ARM_LENGTH_BLOCKS)
            }
        }
    }

    private fun line(from: Vec3, to: Vec3, color: Int) = CinematicWorldRenderer.queueLine(from, to, color)

    private fun PositionExpression.literalPosition(): Vec3? {
        val x = (this.x as? CoordinateValue.Static)?.value ?: return null
        val y = (this.y as? CoordinateValue.Static)?.value ?: return null
        val z = (this.z as? CoordinateValue.Static)?.value ?: return null
        return Vec3(x, y, z)
    }
}
