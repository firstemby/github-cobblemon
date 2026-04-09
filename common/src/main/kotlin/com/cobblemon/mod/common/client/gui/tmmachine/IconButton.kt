/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.gui.tmmachine

import com.cobblemon.mod.common.CobblemonSounds
import com.cobblemon.mod.common.api.gui.blitk
import com.cobblemon.mod.common.api.text.text
import com.cobblemon.mod.common.client.gui.CobblemonRenderable
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.narration.NarrationElementOutput
import net.minecraft.client.resources.sounds.SimpleSoundInstance
import net.minecraft.client.sounds.SoundManager
import net.minecraft.resources.ResourceLocation

class IconButton(
    var buttonX: Float,
    var buttonY: Float,
    val buttonWidth: Number,
    val buttonHeight: Number,
    var resource: ResourceLocation? = null,
    val scale: Float = 0.5F,
    val silent: Boolean = false,
    val clickAction: OnPress
): Button(buttonX.toInt(), buttonY.toInt(), buttonWidth.toInt(), buttonHeight.toInt(), "".text(), clickAction, DEFAULT_NARRATION), CobblemonRenderable {
    override fun mouseDragged(d: Double, e: Double, i: Int, f: Double, g: Double) = false
    override fun defaultButtonNarrationText(builder: NarrationElementOutput) {}

    override fun renderWidget(context: GuiGraphics, mouseX: Int, mouseY: Int, partialTicks: Float) {
        resource?.let {
            blitk(
                matrixStack = context.pose(),
                texture = it,
                x = buttonX / scale,
                y = buttonY / scale,
                width = buttonWidth,
                height = buttonHeight,
                vOffset = if (isMouseOver(mouseX.toDouble(), mouseY.toDouble()) && active) buttonHeight else 0,
                textureHeight = buttonHeight.toFloat() * 2,
                scale = scale
            )
        }
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (active && visible && button == 0 && isMouseOver(mouseX, mouseY)) {
            playDownSound(Minecraft.getInstance().soundManager)
            onClick(mouseX, mouseY)
            return true
        }
        return false
    }

    override fun isMouseOver(mouseX: Double, mouseY: Double): Boolean {
        val minX = x.toDouble()
        val maxX = minX + width
        val minY = y.toDouble()
        val maxY = minY + (height * scale)
        return active && visible && mouseX >= minX && mouseX < maxX && mouseY >= minY && mouseY < maxY
    }

    override fun playDownSound(soundManager: SoundManager) {
        if (visible && !this.silent) soundManager.play(SimpleSoundInstance.forUI(CobblemonSounds.GUI_CLICK, 1.0F))
    }
}
