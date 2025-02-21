/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.gui.pc

import com.cobblemon.mod.common.api.text.bold
import com.cobblemon.mod.common.api.text.font
import com.cobblemon.mod.common.api.text.text
import com.cobblemon.mod.common.client.CobblemonResources
import com.cobblemon.mod.common.client.render.drawScaledText
import com.cobblemon.mod.common.client.storage.ClientBox
import com.cobblemon.mod.common.net.messages.server.storage.pc.RequestRenamePCBoxPacket
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.EditBox
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MutableComponent

class BoxNameWidget(
    private val pX: Int,
    pY: Int,
    text: Component = "BoxNameWidget".text(),
    private val pcGui: PCGUI,
    private val storageWidget: StorageWidget,
): EditBox(Minecraft.getInstance().font, pX, pY, 0, HEIGHT, text) {

    companion object {
        const val HEIGHT = 14
    }

    init {
        setMaxLength(21)
        update()
        setResponder { update() }
    }

    override fun renderWidget(context: GuiGraphics, mouseX: Int, mouseY: Int, delta: Float) {
        if (cursorPosition != value.length) moveCursorToEnd(false)

        drawScaledText(
            context = context,
            text = text(),
            x = x,
            y = y
        )
    }

    override fun setFocused(focused: Boolean) {
        super.setFocused(focused)
        update()
        if (!focused) {
            RequestRenamePCBoxPacket(pcGui.pc.uuid, storageWidget.box, value).sendToServer()
            pcGui.pc.boxes[storageWidget.box].name = if (value.isBlank()) null else Component.literal(value).bold()
            value = ""
        }
    }

    private fun text(): MutableComponent {
        return (if (isFocused) {
            "${value}_".text()
        } else if (value.isEmpty()) {
            getBox().name?: Component.translatable("cobblemon.ui.pc.box.title", storageWidget.box + 1)
        } else {
            value.text()
        }).bold().font(CobblemonResources.DEFAULT_LARGE)
    }

    fun getBox(): ClientBox {
        return pcGui.pc.boxes[storageWidget.box]
    }

    fun update() {
        width = Minecraft.getInstance().font.width(text())
        x = pX - width / 2
    }
}