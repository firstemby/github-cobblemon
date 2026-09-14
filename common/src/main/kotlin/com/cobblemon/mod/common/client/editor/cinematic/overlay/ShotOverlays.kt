/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.editor.cinematic.overlay

import com.cobblemon.mod.common.client.imgui.CobblemonImGui
import com.cobblemon.mod.common.api.cinematic.model.sources.builtin.CranePosition
import com.cobblemon.mod.common.api.cinematic.model.sources.builtin.DollyPosition
import com.cobblemon.mod.common.api.cinematic.model.sources.builtin.FixedFov
import com.cobblemon.mod.common.api.cinematic.model.sources.builtin.FixedPosition
import com.cobblemon.mod.common.api.cinematic.model.sources.builtin.FixedRotation
import com.cobblemon.mod.common.api.cinematic.model.sources.builtin.KeyframePosition
import com.cobblemon.mod.common.api.cinematic.model.sources.builtin.LookAt
import com.cobblemon.mod.common.api.cinematic.model.sources.builtin.LookAtTarget
import com.cobblemon.mod.common.api.cinematic.model.sources.builtin.OrbitPosition
import com.cobblemon.mod.common.api.cinematic.model.sources.builtin.SplinePosition
import com.cobblemon.mod.common.api.cinematic.model.sources.builtin.TruckPosition
import com.cobblemon.mod.common.api.cinematic.runtime.CameraBlockCollision
import com.cobblemon.mod.common.client.editor.cinematic.EditorTheme
import com.cobblemon.mod.common.client.editor.cinematic.EditorPathCache
import com.cobblemon.mod.common.client.editor.cinematic.SceneEditorOverlay
import com.cobblemon.mod.common.client.editor.cinematic.viewport.GizmoAxis
import com.cobblemon.mod.common.client.editor.cinematic.viewport.ViewportGizmoInteraction
import com.cobblemon.mod.common.client.editor.model.cinematic.EditorShot
import com.cobblemon.mod.common.client.render.cinematic.CinematicWorldRenderer
import com.cobblemon.mod.common.platform.events.PlatformEvents
import com.cobblemon.mod.common.platform.events.RenderEvent
import com.google.gson.JsonObject
import net.minecraft.world.phys.Vec3
import net.minecraft.world.phys.AABB
import net.minecraft.client.Minecraft
import org.joml.Vector3f
import kotlin.math.cos
import kotlin.math.sin

// Force class loading on editor initialization so the render subscription is installed.
object ShotOverlays {
    private var renderedShotId = ""
    private var renderedFov: Float? = null
    private data class OrbitPathKey(val orbit: OrbitPosition, val bounds: AABB?, val fov: Float, val bearing: Vec3?)
    private val orbitPaths = mutableMapOf<OrbitPathKey, List<Vec3>>()
    private var orbitPathTick = Long.MIN_VALUE
    private var orbitPathScene: Any? = null
    private var orbitPathLevel: Any? = null

    private fun queuePoint(center: Vec3, sizeBlocks: Float, argb: Int) {
        if (SceneEditorOverlay.viewState.keyframesAndGizmos) CinematicWorldRenderer.queueDiamond(center, sizeBlocks, argb)
    }

    init {
        PlatformEvents.RENDER.subscribe { ev ->
            if (ev.stage != RenderEvent.Stage.TRANSLUCENT) return@subscribe
            if (!CobblemonImGui.isActive()) return@subscribe
            val scene = SceneEditorOverlay.activeScene ?: return@subscribe
            val selectedId = SceneEditorOverlay.selection.getSelectedShotId()
            val view = SceneEditorOverlay.viewState
            val playhead = SceneEditorOverlay.currentPlayheadSeconds
            val level = Minecraft.getInstance().level
            val tick = (level?.gameTime ?: 0L) / 5
            if (tick != orbitPathTick || scene !== orbitPathScene || level !== orbitPathLevel) {
                orbitPaths.clear()
                orbitPathTick = tick
                orbitPathScene = scene
                orbitPathLevel = level
            }
            val shots = ShotOverlaySelection.shots(scene, SceneEditorOverlay.selection, view.showAllShots)
            for (shot in shots) {
                renderedShotId = shot.id
                renderedFov = resolveFov(shot.fovData)
                val selectedRow = if (shot.id == selectedId) SceneEditorOverlay.selectedKeyframeRow else -1
                if (view.cameraPathsAndFrustums) {
                    drawFrustum(shot, playhead)
                    drawPositionOverlays(shot, selectedRow)
                    if (view.keyframesAndGizmos) drawSelectedKeyframeGizmo(shot, selectedRow)
                }
                if (view.focusLines) drawLookAtLine(shot, playhead)
            }
        }
    }

