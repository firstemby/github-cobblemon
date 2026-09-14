/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.editor.cinematic.panel.timeline

import com.cobblemon.mod.common.api.cinematic.model.CutTransition
import com.cobblemon.mod.common.client.editor.cinematic.EditorDiagnostics
import com.cobblemon.mod.common.client.editor.cinematic.EditorTheme
import com.cobblemon.mod.common.client.editor.cinematic.SceneSelection
import com.cobblemon.mod.common.client.editor.cinematic.SceneSelectionManager
import com.cobblemon.mod.common.client.editor.model.cinematic.EditorCutEntry
import com.cobblemon.mod.common.client.editor.model.cinematic.EditorCutTrack
import com.cobblemon.mod.common.client.editor.model.cinematic.EditorScene
import com.cobblemon.mod.common.client.editor.model.cinematic.actions.AddCutEntry
import com.cobblemon.mod.common.client.editor.model.cinematic.actions.SplitCutAt
import imgui.ImGui
import net.minecraft.client.resources.language.I18n

object CutLaneRenderer {

    data class LaneGeometry(
        val laneOriginX: Float,
        val laneTopY: Float,
        val laneWidthPx: Float,
        val laneHeight: Float,
        val cutTimes: List<Float>,
        val blockBoundsPx: List<Pair<Float, Float>>
    )

    val LANE_HEIGHT: Float get() = TimelineStyle.clipHeight

    fun renderGutter(
        scene: EditorScene,
        beatId: String,
        feedId: String,
        playheadSeconds: Float,
        shotPickerPopupId: String,
        gutterX: Float,
        gutterWidth: Float,
        laneTopY: Float
    ) {
        val draw = ImGui.getWindowDrawList()
        val accent = EditorTheme.imguiPacked(EditorTheme.SHOT)
        draw.addRectFilled(gutterX, laneTopY + 5f, gutterX + 3f, laneTopY + LANE_HEIGHT - 5f, accent)
        val compact = TimelineStyle.compactClips
        val actionsWidth = ImGui.calcTextSize("+").x + ImGui.calcTextSize(I18n.get("cobblemon.cinematic_editor.timeline.split")).x + ImGui.getStyle().framePaddingX * 4f + 5f
        TimelineStyle.label(feedId, gutterX + 10f, laneTopY + 7f, gutterWidth - 20f - if (compact) actionsWidth else 0f)
        ImGui.setCursorScreenPos(if (compact) gutterX + gutterWidth - actionsWidth - 8f else gutterX + 10f,
            if (compact) laneTopY + 5f else laneTopY + ImGui.getTextLineHeight() + 10f)
        if (ImGui.smallButton("+##cut$feedId")) ShotPickerPopup.open(shotPickerPopupId)
        EditorDiagnostics.item("timeline.cut.add.$feedId")
        if (ImGui.isItemHovered()) ImGui.setTooltip(I18n.get("cobblemon.cinematic_editor.timeline.add_at_playhead"))
        ImGui.sameLine(0f, 5f)
        if (ImGui.smallButton(I18n.get("cobblemon.cinematic_editor.timeline.split") + "##$feedId")) scene.execute(SplitCutAt(beatId, feedId, playheadSeconds))
        EditorDiagnostics.item("timeline.cut.split.$feedId")
        if (ImGui.isItemHovered()) ImGui.setTooltip(I18n.get("cobblemon.cinematic_editor.timeline.split_at_playhead"))
    }

    fun handleShotPickerPopup(
        scene: EditorScene,
        beatId: String,
        feedId: String,
        playheadSeconds: Float,
        shotPickerPopupId: String,
        selection: SceneSelectionManager
    ) {
        ShotPickerPopup.render(shotPickerPopupId, scene) { shotId ->
            val prevTransition = scene.beats[beatId]?.cuts?.get(feedId)?.entries?.lastOrNull { it.time <= playheadSeconds }?.transition
                ?: CutTransition.Instant
            val entry = EditorCutEntry(time = playheadSeconds, shotId = shotId, transition = prevTransition)
            scene.execute(AddCutEntry(beatId, feedId, entry))
            val index = scene.beats[beatId]?.cuts?.get(feedId)?.entries?.indexOf(entry) ?: -1
            if (index >= 0) selection.select(SceneSelection.CutSelected(beatId, feedId, index))
        }
    }

