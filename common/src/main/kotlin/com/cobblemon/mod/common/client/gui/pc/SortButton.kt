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
import com.cobblemon.mod.common.api.pokemon.PokemonSortMode
import com.cobblemon.mod.common.util.cobblemonResource
import net.minecraft.client.gui.components.Button
import net.minecraft.client.resources.sounds.SimpleSoundInstance
import net.minecraft.client.sounds.SoundManager
import net.minecraft.network.chat.Component
import kotlin.math.abs

class SortButton(
    pX: Int, pY: Int,
    onPress: OnPress,
) : Button(pX, pY, WIDTH, HEIGHT, Component.literal("SortButton"), onPress, DEFAULT_NARRATION) {

    var sortMode = PokemonSortMode.NAME

    companion object {
        const val WIDTH = 24
        const val HEIGHT = 11

        private val modeResources = PokemonSortMode.entries.map { cobblemonResource("textures/gui/pc/pc_sort_${it.name.lowercase()}.png") }
        private val buttonResource = cobblemonResource("textures/gui/pc/pc_sort_button.png")
    }

    override fun renderWidget(context: net.minecraft.client.gui.GuiGraphics, mouseX: Int, mouseY: Int, delta: Float) {
        blitk(
            matrixStack = context.pose(),
            texture = buttonResource,
            x = x,
            y = y,
            width = WIDTH,
            height = HEIGHT,
            vOffset = if (isHovered()) HEIGHT else 0,
            textureHeight = HEIGHT * 2
        )

        blitk(
            matrixStack = context.pose(),
            texture = modeResources[sortMode.ordinal],
            x = x,
            y = y,
            width = WIDTH,
            height = HEIGHT
        )
    }

    override fun mouseScrolled(
        mouseX: Double,
        mouseY: Double,
        scrollX: Double,
        scrollY: Double
    ): Boolean {
        if (abs(scrollY) >= .5 && isMouseOver(mouseX, mouseY)) {
            val modes = PokemonSortMode.entries
            var mode = sortMode.ordinal + if (scrollY > 0) 1 else -1
            if (mode < 0) mode = modes.size - 1
            if (mode >= modes.size) mode = 0
            sortMode = modes[mode]
            return true
        }
        return false
    }

    override fun playDownSound(soundManager: SoundManager) {
        soundManager.play(SimpleSoundInstance.forUI(CobblemonSounds.PC_CLICK, 1.0F))
    }
}