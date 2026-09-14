/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.editor.cinematic.panel.inspector

import com.cobblemon.mod.common.api.cinematic.model.FeedKind
import com.cobblemon.mod.common.client.editor.cinematic.EditorTheme
import imgui.ImGui

object FeedLayoutSketch {

    private const val VIEWPORT_W = 160f
    private const val VIEWPORT_H = 90f

    fun render(kind: FeedKind) {
        val drawList = ImGui.getWindowDrawList()
        val x0 = ImGui.getCursorScreenPosX()
        val y0 = ImGui.getCursorScreenPosY()
        val x1 = x0 + VIEWPORT_W
        val y1 = y0 + VIEWPORT_H

        val bg = EditorTheme.FEED_PREVIEW_BG
        val fg = EditorTheme.FEED_PREVIEW_REGION
        val border = EditorTheme.FEED_PREVIEW_BORDER

        drawList.addRectFilled(x0, y0, x1, y1, bg)

        when (kind) {
            is FeedKind.Fullscreen -> drawList.addRectFilled(x0, y0, x1, y1, fg)
            is FeedKind.Pip -> {
                val w = VIEWPORT_W * kind.size
                val h = VIEWPORT_H * kind.size
                val (rx0, ry0) = when (kind.corner) {
                    FeedKind.Corner.TOP_LEFT -> x0 to y0
                    FeedKind.Corner.TOP_RIGHT -> (x1 - w) to y0
                    FeedKind.Corner.BOTTOM_LEFT -> x0 to (y1 - h)
                    FeedKind.Corner.BOTTOM_RIGHT -> (x1 - w) to (y1 - h)
                }
                drawList.addRectFilled(rx0, ry0, rx0 + w, ry0 + h, fg)
            }
            is FeedKind.Split -> {
                val (rx0, ry0, rx1, ry1) = when (kind.side) {
                    FeedKind.Side.LEFT -> floatTuple(x0, y0, x0 + VIEWPORT_W * kind.size, y1)
                    FeedKind.Side.RIGHT -> floatTuple(x1 - VIEWPORT_W * kind.size, y0, x1, y1)
                    FeedKind.Side.TOP -> floatTuple(x0, y0, x1, y0 + VIEWPORT_H * kind.size)
                    FeedKind.Side.BOTTOM -> floatTuple(x0, y1 - VIEWPORT_H * kind.size, x1, y1)
                }
                drawList.addRectFilled(rx0, ry0, rx1, ry1, fg)
            }
            is FeedKind.Diagonal -> {
                when (kind.half) {
                    FeedKind.DiagonalHalf.UPPER -> drawList.addTriangleFilled(
                        x0, y0, x1, y0, x0, y1, fg
                    )
                    FeedKind.DiagonalHalf.LOWER -> drawList.addTriangleFilled(
                        x1, y0, x1, y1, x0, y1, fg
                    )
                }
            }
        }

        drawList.addRect(x0, y0, x1, y1, border)
        ImGui.dummy(VIEWPORT_W, VIEWPORT_H)
    }

    private fun floatTuple(a: Float, b: Float, c: Float, d: Float): FloatTuple4 = FloatTuple4(a, b, c, d)
    private data class FloatTuple4(val a: Float, val b: Float, val c: Float, val d: Float)
}
