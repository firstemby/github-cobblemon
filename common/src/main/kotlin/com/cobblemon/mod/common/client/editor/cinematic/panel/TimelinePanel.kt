/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.editor.cinematic.panel

import com.cobblemon.mod.common.client.editor.cinematic.EditorDiagnostics
import com.cobblemon.mod.common.client.editor.cinematic.EditorLayoutBuilder
import com.cobblemon.mod.common.client.editor.cinematic.EditorTheme
import com.cobblemon.mod.common.client.editor.cinematic.EditorWidgets
import com.cobblemon.mod.common.client.editor.cinematic.SceneEditorOverlay
import com.cobblemon.mod.common.client.editor.cinematic.SceneSelection
import com.cobblemon.mod.common.client.editor.cinematic.SceneSelectionManager
import com.cobblemon.mod.common.client.editor.cinematic.panel.timeline.ActorLane
import com.cobblemon.mod.common.client.editor.cinematic.panel.timeline.ActorLaneInteraction
import com.cobblemon.mod.common.client.editor.cinematic.panel.timeline.CutLaneInteraction
import com.cobblemon.mod.common.client.editor.cinematic.panel.timeline.CutLaneRenderer
import com.cobblemon.mod.common.client.editor.cinematic.panel.timeline.TimeAxis
import com.cobblemon.mod.common.client.editor.cinematic.panel.timeline.TimelineBeatSpan
import com.cobblemon.mod.common.client.editor.cinematic.panel.timeline.TimelinePresentation
import com.cobblemon.mod.common.client.editor.cinematic.panel.timeline.TimelineStyle
import com.cobblemon.mod.common.client.editor.model.cinematic.EditorBeat
import com.cobblemon.mod.common.client.editor.model.cinematic.EditorCutEntry
import com.cobblemon.mod.common.client.editor.model.cinematic.EditorScene
import com.cobblemon.mod.common.client.editor.model.cinematic.actions.AddCutEntry
import imgui.ImGui
import net.minecraft.client.resources.language.I18n
import imgui.flag.ImGuiHoveredFlags
import imgui.flag.ImGuiKey
import imgui.flag.ImGuiStyleVar
import imgui.flag.ImGuiWindowFlags

class TimelinePanel {
    val playhead = TimelinePlayhead()

    private val cutInteraction = CutLaneInteraction()
    private val actorInteraction = ActorLaneInteraction()
    private var pixelsPerSecond = 80f
    private var scrollSeconds = 0f
    private var draggingPlayhead = false
    private var panning = false
    private var framedScene: EditorScene? = null
    private var framedBeatId: String? = null
    private var fitView = true
    private var playing = false
    private val collapsedActors = mutableSetOf<String>()
    private val rulerHeight: Float get() = ImGui.getTextLineHeight() + 12f

    fun followPlayback(playing: Boolean) {
        this.playing = playing
    }

