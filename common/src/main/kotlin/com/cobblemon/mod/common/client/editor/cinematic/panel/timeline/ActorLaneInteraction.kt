/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.editor.cinematic.panel.timeline

import com.cobblemon.mod.common.client.editor.cinematic.SceneSelection
import com.cobblemon.mod.common.client.editor.cinematic.SceneSelectionManager
import com.cobblemon.mod.common.client.editor.model.EditorTrack
import com.cobblemon.mod.common.client.editor.model.cinematic.EditorScene
import com.cobblemon.mod.common.client.editor.model.cinematic.actions.ChangeActorTrackData
import com.google.gson.JsonObject
import imgui.ImGui
import net.minecraft.client.resources.language.I18n

class ActorLaneInteraction {
    private data class Drag(
        val scene: EditorScene,
        val track: EditorTrack,
        val beatId: String,
        val actorId: String,
        val trackIndex: Int,
        val itemIndex: Int,
        val original: JsonObject
    )
    private var drag: Drag? = null

    private data class CtxTarget(val scene: EditorScene, val beatId: String, val actorId: String, val trackIndex: Int, val itemIndex: Int)
    private var ctxTarget: CtxTarget? = null

    fun handle(
        scene: EditorScene,
        beatId: String,
        axis: TimeAxis,
        hovered: ActorLane.ItemHit?,
        selection: SceneSelectionManager
    ) {
        drag?.takeIf { it.scene !== scene || it.beatId != beatId }?.let {
            restore(it.track.data, it.original)
            drag = null
        }
        val beat = scene.beats[beatId] ?: return

        if (drag == null && hovered != null && ImGui.isMouseClicked(0)) {
            val track = beat.actorDirections[hovered.actorId]?.tracks?.getOrNull(hovered.trackIndex) ?: return
            if (hovered.itemIndex >= 0) drag = Drag(scene, track, beatId, hovered.actorId, hovered.trackIndex, hovered.itemIndex, track.data.deepCopy())
            selection.select(SceneSelection.BeatActorSelected(beatId, hovered.actorId))
        }

        if (hovered != null && hovered.itemIndex >= 0 && ImGui.isMouseClicked(1)) {
            ctxTarget = CtxTarget(scene, beatId, hovered.actorId, hovered.trackIndex, hovered.itemIndex)
            ImGui.openPopup("##actorItemCtx")
        }

        if (ImGui.beginPopup("##actorItemCtx")) {
            val ctx = ctxTarget
            if (ctx == null || ctx.scene !== scene || ctx.beatId != beatId) {
                ctxTarget = null
                ImGui.closeCurrentPopup()
            } else {
                val track = scene.beats[ctx.beatId]?.actorDirections?.get(ctx.actorId)
                    ?.tracks?.getOrNull(ctx.trackIndex)
                if (track == null) {
                    ImGui.closeCurrentPopup()
                } else {
                    ImGui.textDisabled(I18n.get("cobblemon.cinematic_editor.timeline.track_item", TimelinePresentation.trackName(track.trackType), ctx.itemIndex))
                    ImGui.separator()
                    if (ImGui.menuItem(I18n.get("cobblemon.cinematic_editor.timeline.delete"))) {
                        val newData = TimelineTrackTimes.withItemRemoved(track, ctx.itemIndex)
                        scene.execute(ChangeActorTrackData(ctx.beatId, ctx.actorId, ctx.trackIndex, track.data.deepCopy(), newData))
                        ctxTarget = null
                    }
                }
            }
            ImGui.endPopup()
        }

        val d = drag ?: return
        val track = beat.actorDirections[d.actorId]?.tracks?.getOrNull(d.trackIndex)
        if (track == null || track !== d.track) {
            restore(d.track.data, d.original)
            drag = null
            return
        }

        if (ImGui.isMouseDown(0)) {
            if (!ImGui.isMouseDragging(0)) return
            var t = axis.xToTime(ImGui.getMousePosX()).coerceAtLeast(0f)
            if (!ImGui.getIO().getKeyAlt()) {
                val neighbours = TimelineTrackTimes.times(track).filterIndexed { i, _ -> i != d.itemIndex }
                t = TimelineSnap.snap(t, neighbours + listOf(0f), thresholdSeconds = 8f / axis.pixelsPerSecond)
            }
            val previewed = TimelineTrackTimes.withRetimedItem(track, d.itemIndex, t)
            restore(track.data, previewed)
        } else {
            val finalData = track.data.deepCopy()
            restore(track.data, d.original)
            if (finalData != d.original) {
                scene.execute(ChangeActorTrackData(d.beatId, d.actorId, d.trackIndex, d.original, finalData))
            }
            drag = null
        }
    }

    private fun restore(target: JsonObject, source: JsonObject) {
        target.keySet().toList().forEach { target.remove(it) }
        source.entrySet().forEach { (key, value) -> target.add(key, value.deepCopy()) }
    }
}
