/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.gui.pc

import com.cobblemon.mod.common.CobblemonSounds
import com.cobblemon.mod.common.api.gui.blitk
import com.cobblemon.mod.common.util.cobblemonResource
import net.minecraft.client.gui.components.Button
import net.minecraft.client.resources.sounds.SimpleSoundInstance
import net.minecraft.client.sounds.SoundManager
import net.minecraft.network.chat.Component

class WallpaperButton(
    pX: Int, pY: Int,
    onPress: OnPress,
    private val pcGui: PCGUI
) : Button(pX, pY, WIDTH, HEIGHT, Component.literal("WallpaperButton"), onPress, DEFAULT_NARRATION) {

    companion object {
        const val WIDTH = 24
        const val HEIGHT = 11

        private val buttonResource = cobblemonResource("textures/gui/pc/pc_wallpaper_button.png")
    }

    override fun renderWidget(context: net.minecraft.client.gui.GuiGraphics, mouseX: Int, mouseY: Int, delta: Float) {
        blitk(
            matrixStack = context.pose(),
            texture = buttonResource,
            x = x,
            y = y,
            width = WIDTH,
            height = HEIGHT,
            vOffset = if (isHovered() || !pcGui.configuration.showParty) HEIGHT else 0,
            textureHeight = HEIGHT * 2
        )
    }

    override fun playDownSound(soundManager: SoundManager) {
        soundManager.play(SimpleSoundInstance.forUI(CobblemonSounds.PC_CLICK, 1.0F))
    }
}