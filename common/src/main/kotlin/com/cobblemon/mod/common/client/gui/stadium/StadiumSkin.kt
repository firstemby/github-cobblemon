/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.gui.stadium

import com.mojang.blaze3d.systems.RenderSystem
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.resources.ResourceLocation

object StadiumSkin {
    enum class Surface(val u: Int) { CASE(0), DISPLAY(32), CONTROL(64), SELECTED(96) }

    const val INK = 0xFF29454B.toInt()
    const val TEXT = 0xFFF3F1DF.toInt()
    const val MUTED = 0xFF4B6969.toInt()
    const val FOCUS = 0xFFBD5954.toInt()
    private val atlas = ResourceLocation.fromNamespaceAndPath("cobblemon", "textures/gui/stadium/terminal_controls.png")
    private val frame = ResourceLocation.fromNamespaceAndPath("cobblemon", "textures/gui/stadium/terminal_frame.png")
    private val emblem = ResourceLocation.fromNamespaceAndPath("cobblemon", "textures/gui/stadium/terminal_emblem.png")

    fun emblem(graphics: GuiGraphics, x: Int, y: Int) {
        RenderSystem.enableBlend()
        RenderSystem.defaultBlendFunc()
        graphics.blit(emblem, x, y, 0f, 0f, 16, 12, 16, 12)
        RenderSystem.disableBlend()
    }

    fun frame(graphics: GuiGraphics, x: Int, y: Int, width: Int, height: Int) {
        if (width <= 0 || height <= 0) return
        panel(graphics, Surface.CASE, x + 7, y + 9, width - 15, height - 17)
        val leftWidth = minOf(width, 128)
        val rightWidth = minOf(width - leftWidth, 20)
        val topHeight = minOf(height, 16)
        val bottomHeight = minOf(height - topHeight, 20)
        val sx = intArrayOf(0, 128, 380, 400)
        val sy = intArrayOf(0, 16, 212, 232)
        val dx = intArrayOf(x, x + leftWidth, x + width - rightWidth, x + width)
        val dy = intArrayOf(y, y + topHeight, y + height - bottomHeight, y + height)
        RenderSystem.enableBlend()
        RenderSystem.defaultBlendFunc()
        for (row in 0..2) for (column in 0..2) {
            graphics.blit(frame, dx[column], dy[row], dx[column + 1] - dx[column], dy[row + 1] - dy[row],
                sx[column].toFloat(), sy[row].toFloat(), sx[column + 1] - sx[column], sy[row + 1] - sy[row], 400, 232)
        }
        RenderSystem.disableBlend()
    }

    fun panel(graphics: GuiGraphics, surface: Surface, x: Int, y: Int, width: Int, height: Int, border: Int = 4) {
        if (width <= 0 || height <= 0) return
        val edge = minOf(border, width / 2, height / 2)
        val source = intArrayOf(0, 1, 31, 32)
        val xs = intArrayOf(x, x + edge, x + width - edge, x + width)
        val ys = intArrayOf(y, y + edge, y + height - edge, y + height)
        for (row in 0..2) for (column in 0..2) {
            graphics.blit(atlas, xs[column], ys[row], xs[column + 1] - xs[column], ys[row + 1] - ys[row],
                (surface.u + source[column]).toFloat(), source[row].toFloat(),
                source[column + 1] - source[column], source[row + 1] - source[row], 128, 32)
        }
    }
}
