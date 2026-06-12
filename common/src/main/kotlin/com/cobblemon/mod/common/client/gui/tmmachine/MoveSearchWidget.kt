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
import com.cobblemon.mod.common.api.text.bold
import com.cobblemon.mod.common.api.text.text
import com.cobblemon.mod.common.client.CobblemonResources
import com.cobblemon.mod.common.client.gui.pc.TextWidget
import com.cobblemon.mod.common.client.render.drawScaledText
import com.cobblemon.mod.common.util.cobblemonResource
import com.cobblemon.mod.common.util.lang
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.resources.sounds.SimpleSoundInstance
import net.minecraft.network.chat.Component

class MoveSearchWidget(
    private var pX: Int,
    private var pY: Int,
    text: Component = "MoveSearchWidget".text(),
    update: () -> (Unit)
): TextWidget(pX, pY, text = text, update = update) {

    companion object {
        const val ICON_SIZE = 16
        const val SCALE = 0.5F

        private val iconFilterResource = cobblemonResource("textures/gui/tmmachine/icon_search.png")
        private val labelFilter = lang("ui.pokedex.search")
    }

    override fun renderWidget(context: GuiGraphics, mouseX: Int, mouseY: Int, delta: Float) {
        blitk(
            matrixStack = context.pose(),
            x = (x - 10.5) / SCALE,
            y = (y + 2.5) / SCALE,
            texture = iconFilterResource,
            width = ICON_SIZE,
            height = ICON_SIZE,
            vOffset = if (isFocused || !value.isEmpty() || iconHovered(mouseX, mouseY)) ICON_SIZE else 0,
            textureHeight = ICON_SIZE * 2,
            scale = SCALE
        )

        val input = applyTextCursor(value, labelFilter)
        drawScaledText(
            context = context,
            font = CobblemonResources.DEFAULT_LARGE,
            text = input.bold(),
            x = pX,
            y = pY + 2,
            shadow = true
        )

        renderCursor(context, input)
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (iconHovered(mouseX.toInt(), mouseY.toInt())) {
            Minecraft.getInstance().soundManager.play(SimpleSoundInstance.forUI(CobblemonSounds.GUI_CLICK, 1.0F, 0.25F))
            // Simulate clicking on text area if icon is clicked
            if (!isFocused) return super.mouseClicked(x + (width / 2.0), y + (height / 2.0), button)
        }
        return super.mouseClicked(mouseX, mouseY, button)
    }

    fun iconHovered(mouseX: Int, mouseY: Int): Boolean = mouseX >= (x - 10.5) && mouseY >= (y + 2.5)
        && mouseX <= ((x - 10.5) + (ICON_SIZE * SCALE)) && mouseY <= ((y + 2.5) + (ICON_SIZE * SCALE))
}
