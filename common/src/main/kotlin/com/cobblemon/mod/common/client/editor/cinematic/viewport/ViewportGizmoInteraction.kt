/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.editor.cinematic.viewport

import kotlin.math.hypot
import com.cobblemon.mod.common.client.editor.cinematic.SceneEditorOverlay
import com.cobblemon.mod.common.client.editor.cinematic.SceneSelection
import com.cobblemon.mod.common.client.editor.cinematic.SceneSelectionManager
import com.cobblemon.mod.common.client.editor.model.cinematic.ActorKeyframeTarget
import com.cobblemon.mod.common.client.editor.model.cinematic.ActorPositionTarget
import com.cobblemon.mod.common.client.editor.model.cinematic.ActorSplinePointTarget
import com.cobblemon.mod.common.client.editor.model.cinematic.EditorGrabTarget
import com.cobblemon.mod.common.client.editor.model.cinematic.EditorScene
import com.cobblemon.mod.common.client.editor.model.cinematic.ShotKeyframeTarget
import com.cobblemon.mod.common.client.editor.model.cinematic.SplinePointTarget
import com.google.gson.JsonObject
import imgui.ImGui
import imgui.flag.ImGuiKey
import net.minecraft.world.phys.Vec3
import org.joml.Vector2f

object ViewportGizmoInteraction {
    const val ARM_LENGTH_BLOCKS = 0.75f
    private const val PICK_RADIUS_PX = 8f
    var selected: EditorGrabTarget? = null
        private set
    var activeAxis: GizmoAxis? = null
        private set
    val isDragging: Boolean get() = dragAxis != null
    private var dragAxis: GizmoAxis? = null
    private var dragStart = Vector2f()
    private var dragOrigin = Vector2f()
    private var dragTip = Vector2f()
    private var dragBefore: JsonObject? = null
    private var dragBase: Vec3 = Vec3.ZERO
    private var dragScene: EditorScene? = null
    private var dragTarget: EditorGrabTarget? = null
    private var dragOwner: JsonObject? = null
    private var dragGeneration = -1