    fun render(
        scene: EditorScene,
        beatId: String,
        feedId: String,
        cutTrack: EditorCutTrack,
        selection: SceneSelectionManager,
        beatDuration: Float,
        axis: TimeAxis,
        laneTopY: Float
    ): LaneGeometry {
        val laneOriginX = axis.originX
        val laneWidthPx = axis.widthPx
        val laneHeight = LANE_HEIGHT
        val cutTimes = cutTrack.entries.map { it.time }

        val drawList = ImGui.getWindowDrawList()

        val selectedCut = selection.getSelectedCut()
        val blockBoundsPx = ArrayList<Pair<Float, Float>>(cutTrack.entries.size)

        drawList.pushClipRect(laneOriginX, laneTopY, laneOriginX + laneWidthPx, laneTopY + laneHeight, true)
        cutTrack.entries.forEachIndexed { i, entry ->
            val (x0, x1) = CutLaneMath.blockBounds(cutTimes, i, axis, beatDuration)
            blockBoundsPx += x0 to x1

            val isSelected = selectedCut != null &&
                    selectedCut.beatId == beatId &&
                    selectedCut.feedId == feedId &&
                    selectedCut.entryIndex == i
            val accent = EditorTheme.imguiPacked(EditorTheme.SHOT)
            val left = maxOf(x0 + 2f, laneOriginX)
            val right = minOf(x1 - 2f, laneOriginX + laneWidthPx)
            if (right > left) {
                val hovered = ImGui.isWindowHovered() &&
                    ImGui.isMouseHoveringRect(left, laneTopY + 4f, right, laneTopY + laneHeight - 4f)
                drawList.addRectFilled(left, laneTopY + 4f, right, laneTopY + laneHeight - 4f,
                    if (isSelected) TimelineStyle.shotSelected else TimelineStyle.shotFill, 4f)
                drawList.addRectFilled(left, laneTopY + 4f, right, laneTopY + 7f, accent)
                if (isSelected) drawList.addRect(left, laneTopY + 4f, right, laneTopY + laneHeight - 4f, TimelineStyle.text, 4f)
                else if (hovered) drawList.addRect(left, laneTopY + 4f, right, laneTopY + laneHeight - 4f, accent, 4f)
                TimelineStyle.label(entry.shotId, left + 9f, laneTopY + if (TimelineStyle.compactClips) 7f else 11f, right - left - 18f)
                val endTime = cutTimes.getOrNull(i + 1) ?: beatDuration
                if (!TimelineStyle.compactClips) {
                    TimelineStyle.label(TimelinePresentation.timecode((endTime - entry.time).coerceAtLeast(0f)),
                        left + 9f, laneTopY + 12f + ImGui.getTextLineHeight(), right - left - 18f, TimelineStyle.muted)
                }
            }
            if (i > 0) {
                val pillColour = when (entry.transition) {
                    is CutTransition.Instant -> EditorTheme.TRANSITION_PILL_INSTANT
                    is CutTransition.Blend -> EditorTheme.TRANSITION_PILL_BLEND
                }
                val pillW = 5f
                drawList.addRectFilled(x0 - pillW * 0.5f, laneTopY + 9f, x0 + pillW * 0.5f, laneTopY + laneHeight - 9f, pillColour)
            }
        }
        drawList.popClipRect()

        return LaneGeometry(
            laneOriginX = laneOriginX,
            laneTopY = laneTopY,
            laneWidthPx = laneWidthPx,
            laneHeight = laneHeight,
            cutTimes = cutTimes,
            blockBoundsPx = blockBoundsPx
        )
    }
}
