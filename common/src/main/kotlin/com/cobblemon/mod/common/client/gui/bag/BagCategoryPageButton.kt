/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.gui.bag

import com.cobblemon.mod.common.CobblemonSounds
import com.cobblemon.mod.common.api.gui.blitk
import com.cobblemon.mod.common.client.gui.CobblemonRenderable
import com.cobblemon.mod.common.util.cobblemonResource
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.sounds.SoundManager
import net.minecraft.network.chat.Component

class BagCategoryPageButton(
    x: Int,
    y: Int,
    val parent: CobblemonBagScreen,
    val leftButton: Boolean,
    val action: OnPress
): Button(x, y, WIDTH, HEIGHT, Component.empty(), action, DEFAULT_NARRATION), CobblemonRenderable {
    companion object {
        private const val WIDTH = 12
        private const val HEIGHT = 10

        private val LEFT_BUTTON = cobblemonResource("textures/gui/bag/button_left.png")
        private val RIGHT_BUTTON = cobblemonResource("textures/gui/bag/button_right.png")
    }

    var enabled = true

    override fun renderWidget(context: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        blitk(
            matrixStack = context.pose(),
            texture = if (leftButton) LEFT_BUTTON else RIGHT_BUTTON,
            x = x / 0.5F, y = y / 0.5F,
            width = WIDTH * 2, height = HEIGHT * 2,
            textureHeight = 40,
            vOffset = if (enabled) 20 else 0,
            scale = 0.5f
        )
    }

    override fun playDownSound(handler: SoundManager) {}
}