    fun render(scene: EditorScene, selection: SceneSelectionManager) {
        val flags = ImGuiWindowFlags.NoScrollbar or ImGuiWindowFlags.NoScrollWithMouse
        if (!ImGui.begin(I18n.get("cobblemon.cinematic_editor.timeline.title") + EditorLayoutBuilder.WINDOW_TIMELINE, flags)) { ImGui.end(); return }
        EditorDiagnostics.panel("Timeline")
        val beat = scene.beats[selection.getSelectedBeatId() ?: scene.startBeatId]
        if (beat == null) {
            ImGui.textWrapped(I18n.get("cobblemon.cinematic_editor.timeline.select_beat"))
            ImGui.end()
            return
        }
        playhead.setBeatDuration(beat.duration ?: 0f, looping = false)
        val duration = TimelineBeatSpan.spanOf(beat).coerceAtLeast(0.1f)
        val x = ImGui.getCursorScreenPosX()
        val width = ImGui.getContentRegionAvailX()
        val gutter = (ImGui.getFontSize() * 15f).coerceAtLeast(150f).coerceAtMost(width * 0.36f)
        val trackX = x + gutter
        val trackW = (width - gutter - ImGui.getStyle().scrollbarSize).coerceAtLeast(1f)
        if (framedScene !== scene || framedBeatId != beat.id) {
            fitView = true
            framedScene = scene
            framedBeatId = beat.id
            collapsedActors.clear()
        }
        if (fitView) frame(trackX, trackW, duration)
        renderToolbar(scene, beat, selection, trackX, trackW, duration)

        val rulerY = ImGui.getCursorScreenPosY()
        val overviewHeight = ImGui.getTextLineHeight() + 10f
        val showOverview = TimelinePresentation.showOverview(ImGui.getContentRegionAvailY(), ImGui.getTextLineHeight())
        val footerHeight = if (showOverview) overviewHeight else 0f
        if (ImGui.getContentRegionAvailY() < rulerHeight + 16f) {
            ImGui.textDisabled(I18n.get("cobblemon.cinematic_editor.timeline.expand_timeline"))
            ImGui.end()
            return
        }
        val bodyHeight = (ImGui.getContentRegionAvailY() - rulerHeight - footerHeight - 8f).coerceAtLeast(1f)
        var axis = TimeAxis(trackX, trackW, pixelsPerSecond, scrollSeconds)
        handleAxisInput(axis, rulerY, rulerY + rulerHeight, duration, ruler = true)
        if (playing && !panning) scrollSeconds = axis.scrollToFollow(playhead.time)
        axis = TimeAxis(trackX, trackW, pixelsPerSecond, scrollSeconds)
        renderRuler(axis, x, rulerY, duration)
        ImGui.dummy(width, rulerHeight)
        ImGui.pushStyleVar(ImGuiStyleVar.WindowPadding, 0f, 0f)
        val childFlags = ImGuiWindowFlags.AlwaysVerticalScrollbar or ImGuiWindowFlags.NoScrollWithMouse
        ImGui.beginChild("##timelineTracks", width, bodyHeight, false, childFlags)
        ImGui.popStyleVar()
        val bodyTop = ImGui.getWindowPosY()
        handleAxisInput(axis, bodyTop, bodyTop + bodyHeight, duration, ruler = false)
        axis = TimeAxis(trackX, trackW, pixelsPerSecond, scrollSeconds)
        var y = ImGui.getCursorScreenPosY()
        val contentTop = y
        scene.feeds.forEach { feed ->
            val track = beat.cuts[feed.id]?.takeIf { it.entries.isNotEmpty() } ?: return@forEach
            val popupId = "##shotpick${feed.id}"
            TimelineStyle.lane(axis, x, y, CutLaneRenderer.LANE_HEIGHT, duration)
            CutLaneRenderer.renderGutter(scene, beat.id, feed.id, playhead.time, popupId, x, gutter, y)
            CutLaneRenderer.handleShotPickerPopup(scene, beat.id, feed.id, playhead.time, popupId, selection)
            val geometry = CutLaneRenderer.render(scene, beat.id, feed.id, track, selection, duration, axis, y)
            if (!panning) cutInteraction.handle(scene, beat.id, feed.id, track, geometry, selection, duration, axis)
            y += CutLaneRenderer.LANE_HEIGHT
        }
        y = renderCueLane(beat, selection, axis, x, y, duration)
        y = renderKeyframeLane(scene, selection, axis, x, y, duration)
        val actors = ActorLane.render(scene, beat, axis, collapsedActors, x, gutter, y)
        if (!panning) actorInteraction.handle(scene, beat.id, axis, actors.hovered, selection)
        y = actors.bottomY
        renderPlayhead(axis, bodyTop, bodyTop + bodyHeight, false)
        ImGui.setCursorScreenPos(x, contentTop)
        ImGui.dummy(width - ImGui.getStyle().scrollbarSize, (y - contentTop).coerceAtLeast(1f))
        ImGui.endChild()
        if (showOverview) renderOverview(axis, x, width, duration, footerHeight)
        ImGui.end()
    }

