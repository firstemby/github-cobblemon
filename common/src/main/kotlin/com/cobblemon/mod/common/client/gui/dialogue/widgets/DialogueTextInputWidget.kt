/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.gui.dialogue.widgets

import com.cobblemon.mod.common.api.gui.blitk
import com.cobblemon.mod.common.api.gui.drawCenteredText
import com.cobblemon.mod.common.api.text.text
import com.cobblemon.mod.common.client.gui.CobblemonRenderable
import com.cobblemon.mod.common.client.gui.dialogue.DialogueScreen
import com.cobblemon.mod.common.net.messages.client.dialogue.dto.DialogueInputDTO
import com.cobblemon.mod.common.net.messages.server.dialogue.InputToDialoguePacket
import com.cobblemon.mod.common.util.cobblemonResource
import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import org.lwjgl.glfw.GLFW

class DialogueTextInputWidget(
    val dialogueScreen: DialogueScreen,
    x: Int,
    y: Int,
    width: Int,
    height: Int,
    maxLength: Int = 30
) : EditBox(
    Minecraft.getInstance().font,
    x,
    y,
    width,
    height,
    "gui_dialogue_text_input".text()
), CobblemonRenderable {
    init {
        setMaxLength(maxLength)
        isFocused = true
    }

    override fun mouseClicked(mouseButtonEvent: MouseButtonEvent, doubleClick: Boolean): Boolean {
        if (!dialogueScreen.renderInput()) {
            return false
        }
        if (dialogueScreen.dialogueDTO.dialogueInput.inputType != DialogueInputDTO.InputType.TEXT || dialogueScreen.waitingForServerUpdate) {
            return false
        }

        return if (mouseButtonEvent.x().toInt() in x..(x + width) && mouseButtonEvent.y().toInt() in y..(y + height)) {
            isFocused = true
            true
        } else {
            false
        }
    }

    override fun extractWidgetRenderState(context: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        if (!dialogueScreen.renderInput()) {
            return
        }
        if (dialogueScreen.dialogueDTO.dialogueInput.inputType != DialogueInputDTO.InputType.TEXT || dialogueScreen.waitingForServerUpdate) {
            return
        }
        if (cursorPosition != value.length) {
            moveCursorToEnd(false)
        }

        blitk(
            matrixStack = context.pose(),
            x = x,
            y = y,
            width = width,
            height = height,
            texture = cobblemonResource("textures/gui/dialogue/dialogue_text_input.png")
        )

        drawCenteredText(
            context = context,
            text = (if (isFocused) "$value|" else value).text(),
            x = x + width / 2F,
            y = y + (height / 2) - 3,
            shadow = true,
            colour = 0xFFFFFF
        )
    }

    override fun keyPressed(keyEvent: KeyEvent): Boolean {
        val keyCode = keyEvent.key()
        if (keyCode == InputConstants.KEY_ESCAPE || keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            this.dialogueScreen.sendToServer(InputToDialoguePacket(dialogueScreen.dialogueDTO.dialogueInput.inputId, value.trim()))
        }
        return super.keyPressed(keyEvent)
    }
}
