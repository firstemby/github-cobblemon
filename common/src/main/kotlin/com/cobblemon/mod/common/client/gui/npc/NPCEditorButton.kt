/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.gui.npc

import com.cobblemon.mod.common.CobblemonSounds
import com.cobblemon.mod.common.api.gui.blitk
import com.cobblemon.mod.common.api.text.text
import com.cobblemon.mod.common.client.render.drawScaledText
import com.cobblemon.mod.common.util.asTranslated
import com.cobblemon.mod.common.util.cobblemonResource
import com.cobblemon.mod.common.util.lang
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.resources.sounds.SimpleSoundInstance
import net.minecraft.client.sounds.SoundManager
import net.minecraft.network.chat.MutableComponent

class NPCEditorButton(
    var buttonX: Float,
    var buttonY: Float,
    var label: MutableComponent,
    var cycleButtonState: Boolean? = null,
    var buttonWidth: Int = Minecraft.getInstance().font.width(label) + (BUTTON_PADDING * 2),
    val silent: Boolean = false,
    val alignRight: Boolean = false,
    val clickAction: OnPress
): Button(if (alignRight) (buttonX - buttonWidth).toInt() else buttonX.toInt(), buttonY.toInt(), buttonWidth, HEIGHT, "".text(), clickAction, DEFAULT_NARRATION) {

    companion object {
        val HEIGHT = 16
        val BORDER_WIDTH = 2
        val BUTTON_PADDING = 6
        val buttonResource = cobblemonResource("textures/gui/npc/button_base.png")
        val buttonBorderResource = cobblemonResource("textures/gui/npc/button_border.png")
    }

    var isWidgetActive = false

    override fun renderWidget(context: GuiGraphics, mouseX: Int, mouseY: Int, partialTicks: Float) {
        val matrices = context.pose()

        // Left border
        blitk(
            matrixStack = matrices,
            texture = buttonBorderResource,
            x = x,
            y = y,
            width = BORDER_WIDTH,
            height = HEIGHT,
            vOffset = if (isMouseOver(mouseX.toDouble(), mouseY.toDouble()) || isWidgetActive) HEIGHT else 0,
            textureWidth = 3,
            textureHeight = HEIGHT * 2,
        )
        // Base
        blitk(
            matrixStack = matrices,
            texture = buttonResource,
            x = x + BORDER_WIDTH,
            y = y,
            width = buttonWidth - (BORDER_WIDTH * 2),
            height = HEIGHT,
            vOffset = if (isMouseOver(mouseX.toDouble(), mouseY.toDouble()) || isWidgetActive) HEIGHT else 0,
            textureHeight = HEIGHT * 2,
        )
        // Right border
        blitk(
            matrixStack = matrices,
            texture = buttonBorderResource,
            x = x + (buttonWidth - BORDER_WIDTH),
            y = y,
            width = BORDER_WIDTH,
            height = HEIGHT,
            uOffset = 1,
            vOffset = if (isMouseOver(mouseX.toDouble(), mouseY.toDouble()) || isWidgetActive) HEIGHT else 0,
            textureWidth = 3,
            textureHeight = HEIGHT * 2,
        )

        var buttonLabel = if (cycleButtonState != null) "options.${if (cycleButtonState as Boolean) "on" else "off"}.composed".asTranslated(label) else label
        drawScaledText(
            context = context,
            text = buttonLabel,
            x = x + (buttonWidth / 2),
            y = y + 4,
            centered = true,
            shadow = true
        )
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (active && isMouseOver(mouseX, mouseY)) {
            super.mouseClicked(mouseX, mouseY, button)
        }
        return false
    }

    override fun playDownSound(soundManager: SoundManager) {
        if (active && !this.silent) {
            soundManager.play(SimpleSoundInstance.forUI(CobblemonSounds.GUI_CLICK, 1.0F))
        }
    }
}