    private fun renderToolbar(scene: EditorScene, beat: EditorBeat, selection: SceneSelectionManager,
                              trackX: Float, trackW: Float, duration: Float) {
        val width = ImGui.getContentRegionAvailX()
        ImGui.setNextItemWidth((width * 0.25f).coerceIn(70f, 220f))
        if (ImGui.beginCombo("##timelineBeat", beat.id)) {
            scene.beats.keys.forEach { id ->
                if (ImGui.selectable(id, id == beat.id)) selection.select(SceneSelection.BeatSelected(id))
            }
            ImGui.endCombo()
        }
        EditorDiagnostics.item("timeline.beat")
        EditorWidgets.nextInline(ImGui.calcTextSize(TimelinePresentation.timecode(playhead.time)).x)
        ImGui.textColored(1f, 0.78f, 0.3f, 1f, TimelinePresentation.timecode(playhead.time))
        if (width > ImGui.getFontSize() * 55f) {
            ImGui.sameLine()
            ImGui.textDisabled(if (beat.duration == null) I18n.get("cobblemon.cinematic_editor.timeline.automatic_duration", TimelinePresentation.timecode(duration)) else "/ ${TimelinePresentation.timecode(duration)}")
        }
        EditorWidgets.nextInline(EditorWidgets.buttonWidth(I18n.get("cobblemon.cinematic_editor.timeline.fit")))
        if (ImGui.button(I18n.get("cobblemon.cinematic_editor.timeline.fit"))) { fitView = true; frame(trackX, trackW, duration) }
        EditorDiagnostics.item("timeline.fit")
        if (ImGui.isItemHovered()) ImGui.setTooltip(I18n.get("cobblemon.cinematic_editor.timeline.fit_help"))
        EditorWidgets.nextInline(EditorWidgets.buttonWidth(I18n.get("cobblemon.cinematic_editor.timeline.add_feed")))
        if (ImGui.button(I18n.get("cobblemon.cinematic_editor.timeline.add_feed"))) ImGui.openPopup("##addBeatFeed")
        if (ImGui.beginPopup("##addBeatFeed")) {
            val unusedFeeds = scene.feeds.filter { beat.cuts[it.id]?.entries.isNullOrEmpty() }
            if (unusedFeeds.isEmpty()) ImGui.textDisabled(I18n.get("cobblemon.cinematic_editor.timeline.all_feeds_used"))
            unusedFeeds.forEach { feed ->
                if (ImGui.beginMenu(feed.id)) {
                    if (scene.shots.isEmpty()) ImGui.textDisabled(I18n.get("cobblemon.cinematic_editor.timeline.add_shot_first"))
                    scene.shots.keys.sorted().forEach { shotId ->
                        if (ImGui.menuItem(shotId)) {
                            scene.execute(AddCutEntry(beat.id, feed.id, EditorCutEntry(time = 0f, shotId = shotId)))
                            ImGui.closeCurrentPopup()
                        }
                    }
                    ImGui.endMenu()
                }
            }
            ImGui.endPopup()
        }
        if (width > ImGui.getFontSize() * 40f) {
            ImGui.sameLine()
            if (ImGui.button("-##timelineZoom")) zoom(trackX + trackW / 2f, trackX, trackW, 0.8f, duration)
            ImGui.sameLine()
            if (ImGui.button("+##timelineZoom")) zoom(trackX + trackW / 2f, trackX, trackW, 1.25f, duration)
            ImGui.sameLine()
            ImGui.textDisabled("(?)")
            if (ImGui.isItemHovered()) {
                ImGui.beginTooltip()
                ImGui.textUnformatted(I18n.get("cobblemon.cinematic_editor.timeline.navigation_help"))
                ImGui.endTooltip()
            }
        }
    }