    private fun drawFrustum(shot: EditorShot, time: Float) {
        val origin = resolvePosition(shot.positionData, time) ?: return
        val (yaw, pitch) = resolveRotation(shot.rotationData, origin) ?: return
        val fov = resolveFov(shot.fovData) ?: 70f

        val yawRad = Math.toRadians(yaw.toDouble()).toFloat()
        val pitchRad = Math.toRadians(pitch.toDouble()).toFloat()
        val forward = Vector3f(
            -cos(pitchRad) * sin(yawRad),
            -sin(pitchRad),
            cos(pitchRad) * cos(yawRad)
        )
        val up = Vector3f(
            -sin(pitchRad) * sin(yawRad),
            cos(pitchRad),
            sin(pitchRad) * cos(yawRad)
        )

        CinematicWorldRenderer.queueFrustum(
            origin = origin,
            forward = forward, up = up,
            fov = fov, aspect = 16f / 9f, near = 1.5f,
            argb = EditorTheme.PATH_ORANGE
        )
    }

    private fun drawPositionOverlays(shot: EditorShot, selectedRow: Int) {
        val pos = shot.positionData
        when (pos.get("type")?.asString) {
            "keyframe" -> drawKeyframePathAndDiamonds(pos, selectedRow)
            "spline" -> drawSplineOverlay(pos, selectedRow)
            "orbit" -> drawOrbitOverlay(pos)
            "dolly" -> drawDollyOverlay(pos)
            "truck" -> drawTruckOverlay(pos)
            "crane" -> drawCraneOverlay(pos)
        }
    }

    private fun drawKeyframePathAndDiamonds(pos: JsonObject, selectedRow: Int) {
        val kf = try { KeyframePosition.fromJson(pos) } catch (_: Exception) { return }
        val times = kf.keyframes.map { it.time }
        if (times.isEmpty()) return
        val tMin = times.min(); val tMax = times.max()
        val steps = 32
        if (tMax > tMin) {
            var prev: Vec3? = null
            for (i in 0..steps) {
                val t = tMin + (tMax - tMin) * (i.toFloat() / steps)
                val p = kf.evaluate(t)
                prev?.let { pv ->
                    CinematicWorldRenderer.queueLine(
                        pv, p,
                        EditorTheme.PATH_ORANGE
                    )
                }
                prev = p
            }
        }
        kf.keyframes.forEachIndexed { i, row ->
            val colour = if (i == selectedRow) EditorTheme.PATH_SELECTED_BLUE else EditorTheme.PATH_ORANGE
            queuePoint(
                row.position,
                sizeBlocks = 0.15f, argb = colour
            )
        }
    }

    private fun drawSplineOverlay(pos: JsonObject, selectedRow: Int) {
        val spl = try { EditorPathCache.shot(renderedShotId, pos) } catch (_: Exception) { return }
        val steps = 64
        var prev: Vec3? = null
        for (i in 0..steps) {
            val t = (i.toFloat() / steps) * spl.duration
            val p = spl.evaluate(t)
            prev?.let { pv ->
                CinematicWorldRenderer.queueLine(
                    pv, p,
                    EditorTheme.PATH_ORANGE
                )
            }
            prev = p
        }
        spl.controlPoints.forEachIndexed { i, cp ->
            val colour = if (i == selectedRow) EditorTheme.PATH_SELECTED_BLUE else EditorTheme.PATH_ORANGE
            queuePoint(
                cp,
                sizeBlocks = 0.2f, argb = colour
            )
        }
    }

