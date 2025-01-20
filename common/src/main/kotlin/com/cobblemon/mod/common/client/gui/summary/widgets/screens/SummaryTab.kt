/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.gui.summary.widgets.screens

import com.cobblemon.mod.common.api.gui.blitk
import com.cobblemon.mod.common.api.text.bold
import com.cobblemon.mod.common.client.CobblemonResources
import com.cobblemon.mod.common.client.gui.CobblemonRenderable
import com.cobblemon.mod.common.client.render.drawScaledText
import com.cobblemon.mod.common.util.cobblemonResource
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.sounds.SoundManager
import net.minecraft.network.chat.MutableComponent
import net.minecraft.resources.ResourceLocation

class SummaryTab(
    pX: Int, pY: Int,
    val label: MutableComponent? = null,
    val icon: ResourceLocation? = null,
    onPress: OnPress
): Button(pX, pY, 50, 13, label, onPress, DEFAULT_NARRATION), CobblemonRenderable {
    private var isActive = false

    override fun renderWidget(context: GuiGraphics, pMouseX: Int, pMouseY: Int, pPartialTicks: Float) {
        val matrices = context.pose()
        if (isActive) {
            blitk(
                matrixStack = matrices,
                texture = cobblemonResource("textures/gui/summary/summary_tab.png"),
                x = x,
                y = y,
                width = width,
                height = height
            )
        }

        if (icon !== null) {
            blitk(
                matrixStack = matrices,
                texture = icon,
                x = (x + 21) / 0.5F,
                y = (y + 3.5) / 0.5F,
                width = 17,
                height = 17,
                scale = 0.5F
            )
        }

        if (label !== null) {
            drawScaledText(
                context = context,
                font = CobblemonResources.DEFAULT_LARGE,
                text = label.bold(),
                x = x + 25,
                y = y + 3,
                centered = true,
                shadow = true
            )
        }
    }

    override fun playDownSound(soundManager: SoundManager) {
    }

    fun toggleTab(state: Boolean = true) {
        isActive = state
    }
}