    private fun renderCueLane(beat: EditorBeat, selection: SceneSelectionManager, axis: TimeAxis,
                              gutterX: Float, y: Float, duration: Float): Float {
        val height = TimelineStyle.rowHeight
        TimelineStyle.lane(axis, gutterX, y, height, duration)
        val color = EditorTheme.imguiPacked(EditorTheme.TRANSITION)
        TimelineStyle.label(I18n.get("cobblemon.cinematic_editor.timeline.cues"), gutterX + 10f, y + 7f, axis.originX - gutterX - 18f, color)
        val draw = ImGui.getWindowDrawList()
        draw.pushClipRect(axis.originX, y, axis.originX + axis.widthPx, y + height, true)
        if (beat.cues.isEmpty()) TimelineStyle.label(I18n.get("cobblemon.cinematic_editor.timeline.no_cues"), axis.originX + 10f, y + 7f, axis.widthPx - 20f, TimelineStyle.muted)
        val size = (ImGui.getFontSize() * 0.35f).coerceAtLeast(5f)
        val sorted = beat.cues.sortedBy { it.time }
        sorted.forEachIndexed { i, cue ->
            val cx = axis.timeToX(cue.time)
            TimelineStyle.diamond(cx, y + height / 2f, size, color)
            val nextX = sorted.getOrNull(i + 1)?.let { axis.timeToX(it.time) } ?: (axis.originX + axis.widthPx)
            TimelineStyle.label(cue.id, cx + size + 5f, y + 7f, nextX - cx - size * 2f - 10f, TimelineStyle.text)
        }
        draw.popClipRect()
        if (!panning && ImGui.isWindowHovered() &&
            ImGui.isMouseHoveringRect(axis.originX, y, axis.originX + axis.widthPx, y + height)) {
            val hit = axis.nearestIndex(ImGui.getMousePosX(), beat.cues.map { it.time }, size + 4f)
            if (hit >= 0) {
                val cue = beat.cues[hit]
                ImGui.beginTooltip()
                ImGui.pushTextWrapPos((ImGui.getFontSize() * 28f).coerceAtMost(ImGui.getIO().displaySizeX - 32f))
                ImGui.textWrapped("${cue.id} · ${TimelinePresentation.timecode(cue.time)}")
                ImGui.textWrapped(I18n.get("cobblemon.cinematic_editor.timeline.cue_help"))
                ImGui.popTextWrapPos()
                ImGui.endTooltip()
                if (ImGui.isMouseClicked(0)) {
                    selection.select(SceneSelection.BeatSelected(beat.id))
                    SceneEditorOverlay.seek(cue.time)
                }
            }
        }
        return y + height
    }

    private fun renderKeyframeLane(scene: EditorScene, selection: SceneSelectionManager, axis: TimeAxis,
                                   gutterX: Float, y: Float, duration: Float): Float {
        val shotId = selection.getSelectedShotId() ?: return y
        val data = scene.shots[shotId]?.positionData ?: return y
        if (data.get("type")?.asString != "keyframe") return y
        val keys = data.getAsJsonArray("keyframes") ?: return y
        val times = keys.map { it.asJsonObject.get("time").asFloat }
        val height = TimelineStyle.rowHeight
        TimelineStyle.lane(axis, gutterX, y, height, duration)
        TimelineStyle.label(I18n.get("cobblemon.cinematic_editor.timeline.keys", shotId), gutterX + 10f, y + 7f, axis.originX - gutterX - 20f, EditorTheme.PATH_ORANGE_PACKED)
        val draw = ImGui.getWindowDrawList()
        draw.pushClipRect(axis.originX, y, axis.originX + axis.widthPx, y + height, true)
        times.forEach { TimelineStyle.diamond(axis.timeToX(it), y + height / 2f, 5f, EditorTheme.PATH_ORANGE_PACKED) }
        draw.popClipRect()
        if (!panning && ImGui.isWindowHovered() && ImGui.isMouseClicked(0) &&
            ImGui.isMouseHoveringRect(axis.originX, y, axis.originX + axis.widthPx, y + height)) {
            val hit = axis.nearestIndex(ImGui.getMousePosX(), times, 7f)
            if (hit >= 0) selection.setPendingKeyframeRowFocus(hit)
        }
        return y + height
    }

