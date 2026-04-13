/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.gui.stadium.widget

import com.cobblemon.mod.common.api.gui.blitk
import com.cobblemon.mod.common.client.gui.CobblemonRenderable
import com.cobblemon.mod.common.client.render.drawScaledText
import com.cobblemon.mod.common.util.cobblemonResource
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.sounds.SoundManager
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MutableComponent

class StadiumTabWidget(
    pX: Int,
    pY: Int,
    private val label: MutableComponent,
    private val isActive: () -> Boolean,
    onPress: OnPress
) : Button(pX, pY, WIDTH.toInt(), HEIGHT.toInt(), label, onPress, DEFAULT_NARRATION), CobblemonRenderable {

    companion object {
        const val WIDTH = 80F
        const val HEIGHT = 13F
        private val tabTexture = cobblemonResource("textures/gui/stadium/stadium_config_tabs.png")
    }

    override fun renderWidget(context: GuiGraphics, pMouseX: Int, pMouseY: Int, pPartialTicks: Float) {
        val hovered = pMouseX.toFloat() in x.toFloat()..(x + WIDTH) &&
            pMouseY.toFloat() in y.toFloat()..(y + HEIGHT)
        val vOffset = when {
            isActive() -> 0F
            hovered -> HEIGHT
            else -> HEIGHT * 2
        }

        blitk(
            matrixStack = context.pose(),
            texture = tabTexture,
            x = x, y = y,
            width = WIDTH, height = HEIGHT,
            vOffset = vOffset,
            textureHeight = HEIGHT * 3
        )

        drawScaledText(
            context = context,
            text = label,
            x = x + WIDTH.toInt() / 2,
            y = y + 3,
            scale = 0.8f,
            colour = if (isActive()) 0xFFFFFF else if (hovered) 0xCCCCCC else 0x888888,
            centered = true,
            shadow = true
        )
    }

    override fun playDownSound(soundManager: SoundManager) {}
}
