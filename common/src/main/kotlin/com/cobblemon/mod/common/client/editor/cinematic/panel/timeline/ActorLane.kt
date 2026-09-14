/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.editor.cinematic.panel.timeline

import com.cobblemon.mod.common.client.editor.cinematic.EditorDiagnostics
import com.cobblemon.mod.common.client.editor.cinematic.EditorTheme
import com.cobblemon.mod.common.client.editor.model.cinematic.EditorBeat
import com.cobblemon.mod.common.client.editor.model.cinematic.EditorScene
import imgui.ImGui
import net.minecraft.client.resources.language.I18n
import kotlin.math.abs

object ActorLane {
    val LANE_H: Float get() = TimelineStyle.rowHeight

    data class ItemHit(val actorId: String, val trackIndex: Int, val itemIndex: Int)
    data class Result(val hovered: ItemHit?, val bottomY: Float)

    fun render(scene: EditorScene, beat: EditorBeat, axis: TimeAxis, collapsed: MutableSet<String>,
               gutterX: Float, gutterWidth: Float, startY: Float): Result {
        var hovered: ItemHit? = null
        var y = startY
        val draw = ImGui.getWindowDrawList()
        val accent = EditorTheme.imguiPacked(EditorTheme.ACTOR)
        val duration = TimelineBeatSpan.spanOf(beat)
        scene.actors.keys.forEach { actorId ->
            val direction = beat.actorDirections[actorId] ?: return@forEach
            val open = actorId !in collapsed
            TimelineStyle.lane(axis, gutterX, y, LANE_H, duration)
            ImGui.setCursorScreenPos(gutterX, y)
            if (ImGui.invisibleButton("##actorhdr$actorId", gutterWidth, LANE_H)) {
                if (open) collapsed.add(actorId) else collapsed.remove(actorId)
            }
            EditorDiagnostics.item("timeline.actor.$actorId")
            TimelineStyle.label((if (open) "v  " else ">  ") + actorId, gutterX + 10f, y + 7f, gutterWidth - 20f, accent)
            val count = direction.tracks.size
            TimelineStyle.label(I18n.get("cobblemon.cinematic_editor.timeline.track_count", count), axis.originX + 10f, y + 7f, axis.widthPx - 20f, TimelineStyle.muted)
            if (ImGui.isItemHovered()) ImGui.setTooltip(I18n.get(if (open) "cobblemon.cinematic_editor.timeline.collapse_actor" else "cobblemon.cinematic_editor.timeline.expand_actor", actorId))
            y += LANE_H
            if (!open) return@forEach
            direction.tracks.forEachIndexed { trackIndex, track ->
                TimelineStyle.lane(axis, gutterX, y, LANE_H, duration)
                TimelineStyle.label(TimelinePresentation.trackName(track.trackType), gutterX + 24f, y + 7f, gutterWidth - 34f, TimelineStyle.muted)
                draw.pushClipRect(axis.originX, y, axis.originX + axis.widthPx, y + LANE_H, true)
                val times = TimelineTrackTimes.times(track)
                if (times.isEmpty()) {
                    val start = axis.timeToX(0f).coerceAtLeast(axis.originX)
                    val end = axis.timeToX(track.data.get("duration")?.asFloat ?: duration).coerceAtMost(axis.originX + axis.widthPx)
                    if (end > start) {
                        draw.addRectFilled(start + 2f, y + 4f, end - 2f, y + LANE_H - 4f, TimelineStyle.actorFill, 3f)
                        draw.addRectFilled(start + 2f, y + 4f, start + 5f, y + LANE_H - 4f, accent)
                        TimelineStyle.label(TimelinePresentation.trackName(track.trackType), start + 12f, y + 7f, end - start - 24f)
                    }
                    if (ImGui.isWindowHovered() && ImGui.isMouseHoveringRect(start, y, end, y + LANE_H)) hovered = ItemHit(actorId, trackIndex, -1)
                } else {
                    val cy = y + LANE_H / 2f
                    val radius = (ImGui.getFontSize() * 0.35f).coerceAtLeast(5f)
                    times.forEachIndexed { itemIndex, time ->
                        val cx = axis.timeToX(time)
                        TimelineStyle.diamond(cx, cy, radius, EditorTheme.PATH_ORANGE_PACKED)
                        if (ImGui.isWindowHovered() && ImGui.getMousePosX() in axis.originX..(axis.originX + axis.widthPx) &&
                            abs(ImGui.getMousePosX() - cx) <= radius + 2f && abs(ImGui.getMousePosY() - cy) <= radius + 2f) {
                            hovered = ItemHit(actorId, trackIndex, itemIndex)
                        }
                    }
                }
                draw.popClipRect()
                y += LANE_H
            }
        }
        return Result(hovered, y)
    }
}
