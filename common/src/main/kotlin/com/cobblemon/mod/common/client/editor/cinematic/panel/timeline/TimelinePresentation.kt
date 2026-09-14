/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.editor.cinematic.panel.timeline

import com.cobblemon.mod.common.client.editor.cinematic.EditorTheme
import com.cobblemon.mod.common.client.editor.cinematic.panel.inspector.ActorTrackTypes
import imgui.ImGui
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.log10
import kotlin.math.roundToLong

object TimelinePresentation {
    fun compactClips(trackAreaHeight: Float, textHeight: Float): Boolean = trackAreaHeight < textHeight * 4f

    fun showOverview(availableHeight: Float, textHeight: Float): Boolean =
        availableHeight >= (textHeight + 12f) + (textHeight + 10f) + (textHeight * 2f + 18f) + 8f

    fun timecode(seconds: Float): String {
        val millis = (seconds.coerceAtLeast(0f).toDouble() * 1000).roundToLong()
        return String.format(Locale.ROOT, "%02d:%02d.%03d", millis / 60000, millis / 1000 % 60, millis % 1000)
    }

    fun trackName(type: String): String =
        ActorTrackTypes.entries().firstOrNull { it.trackType == type }?.label ?: type

    fun tickLabel(seconds: Float, step: Float): String {
        val decimals = ceil(-log10(step.coerceAtLeast(0.001f))).toInt().coerceIn(0, 3)
        return String.format(Locale.ROOT, "%.${decimals}fs", seconds)
    }

    fun ticks(axis: TimeAxis, step: Float): List<Float> {
        if (!step.isFinite() || step <= 0f) return emptyList()
        val (start, end) = axis.visibleRange()
        val first = ceil(start.coerceAtLeast(0f) / step).toInt()
        val last = (end / step).toInt()
        if (last < first) return emptyList()
        return (first..last.coerceAtMost(first + 1000)).map { it * step }
    }

    fun overviewScroll(fraction: Float, duration: Float, axis: TimeAxis): Float =
        axis.copy(scrollSeconds = fraction.coerceIn(0f, 1f) * duration - axis.widthPx / axis.pixelsPerSecond / 2f)
            .clampedScrollFor(duration)
}

object TimelineStyle {
    val background = EditorTheme.imguiPacked(0xFF_171B24.toInt())
    val header = EditorTheme.imguiPacked(0xFF_202632.toInt())
    val border = EditorTheme.imguiPacked(0xFF_303847.toInt())
    val muted = EditorTheme.imguiPacked(0xFF_8C98AA.toInt())
    val text = EditorTheme.imguiPacked(0xFF_DDE4F0.toInt())
    val shotFill = EditorTheme.imguiPacked(0xFF_37304F.toInt())
    val shotSelected = EditorTheme.imguiPacked(0xFF_59437D.toInt())
    val actorFill = EditorTheme.imguiPacked(0xFF_25483F.toInt())
    val rowHeight: Float get() = ImGui.getTextLineHeight() + 14f
    val compactClips: Boolean get() = TimelinePresentation.compactClips(ImGui.getWindowHeight(), ImGui.getTextLineHeight())
    val clipHeight: Float get() = if (compactClips) rowHeight else ImGui.getTextLineHeight() * 2f + 18f

    fun label(value: String, x: Float, y: Float, width: Float, color: Int = text) {
        if (width <= 0f) return
        var fitted = value
        if (ImGui.calcTextSize(fitted).x > width) {
            while (fitted.isNotEmpty() && ImGui.calcTextSize("$fitted…").x > width) fitted = fitted.dropLast(1)
            fitted = if (fitted.isEmpty()) "" else "$fitted…"
        }
        ImGui.getWindowDrawList().addText(x, y, color, fitted)
    }

    fun lane(axis: TimeAxis, gutterX: Float, y: Float, height: Float, duration: Float) {
        val draw = ImGui.getWindowDrawList()
        draw.addRectFilled(gutterX, y, axis.originX, y + height, header)
        draw.addRectFilled(axis.originX, y, axis.originX + axis.widthPx, y + height, background)
        draw.addLine(gutterX, y + height, axis.originX + axis.widthPx, y + height, border)
        draw.addLine(axis.originX - 1f, y, axis.originX - 1f, y + height, border)
        draw.pushClipRect(axis.originX, y, axis.originX + axis.widthPx, y + height, true)
        val step = axis.niceTickStep(ImGui.getFontSize() * 7f)
        TimelinePresentation.ticks(axis, step).forEach { t ->
            val x = axis.timeToX(t)
            draw.addLine(x, y, x, y + height, border)
        }
        val end = axis.timeToX(duration).coerceIn(axis.originX, axis.originX + axis.widthPx)
        draw.addRectFilled(end, y, axis.originX + axis.widthPx, y + height, 0x66000000)
        draw.popClipRect()
    }

    fun diamond(x: Float, y: Float, radius: Float, color: Int) {
        val draw = ImGui.getWindowDrawList()
        draw.addQuadFilled(x, y - radius, x + radius, y, x, y + radius, x - radius, y, color)
    }
}
