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
import com.cobblemon.mod.common.client.editor.model.cinematic.EditorCutTrack
import com.cobblemon.mod.common.client.editor.model.cinematic.EditorScene
import com.cobblemon.mod.common.client.editor.model.cinematic.actions.ChangeCutEntry
import com.cobblemon.mod.common.client.editor.model.cinematic.actions.RemoveCutEntry
import imgui.ImGui
import net.minecraft.client.resources.language.I18n
import kotlin.math.abs

class CutLaneInteraction {

    private data class DragTarget(val beatId: String, val feedId: String, val entryIndex: Int, val startTime: Float, val pointerTime: Float)

    private var drag: DragTarget? = null
    private var contextMenuFeedId: String? = null
    private var contextMenuEntryIndex: Int = -1

    fun handle(
        scene: EditorScene,
        beatId: String,
        feedId: String,
        cutTrack: EditorCutTrack,
        geometry: CutLaneRenderer.LaneGeometry,
        selection: SceneSelectionManager,
        beatDuration: Float,
        axis: TimeAxis
    ) {
        val mouseX = ImGui.getMousePosX()
        val mouseY = ImGui.getMousePosY()
        val overLane = mouseY in geometry.laneTopY..(geometry.laneTopY + geometry.laneHeight) &&
            mouseX in axis.originX..(axis.originX + axis.widthPx)

        val hoverIndex = if (overLane) CutLaneMath.blockAt(mouseX, geometry.blockBoundsPx) else -1

        if (overLane && hoverIndex >= 0 && ImGui.isMouseClicked(0) && ImGui.isWindowHovered()) {
            selection.select(SceneSelection.CutSelected(beatId, feedId, hoverIndex))
            drag = DragTarget(
                beatId, feedId, hoverIndex,
                startTime = cutTrack.entries[hoverIndex].time,
                pointerTime = axis.xToTime(mouseX)
            )
        }

        val active = drag
        if (active != null && active.feedId == feedId && active.beatId == beatId) {
            if (ImGui.isMouseDown(0)) {
                val proposedTime = if (ImGui.isMouseDragging(0)) active.startTime + axis.xToTime(mouseX) - active.pointerTime else active.startTime
                val clamped = CutLaneMath.clampToMonotonic(
                    cutTimes = cutTrack.entries.map { it.time },
                    index = active.entryIndex,
                    proposedTime = proposedTime,
                    beatDuration = beatDuration
                )
                val entry = cutTrack.entries.getOrNull(active.entryIndex) ?: return
                if (abs(entry.time - clamped) > 1e-4f) {
                    // Commit one undo action at drag end, not a new action every frame.
                    entry.time = clamped
                }
            } else {
                val entry = cutTrack.entries.getOrNull(active.entryIndex)
                if (entry != null && abs(entry.time - active.startTime) > 1e-4f) {
                    val finalTime = entry.time
                    entry.time = active.startTime
                    scene.execute(ChangeCutEntry.time(beatId, feedId, active.entryIndex, active.startTime, finalTime))
                }
                drag = null
            }
        }

        if (overLane && hoverIndex >= 0 && ImGui.isMouseClicked(1) && ImGui.isWindowHovered()) {
            contextMenuFeedId = feedId
            contextMenuEntryIndex = hoverIndex
            ImGui.openPopup("##cutctx$feedId")
        }

        if (contextMenuFeedId == feedId && ImGui.beginPopup("##cutctx$feedId")) {
            val idx = contextMenuEntryIndex
            val entry = cutTrack.entries.getOrNull(idx)
            if (entry == null) {
                ImGui.closeCurrentPopup()
            } else {
                ImGui.textDisabled(I18n.get("cobblemon.cinematic_editor.timeline.cut_title", idx, entry.shotId))
                ImGui.separator()
                if (ImGui.menuItem(I18n.get("cobblemon.cinematic_editor.timeline.delete_cut"))) {
                    scene.execute(RemoveCutEntry(beatId, feedId, idx, entry))
                    if (selection.getSelectedCut()?.entryIndex == idx) selection.clear()
                }
                if (ImGui.menuItem(I18n.get("cobblemon.cinematic_editor.timeline.change_transition"))) {
                    TransitionPopover.open("##trpop$feedId$idx")
                }
            }
            ImGui.endPopup()
        }

        cutTrack.entries.forEachIndexed { i, entry ->
            val popupId = "##trpop$feedId$i"
            TransitionPopover.render(popupId, entry.transition) { next ->
                scene.execute(ChangeCutEntry.transition(beatId, feedId, i, entry.transition, next))
            }
        }
    }
}
