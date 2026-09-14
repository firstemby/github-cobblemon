/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.editor.cinematic.panel.inspector

import com.cobblemon.mod.common.client.editor.cinematic.panel.inspector.actortrack.AnimationTrackEditor
import com.cobblemon.mod.common.client.editor.cinematic.panel.inspector.actortrack.AttachTrackEditor
import com.cobblemon.mod.common.client.editor.cinematic.panel.inspector.actortrack.FollowActorTrackEditor
import com.cobblemon.mod.common.client.editor.cinematic.panel.inspector.actortrack.KeyframeMovementEditor
import com.cobblemon.mod.common.client.editor.cinematic.panel.inspector.actortrack.LookAtTrackEditor
import com.cobblemon.mod.common.client.editor.cinematic.panel.inspector.actortrack.PoseLockTrackEditor
import com.cobblemon.mod.common.client.editor.cinematic.panel.inspector.actortrack.SplineMovementEditor
import com.cobblemon.mod.common.client.editor.cinematic.panel.inspector.actortrack.WaypointPathfindingEditor
import com.cobblemon.mod.common.client.editor.cinematic.EditorWidgets
import imgui.flag.ImGuiTreeNodeFlags
import com.cobblemon.mod.common.client.editor.model.EditorTrack
import com.cobblemon.mod.common.client.editor.model.cinematic.EditorScene
import com.cobblemon.mod.common.client.editor.model.cinematic.actions.AddActorTrack
import com.cobblemon.mod.common.client.editor.model.cinematic.actions.RemoveActorTrack
import imgui.ImGui
import net.minecraft.client.resources.language.I18n

class ActorTracksEditor {

    private val keyframeMovement = KeyframeMovementEditor()
    private val splineMovement = SplineMovementEditor()
    private val pathfinding = WaypointPathfindingEditor()
    private val followActor = FollowActorTrackEditor()
    private val lookAt = LookAtTrackEditor()
    private val animation = AnimationTrackEditor()
    private val attach = AttachTrackEditor()
    private val poseLock = PoseLockTrackEditor()

    fun render(scene: EditorScene, beatId: String, actorId: String) {
        val beat = scene.beats[beatId] ?: run { ImGui.text(I18n.get("cobblemon.cinematic_editor.beat_missing", beatId)); return }
        val direction = beat.actorDirections[actorId] ?: run {
            ImGui.textDisabled(I18n.get("cobblemon.cinematic_editor.direction_missing", actorId))
            return
        }

        ImGui.textWrapped("$beatId / $actorId")
        ImGui.separator()

        var trackToRemove = -1
        direction.tracks.forEachIndexed { i, track ->
            ImGui.pushID("atr_${beatId}_${actorId}_$i")
            if (ImGui.collapsingHeader("[$i] ${ActorTrackTypes.entryFor(track.trackType)?.label ?: track.trackType}", ImGuiTreeNodeFlags.DefaultOpen)) {
                ImGui.indent()
                when (track.trackType) {
                    "keyframe_movement" -> keyframeMovement.render(scene, beatId, actorId, i, track)
                    "spline_movement" -> splineMovement.render(scene, beatId, actorId, i, track)
                    "pathfinding" -> pathfinding.render(scene, beatId, actorId, i, track)
                    "follow_actor" -> followActor.render(scene, beatId, actorId, i, track)
                    "look_at" -> lookAt.render(scene, beatId, actorId, i, track)
                    "animation" -> animation.render(scene, beatId, actorId, i, track)
                    "attach" -> attach.render(scene, beatId, actorId, i, track)
                    "pose_lock" -> poseLock.render(scene, beatId, actorId, i, track)
                    else -> ImGui.textDisabled(I18n.get("cobblemon.cinematic_editor.unknown_track", track.trackType))
                }
                if (ImGui.smallButton(I18n.get("cobblemon.cinematic_editor.delete_track"))) trackToRemove = i
                ImGui.unindent()
            }
            ImGui.popID()
        }

        if (trackToRemove >= 0 && trackToRemove in direction.tracks.indices) {
            val existing = direction.tracks[trackToRemove]
            scene.execute(RemoveActorTrack(beatId, actorId, trackToRemove, existing))
        }

        ImGui.separator()
        ImGui.text(I18n.get("cobblemon.cinematic_editor.add_track"))
        EditorWidgets.fullWidth()
        if (ImGui.beginCombo("##addactortrack_${beatId}_$actorId", I18n.get("cobblemon.cinematic_editor.choose_type"))) {
            ActorTrackTypes.entries().forEach { entry ->
                if (ImGui.selectable(entry.label)) {
                    val body = entry.defaultBody()
                    if (entry.trackType == "pathfinding") WaypointPathfindingEditor.placeInitialPoints(body)
                    val track = EditorTrack(entry.trackType, body)
                    scene.execute(AddActorTrack(beatId, actorId, track))
                }
            }
            ImGui.endCombo()
        }
    }
}