    fun reset() {
        cancelDrag()
        selected = null
        activeAxis = null
    }
    private fun clearDrag() {
        dragAxis = null
        dragBefore = null
        dragScene = null
        dragTarget = null
        dragOwner = null
        dragGeneration = -1
    }
    private fun cancelDrag() {
        val scene = dragScene
        val target = dragTarget
        val before = dragBefore
        if (scene != null && target != null && before != null && ownsDrag(scene, target)) {
            target.restore(scene, before)
        }
        clearDrag()
    }
    fun update(scene: EditorScene, selection: SceneSelectionManager) {
        val mouse = Vector2f(ImGui.getMousePosX(), ImGui.getMousePosY())
        if (dragAxis != null) { updateDrag(scene, mouse); return }
        if (!EditorCameraProjection.isReady) { activeAxis = null; return }
        adoptInspectorSelection(scene, selection)
        val validTargets = candidates(scene, selection)
        if (selected != null && selected !in validTargets) { selected = null; activeAxis = null }
        if (ImGui.getIO().getWantCaptureMouse() || !insideViewport(mouse)) { activeAxis = null; return }
        val target = selected
        if (target != null) {
            val position = target.position(scene)
            val origin = position?.let(::screenOf)
            if (position != null && origin != null) {
                val tips = armTips(position)
                activeAxis = GizmoHandles.hit(mouse, origin, tips)
                val axis = activeAxis
                if (axis != null && ImGui.isMouseClicked(0)) { beginDrag(scene, target, axis, mouse, origin, tips.getValue(axis)); return }
            } else activeAxis = null
        }
        if (ImGui.isMouseClicked(0)) nearestTarget(scene, validTargets, mouse)?.let { selectTarget(it, selection) }
    }
    private fun adoptInspectorSelection(scene: EditorScene, selection: SceneSelectionManager) {
        val inspectorShot = selection.getSelectedShotId() ?: return
        val row = SceneEditorOverlay.selectedKeyframeRow
        if (row < 0) return
        val wanted = when (runCatching { scene.shots[inspectorShot]?.positionData?.get("type")?.asString }.getOrNull()) {
            "keyframe" -> ShotKeyframeTarget(inspectorShot, row)
            "spline" -> SplinePointTarget(inspectorShot, row)
            else -> return
        }
        if (selected != wanted) selected = wanted
    }
    private fun insideViewport(mouse: Vector2f): Boolean {
        val x = EditorViewportState.frameX.toFloat()
        val y = EditorViewportState.frameY.toFloat()
        return mouse.x >= x && mouse.x <= x + EditorViewportState.frameWidth &&
            mouse.y >= y && mouse.y <= y + EditorViewportState.frameHeight
    }
    private fun screenOf(position: Vec3): Vector2f? = EditorCameraProjection.worldToScreen(position.x, position.y, position.z)
    private fun armTips(origin: Vec3): Map<GizmoAxis, Vector2f> = LinkedHashMap<GizmoAxis, Vector2f>(3).also { out ->
        val length = ARM_LENGTH_BLOCKS.toDouble()
        screenOf(Vec3(origin.x + length, origin.y, origin.z))?.let { out[GizmoAxis.X] = it }
        screenOf(Vec3(origin.x, origin.y + length, origin.z))?.let { out[GizmoAxis.Y] = it }
        screenOf(Vec3(origin.x, origin.y, origin.z + length))?.let { out[GizmoAxis.Z] = it }
    }
    private fun beginDrag(scene: EditorScene, target: EditorGrabTarget, axis: GizmoAxis, mouse: Vector2f, origin: Vector2f, tip: Vector2f) {
        val before = target.owningJson(scene)?.deepCopy() ?: return
        val base = target.position(scene) ?: return
        target.isolate(scene)
        val owner = target.owningJson(scene) ?: return
        dragAxis = axis
        dragStart = Vector2f(mouse)
        dragOrigin = Vector2f(origin)
        dragTip = Vector2f(tip)
        dragBefore = before
        dragBase = base
        dragScene = scene
        dragTarget = target
        dragOwner = owner
        dragGeneration = scene.editGeneration
        activeAxis = axis
    }
    private fun updateDrag(scene: EditorScene, mouse: Vector2f) {
        val axis = dragAxis ?: return
        val target = dragTarget ?: run { clearDrag(); return }
        if (dragScene !== scene) {
            cancelDrag()
            activeAxis = null
            return
        }
        if (!ownsDrag(scene, target)) {
            clearDrag()
            activeAxis = null
            return
        }
        if (ImGui.isKeyPressed(ImGuiKey.Escape)) {
            cancelDrag()
            activeAxis = null
            return
        }
        if (ImGui.isMouseDown(0)) {
            val delta = GizmoHandles.axisDelta(dragStart, mouse, dragOrigin, dragTip, ARM_LENGTH_BLOCKS).toDouble()
            if (!delta.isFinite()) return
            target.moveTo(
                scene,
                dragBase.x + if (axis == GizmoAxis.X) delta else 0.0,
                dragBase.y + if (axis == GizmoAxis.Y) delta else 0.0,
                dragBase.z + if (axis == GizmoAxis.Z) delta else 0.0
            )
        } else {
            dragBefore?.let { target.commit(scene, it) }
            clearDrag()
        }
    }
    private fun ownsDrag(scene: EditorScene?, target: EditorGrabTarget?): Boolean =
        scene != null && target != null && dragScene === scene && dragTarget === target &&
            dragOwner === target.owningJson(scene) && dragGeneration == scene.editGeneration
    private fun candidates(scene: EditorScene, selection: SceneSelectionManager): List<EditorGrabTarget> {
        val out = ArrayList<EditorGrabTarget>()
        try {
            selection.getSelectedShotId()?.let { shotId ->
                if (!SceneEditorOverlay.viewState.cameraPathsAndFrustums || !SceneEditorOverlay.viewState.keyframesAndGizmos) return@let
                val position = scene.shots[shotId]?.positionData
                when (position?.get("type")?.asString) {
                    "keyframe" -> position.getAsJsonArray("keyframes")?.let { frames -> for (i in 0 until frames.size()) out += ShotKeyframeTarget(shotId, i) }
                    "spline" -> position.getAsJsonArray("controlPoints")?.let { points -> for (i in 0 until points.size()) out += SplinePointTarget(shotId, i) }
                }
            }
        } catch (_: Throwable) { }
        val beatId = selection.getSelectedBeatId() ?: scene.startBeatId
        scene.beats[beatId]?.actorDirections?.forEach { (actorId, direction) -> direction.tracks.forEachIndexed { index, track ->
            if (!SceneEditorOverlay.viewState.actors || !SceneEditorOverlay.viewState.keyframesAndGizmos) return@forEachIndexed
            try {
                val spline = track.trackType in setOf("spline_movement", "pathfinding")
                if (!spline && track.trackType != "keyframe_movement") return@forEachIndexed
                val entries = track.data.getAsJsonArray(if (spline) "controlPoints" else "keyframes") ?: return@forEachIndexed
                for (i in 0 until entries.size()) out += if (spline) ActorSplinePointTarget(beatId, actorId, index, i) else ActorKeyframeTarget(beatId, actorId, index, i)
            } catch (_: Throwable) { }
        } }
        return out
    }
    private fun nearestTarget(scene: EditorScene, candidates: List<EditorGrabTarget>, mouse: Vector2f): EditorGrabTarget? {
        var best: EditorGrabTarget? = null; var distance = Float.POSITIVE_INFINITY
        candidates.forEach { target ->
            val point = target.position(scene) ?: return@forEach
            val screen = screenOf(point) ?: return@forEach
            val nextDistance = hypot(mouse.x - screen.x, mouse.y - screen.y)
            if (nextDistance <= PICK_RADIUS_PX && nextDistance < distance) { best = target; distance = nextDistance }
        }
        return best
    }
    fun selectTarget(target: EditorGrabTarget, selection: SceneSelectionManager) {
        selected = target
        when (target) {
            is ShotKeyframeTarget -> selection.setPendingKeyframeRowFocus(target.row)
            is SplinePointTarget -> selection.setPendingKeyframeRowFocus(target.index)
            is ActorPositionTarget -> selection.select(SceneSelection.BeatActorSelected(target.beatId, target.actorId))
        }
    }
}
