/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.gui.pc

import com.cobblemon.mod.common.api.gui.blitk
import com.cobblemon.mod.common.api.text.bold
import com.cobblemon.mod.common.api.text.text
import com.cobblemon.mod.common.client.CobblemonResources
import com.cobblemon.mod.common.client.render.drawScaledText
import com.cobblemon.mod.common.util.cobblemonResource
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.EditBox
import net.minecraft.network.chat.Component

class SearchWidget(
    private var pX: Int,
    private var pY: Int,
    text: Component = "SearchWidget".text(),
    update: () -> (Unit)
): EditBox(Minecraft.getInstance().font, pX, pY, WIDTH, HEIGHT, text) {

    companion object {
        const val WIDTH = 110
        const val HEIGHT = 14
        const val HINT_COLOUR = 0x8D8D8D
        private val hint = Component.translatable("cobblemon.ui.pc.search")
        private val searchIcon = cobblemonResource("textures/gui/pc/pc_search_icon.png")
    }

    init {
        setMaxLength(21)
        setResponder { update() }
    }

    override fun renderWidget(context: GuiGraphics, mouseX: Int, mouseY: Int, delta: Float) {
        if (cursorPosition != value.length) moveCursorToEnd(false)
        val input = if (isFocused) "${value}_".text() else value.text()
        val useHint = !isFocused && value.isEmpty()

        blitk(
            matrixStack = context.pose(),
            texture = searchIcon,
            x = pX + 1,
            y = pY + 3,
            width = 8,
            height = 8
        )

        drawScaledText(
            context = context,
            font = CobblemonResources.DEFAULT_LARGE,
            text = if (useHint) hint.bold() else input.bold(),
            x = pX + if (useHint) 55 else 14,
            y = pY + 2,
            colour = if (useHint) HINT_COLOUR else 0xFFFFFF,
            centered = useHint
        )
    }
}