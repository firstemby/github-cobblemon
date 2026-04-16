/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.gui.pokedex

import com.cobblemon.mod.common.CobblemonSounds
import com.cobblemon.mod.common.api.gui.blitk
import com.cobblemon.mod.common.api.text.text
import com.cobblemon.mod.common.client.gui.CobblemonRenderable
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.narration.NarrationElementOutput
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.client.resources.sounds.SimpleSoundInstance
import net.minecraft.client.sounds.SoundManager
import net.minecraft.resources.Identifier

class ScaledButton(
    buttonX: Float,
    buttonY: Float,
    val buttonWidth: Number,
    val buttonHeight: Number,
    var resource: Identifier? = null,
    val scale: Float = 0.5F,
    val silent: Boolean = false,
    val clickAction: Button.OnPress
): Button(buttonX.toInt(), buttonY.toInt(), buttonWidth.toInt(), buttonHeight.toInt(), "".text(), clickAction, DEFAULT_NARRATION), CobblemonRenderable {

    var isWidgetActive = false

    override fun mouseDragged(mouseButtonEvent: MouseButtonEvent, deltaX: Double, deltaY: Double) = false
    override fun defaultButtonNarrationText(builder: NarrationElementOutput) {
    }

    override fun extractContents(context: GuiGraphicsExtractor, pMouseX: Int, pMouseY: Int, pPartialTicks: Float) {
        val matrices = context.pose()
        if (visible && resource != null) {
            blitk(
                matrixStack = matrices,
                texture = resource,
                x = x / scale,
                y = y / scale,
                width = buttonWidth,
                height = buttonHeight,
                vOffset = if (isButtonHovered(pMouseX, pMouseY) || isWidgetActive) buttonHeight else 0,
                textureHeight = buttonHeight.toFloat() * 2,
                scale = scale
            )
        }
    }

    override fun mouseClicked(mouseButtonEvent: MouseButtonEvent, doubleClick: Boolean): Boolean {
        if (visible && active && isButtonHovered(mouseButtonEvent.x(), mouseButtonEvent.y())) {
            return super.mouseClicked(mouseButtonEvent, doubleClick)
        }
        return false
    }

    override fun playDownSound(soundManager: SoundManager) {
        if (visible && active && !this.silent) {
            soundManager.play(SimpleSoundInstance.forUI(CobblemonSounds.POKEDEX_CLICK_SHORT, 1.0F))
        }
    }

    fun isButtonHovered(mouseX: Number, mouseY: Number): Boolean {
        return mouseX.toFloat() in (x.toFloat()..(x + (buttonWidth.toFloat() * scale)))
                && mouseY.toFloat() in (y.toFloat()..(y + (buttonHeight.toFloat() * scale)))
    }
}