    private fun drawOrbitOverlay(pos: JsonObject) {
        val orb = try { OrbitPosition.fromJson(pos) } catch (_: Exception) { return }
        val bounds = EditorActorBounds.bounds(orb.actorIds)
        val focus = orb.focus(bounds)
        val bearing = orb.bearingActorId?.let { EditorActorBounds.bounds(it)?.center }
        val fov = renderedFov ?: orb.framingFov
        val points = orbitPaths.getOrPut(OrbitPathKey(orb, bounds, fov, bearing)) {
            val collision = if (orb.avoidBlocks) Minecraft.getInstance().level?.let(CameraBlockCollision::forLevel) else null
            val circle = orb.copy(angularVelocityDegPerSec = 360f)
            val steps = if (orb.avoidBlocks && orb.avoidanceSmoothing > 0f) 192 else 48
            (0..steps).mapNotNull { step ->
                val desired = circle.evaluate(step.toFloat() / steps, bounds, fov, bearing)
                if (collision != null) collision.orbitPosition(focus, desired, orb.avoidanceSmoothing) else desired
            }
        }
        points.zipWithNext().forEach { (a, b) ->
            CinematicWorldRenderer.queueLine(a, b, EditorTheme.PATH_ORANGE)
        }
        queuePoint(
            focus,
            sizeBlocks = 0.2f, argb = EditorTheme.PATH_ORANGE
        )
    }

    private fun drawDollyOverlay(pos: JsonObject) {
        val dly = try { DollyPosition.fromJson(pos) } catch (_: Exception) { return }
        CinematicWorldRenderer.queueLine(
            dly.from, dly.to,
            EditorTheme.PATH_ORANGE
        )
        queuePoint(
            dly.from,
            sizeBlocks = 0.2f, argb = EditorTheme.PATH_ORANGE
        )
        queuePoint(
            dly.to,
            sizeBlocks = 0.2f, argb = EditorTheme.PATH_ORANGE
        )
    }

    private fun drawTruckOverlay(pos: JsonObject) {
        val tr = try { TruckPosition.fromJson(pos) } catch (_: Exception) { return }
        val len = 10.0
        val end = Vec3(tr.origin.x + tr.axis.x * len, tr.origin.y + tr.axis.y * len, tr.origin.z + tr.axis.z * len)
        CinematicWorldRenderer.queueLine(
            tr.origin, end,
            EditorTheme.PATH_ORANGE
        )
        queuePoint(
            tr.origin,
            sizeBlocks = 0.2f, argb = EditorTheme.PATH_ORANGE
        )
    }

    private fun drawCraneOverlay(pos: JsonObject) {
        val cr = try { CranePosition.fromJson(pos) } catch (_: Exception) { return }
        val steps = 48
        val y = cr.pivot.y + cr.height
        var prev: Vec3? = null
        val sweepDeg = 360f
        for (i in 0..steps) {
            val aDeg = cr.startAngleDeg + (i.toFloat() / steps) * sweepDeg
            val a = Math.toRadians(aDeg.toDouble())
            val x = cr.pivot.x + cr.armLength * cos(a)
            val z = cr.pivot.z + cr.armLength * sin(a)
            val p = Vec3(x, y, z)
            prev?.let { CinematicWorldRenderer.queueLine(it, p, EditorTheme.PATH_ORANGE) }
            prev = p
        }
        queuePoint(
            cr.pivot,
            sizeBlocks = 0.2f, argb = EditorTheme.PATH_ORANGE
        )
    }

    private fun drawSelectedKeyframeGizmo(shot: EditorShot, selectedRow: Int) {
        if (selectedRow < 0) return
        val pos = shot.positionData
        val origin = try {
            when (pos.get("type")?.asString) {
                "keyframe" -> KeyframePosition.fromJson(pos).keyframes.getOrNull(selectedRow)?.position
                "spline" -> SplinePosition.fromJson(pos).controlPoints.getOrNull(selectedRow)
                else -> null
            }
        } catch (_: Exception) { null } ?: return
        drawTranslateGizmo(origin)
    }