    private fun frame(trackX: Float, trackW: Float, duration: Float) {
        val fitted = TimeAxis(trackX, trackW, pixelsPerSecond, scrollSeconds).fittedTo(duration)
        pixelsPerSecond = fitted.pixelsPerSecond
        scrollSeconds = fitted.scrollSeconds
    }

    private fun zoom(pivot: Float, trackX: Float, trackW: Float, factor: Float, duration: Float) {
        val axis = TimeAxis(trackX, trackW, pixelsPerSecond, scrollSeconds).zoomedAroundCursor(pivot, factor)
        pixelsPerSecond = axis.pixelsPerSecond
        scrollSeconds = axis.clampedScrollFor(duration)
        fitView = false
    }

    private fun handleAxisInput(axis: TimeAxis, top: Float, bottom: Float, duration: Float, ruler: Boolean) {
        val io = ImGui.getIO()
        val hovered = ImGui.isWindowHovered()
        val mouseX = ImGui.getMousePosX()
        val overTrack = hovered && ImGui.isMouseHoveringRect(axis.originX, top, axis.originX + axis.widthPx, bottom)
        if (hovered && ImGui.getMousePosY() in top..bottom && io.getMouseWheel() != 0f) {
            when {
                io.getKeyCtrl() && overTrack -> zoom(mouseX, axis.originX, axis.widthPx, if (io.getMouseWheel() > 0) 1.15f else 1f / 1.15f, duration)
                io.getKeyShift() -> { fitView = false; scrollSeconds -= io.getMouseWheel() * 60f / pixelsPerSecond }
                !ruler -> ImGui.setScrollY(ImGui.getScrollY() - io.getMouseWheel() * TimelineStyle.rowHeight * 3f)
            }
        }
        if (overTrack && ImGui.isMouseClicked(2)) { panning = true; fitView = false }
        if (panning && ruler) {
            if (ImGui.isMouseDown(2)) scrollSeconds -= io.getMouseDeltaX() / pixelsPerSecond else panning = false
        }
        if (ruler) {
            if (overTrack && ImGui.isMouseClicked(0)) draggingPlayhead = true
            if (draggingPlayhead && ImGui.isMouseDown(0)) SceneEditorOverlay.seek(axis.xToTime(mouseX))
            if (!ImGui.isMouseDown(0)) draggingPlayhead = false
            if (ImGui.isWindowHovered(ImGuiHoveredFlags.ChildWindows) && !io.getWantTextInput() && ImGui.isKeyPressed(ImGuiKey.Home)) {
                fitView = true
                frame(axis.originX, axis.widthPx, duration)
            }
        }
        scrollSeconds = TimeAxis(axis.originX, axis.widthPx, pixelsPerSecond, scrollSeconds).clampedScrollFor(duration)
    }

    private fun renderRuler(axis: TimeAxis, gutterX: Float, y: Float, duration: Float) {
        val draw = ImGui.getWindowDrawList()
        draw.addRectFilled(gutterX, y, axis.originX + axis.widthPx, y + rulerHeight, TimelineStyle.header)
        TimelineStyle.label(I18n.get("cobblemon.cinematic_editor.timeline.tracks"), gutterX + 10f, y + 5f, axis.originX - gutterX - 20f, TimelineStyle.muted)
        draw.pushClipRect(axis.originX, y, axis.originX + axis.widthPx, y + rulerHeight, true)
        val step = axis.niceTickStep(ImGui.getFontSize() * 7f)
        TimelinePresentation.ticks(axis, step / 5f).forEach { t ->
            val x = axis.timeToX(t)
            draw.addLine(x, y + rulerHeight - 4f, x, y + rulerHeight, TimelineStyle.muted)
        }
        TimelinePresentation.ticks(axis, step).forEach { t ->
            val x = axis.timeToX(t)
            draw.addLine(x, y + rulerHeight - 8f, x, y + rulerHeight, TimelineStyle.muted)
            val label = TimelinePresentation.tickLabel(t, step)
            draw.addText(x + 5f, y + 3f, TimelineStyle.muted, label)
        }
        val end = axis.timeToX(duration)
        draw.addLine(end, y, end, y + rulerHeight, TimelineStyle.text)
        draw.popClipRect()
        renderPlayhead(axis, y, y + rulerHeight, true)
    }

