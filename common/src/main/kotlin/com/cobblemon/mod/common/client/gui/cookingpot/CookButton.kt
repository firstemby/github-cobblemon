/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.gui.cookingpot

import com.cobblemon.mod.common.api.gui.blitk
import com.cobblemon.mod.common.util.cobblemonResource
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.resources.sounds.SimpleSoundInstance
import net.minecraft.client.sounds.SoundManager
import net.minecraft.network.chat.Component
import net.minecraft.sounds.SoundEvent
import net.minecraft.sounds.SoundEvents

class CookButton(
    pX: Int, pY: Int,
    var selected: Boolean = false,
    onPress: OnPress
): Button(pX, pY, WIDTH.toInt(), HEIGHT.toInt(), Component.literal("Cook"), onPress, DEFAULT_NARRATION) {

    companion object {
        private const val WIDTH = 20F
        private const val HEIGHT = 19F

        private val buttonResource = cobblemonResource("textures/gui/campfirepot/button_cook.png")
        private val buttonSelectedResource = cobblemonResource("textures/gui/campfirepot/button_cook_selected.png")
    }

    override fun renderWidget(context: GuiGraphics, pMouseX: Int, pMouseY: Int, pPartialTicks: Float) {
        blitk(
            matrixStack = context.pose(),
            texture = if (selected) buttonSelectedResource else buttonResource,
            x = x,
            y = y,
            width = WIDTH,
            height = HEIGHT,
            vOffset = if (isMouseOver(pMouseX.toDouble(), pMouseY.toDouble())) HEIGHT else 0,
            textureHeight = HEIGHT * 2
        )
    }

    override fun playDownSound(soundManager: SoundManager) {
        Minecraft.getInstance().soundManager.play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F))
    }
}