    private fun drawTranslateGizmo(origin: Vec3) {
        val l = ViewportGizmoInteraction.ARM_LENGTH_BLOCKS
        val active = ViewportGizmoInteraction.activeAxis
        fun arm(axis: GizmoAxis, tip: Vec3, colour: Int) {
            val shown = if (active == null || axis == active) colour
                        else (colour and 0x00FFFFFF) or (0x55 shl 24)
            CinematicWorldRenderer.queueLine(origin, tip, shown)
        }
        arm(GizmoAxis.X, origin.add(l.toDouble(), 0.0, 0.0), EditorTheme.AXIS_X)
        arm(GizmoAxis.Y, origin.add(0.0, l.toDouble(), 0.0), EditorTheme.AXIS_Y)
        arm(GizmoAxis.Z, origin.add(0.0, 0.0, l.toDouble()), EditorTheme.AXIS_Z)
    }

    private fun drawLookAtLine(shot: EditorShot, time: Float) {
        val rot = shot.rotationData
        if (rot.get("type")?.asString != "look_at") return
        val source = resolvePosition(shot.positionData, time) ?: return
        val la = try { LookAt.fromJson(rot) } catch (_: Exception) { return }
        val target = resolveTarget(la.target) ?: return
        CinematicWorldRenderer.queueLine(
            source, target,
            EditorTheme.LOOK_AT_GREY
        )
    }

    private fun resolvePosition(data: JsonObject, time: Float): Vec3? = try {
        when (data.get("type")?.asString) {
            "fixed" -> FixedPosition.fromJson(data).position
            "keyframe" -> KeyframePosition.fromJson(data).evaluate(time)
            "spline" -> EditorPathCache.shot(renderedShotId, data).evaluate(time)
            "orbit" -> {
                val orbit = OrbitPosition.fromJson(data)
                val bounds = EditorActorBounds.bounds(orbit.actorIds)
                val desired = orbit.evaluate(time, bounds, renderedFov ?: orbit.framingFov,
                    orbit.bearingActorId?.let { EditorActorBounds.bounds(it)?.center })
                val level = Minecraft.getInstance().level
                if (orbit.avoidBlocks && level != null) {
                    CameraBlockCollision.forLevel(level).orbitPosition(orbit.focus(bounds), desired, orbit.avoidanceSmoothing)
                } else desired
            }
            "dolly" -> DollyPosition.fromJson(data).evaluate(time)
            "truck" -> TruckPosition.fromJson(data).evaluate(time)
            "crane" -> CranePosition.fromJson(data).evaluate(time)
            // follow_actor requires runtime ActorDirector; null means no frustum this frame
            else -> null
        }
    } catch (_: Exception) { null }

    private fun resolveRotation(data: JsonObject, cameraPos: Vec3): Pair<Float, Float>? = try {
        when (data.get("type")?.asString) {
            "fixed" -> FixedRotation.fromJson(data).rotation.let { it.yaw to it.pitch }
            "look_at" -> {
                val la = LookAt.fromJson(data)
                val targetPos = resolveTarget(la.target) ?: return null
                val r = la.computeRotation(cameraPos, targetPos)
                r.yaw to r.pitch
            }
            else -> null
        }
    } catch (_: Exception) { null }

    private fun resolveTarget(target: LookAtTarget): Vec3? = when (target) {
        is LookAtTarget.Position -> target.pos
        is LookAtTarget.Actors -> EditorActorBounds.bounds(target.actorIds)?.center
        is LookAtTarget.Actor -> EditorActorBounds.bounds(target.actorId)?.let { Vec3(it.center.x, it.minY, it.center.z) }
        is LookAtTarget.ActorBone -> EditorActorBounds.bounds(target.actorId)?.center?.add(target.offset)
        else -> null
    }

    private fun resolveFov(data: JsonObject?): Float? = try {
        if (data == null) null
        else if (data.get("type")?.asString == "fixed") FixedFov.fromJson(data).fov
        else null
    } catch (_: Exception) { null }
}