    private fun renderPlayhead(axis: TimeAxis, top: Float, bottom: Float, cap: Boolean) {
        val x = axis.timeToX(playhead.time)
        if (x < axis.originX || x > axis.originX + axis.widthPx) return
        val draw = ImGui.getWindowDrawList()
        val color = EditorTheme.imguiPacked(EditorTheme.PLAYHEAD)
        draw.pushClipRect(axis.originX, top, axis.originX + axis.widthPx, bottom, true)
        draw.addLine(x, top, x, bottom, color, 2f)
        if (cap) draw.addTriangleFilled(x - 6f, top, x + 6f, top, x, top + 8f, color)
        draw.popClipRect()
    }

    private fun renderOverview(axis: TimeAxis, x: Float, width: Float, duration: Float, height: Float) {
        val y = ImGui.getCursorScreenPosY()
        val draw = ImGui.getWindowDrawList()
        ImGui.invisibleButton("##timelineOverview", width, height)
        EditorDiagnostics.item("timeline.overview")
        if (ImGui.isItemActive() && ImGui.isMouseDown(0)) {
            fitView = false
            scrollSeconds = TimelinePresentation.overviewScroll((ImGui.getMousePosX() - x) / width, duration, axis)
        }
        if (ImGui.isItemHovered()) ImGui.setTooltip(I18n.get("cobblemon.cinematic_editor.timeline.overview_help"))
        draw.addRectFilled(x, y + 3f, x + width, y + height - 3f, TimelineStyle.background, 3f)
        val left = x + (axis.scrollSeconds / duration).coerceIn(0f, 1f) * width
        val right = x + ((axis.scrollSeconds + axis.widthPx / axis.pixelsPerSecond) / duration).coerceIn(0f, 1f) * width
        draw.addRectFilled(left, y + 3f, right, y + height - 3f, TimelineStyle.header, 3f)
        draw.addRect(left, y + 3f, right, y + height - 3f, TimelineStyle.muted, 3f)
        val marker = x + (playhead.time / duration).coerceIn(0f, 1f) * width
        draw.addLine(marker, y + 3f, marker, y + height - 3f, EditorTheme.imguiPacked(EditorTheme.PLAYHEAD), 2f)
    }
}

class TimelinePlayhead {
    var time: Float = 0f
        private set
    private var duration: Float = 0f
    private var looping: Boolean = false

    val normalized: Float
        get() = if (duration <= 0f) 0f else (time / duration).coerceIn(0f, 1f)

    fun setBeatDuration(duration: Float, looping: Boolean) {
        this.duration = duration.coerceAtLeast(0f)
        this.looping = looping
        this.time = clampOrWrap(time)
    }

    fun setTime(t: Float) { time = clampOrWrap(t) }

    fun step(deltaSeconds: Float) { setTime(time + deltaSeconds) }

    private fun clampOrWrap(t: Float): Float {
        // Automatic beats run unbounded; clamping them to zero freezes the playback cursor.
        if (duration <= 0f) return t.coerceAtLeast(0f)
        return if (looping) {
            var r = t % duration
            if (r < 0f) r += duration
            r
        } else {
            t.coerceIn(0f, duration)
        }
    }
}
