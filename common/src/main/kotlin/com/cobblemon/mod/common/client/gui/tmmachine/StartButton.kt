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
import com.cobblemon.mod.common.util.cobblemonResource
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.narration.NarrationElementOutput
import net.minecraft.client.resources.sounds.SimpleSoundInstance
import net.minecraft.client.sounds.SoundManager

class StartButton(
    var buttonX: Float,
    var buttonY: Float,
    val clickAction: OnPress
): Button(buttonX.toInt(), buttonY.toInt(), WIDTH, HEIGHT, "".text(), clickAction, DEFAULT_NARRATION), CobblemonRenderable {
    companion object {
        const val WIDTH = 22
        const val HEIGHT = 12

        val base = cobblemonResource("textures/gui/tmmachine/button_start.png")
        val baseStop = cobblemonResource("textures/gui/tmmachine/button_stop.png")
        val baseDisabled = cobblemonResource("textures/gui/tmmachine/button_start_disabled.png")

        val iconStart = cobblemonResource("textures/gui/tmmachine/button_icon_start.png")
        val iconStop = cobblemonResource("textures/gui/tmmachine/button_icon_stop.png")
        val iconRepeat = cobblemonResource("textures/gui/tmmachine/button_icon_repeat.png")
    }

    var disabled = false
    var processing = false
    var shouldRepeat = false

    override fun mouseDragged(d: Double, e: Double, i: Int, f: Double, g: Double) = false
    override fun defaultButtonNarrationText(builder: NarrationElementOutput) {}

    override fun renderWidget(context: GuiGraphics, mouseX: Int, mouseY: Int, partialTicks: Float) {
        val resource = if (disabled) baseDisabled
            else (if (processing) baseStop else base)

        blitk(
            matrixStack = context.pose(),
            texture = resource,
            x = buttonX,
            y = buttonY,
            width = WIDTH,
            height = HEIGHT,
            vOffset = if (isMouseOver(mouseX.toDouble(), mouseY.toDouble())) HEIGHT else 0,
            textureHeight = HEIGHT * 2,
        )

        blitk(
            matrixStack = context.pose(),
            texture = if (processing) iconStop else (if (shouldRepeat) iconRepeat else iconStart),
            x = buttonX,
            y = buttonY,
            width = WIDTH,
            height = HEIGHT
        )
    }

    /*override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (active && visible && !disabled && isMouseOver(mouseX, mouseY)) {
            super.mouseClicked(mouseX, mouseY, button)
        }
        return false
    }*/

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (!active || !visible || disabled) return false
        return super.mouseClicked(mouseX, mouseY, button)
    }

    override fun playDownSound(soundManager: SoundManager) {
        if (visible && !disabled) {
            soundManager.play(SimpleSoundInstance.forUI(CobblemonSounds.GUI_CLICK, 1.0F))
        }
    }
}
