/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.gui.interact.wheel

import com.cobblemon.mod.common.api.gui.blitk
import com.cobblemon.mod.common.client.gui.CobblemonRenderable
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.Tooltip
import net.minecraft.client.sounds.SoundManager
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import org.joml.Vector3f

class InteractWheelButton(
        private val iconResource: ResourceLocation?,
        private val secondaryIconResource: ResourceLocation? = null,
        private val buttonResource: ResourceLocation,
        private val tooltipText: String?,
        x: Int,
        y: Int,
        private val isEnabled: Boolean,
        private val colour: () -> Vector3f?,
        onPress: OnPress,
        private val canHover: (Double, Double) -> Boolean
) : Button(x, y, BUTTON_SIZE, BUTTON_SIZE, Component.literal("Interact"), onPress, DEFAULT_NARRATION), CobblemonRenderable {

    companion object {
        const val BUTTON_SIZE = 69
        const val TEXTURE_HEIGHT = BUTTON_SIZE * 2
        const val ICON_SIZE = 32
        const val ICON_SCALE = 0.5F
        const val ICON_OFFSET = 26.5
    }

    private var passedTicks = 0F
    private val blinkInterval = 35

    override fun renderWidget(context: GuiGraphics, mouseX: Int, mouseY: Int, delta: Float) {
        val matrices = context.pose()
        passedTicks += delta
        blitk(
            matrixStack = matrices,
            texture = buttonResource,
            x = x,
            y = y,
            width = BUTTON_SIZE,
            height = BUTTON_SIZE,
            vOffset = if (isHovered(mouseX.toFloat(), mouseY.toFloat()) && isEnabled) BUTTON_SIZE else 0,
            textureHeight = TEXTURE_HEIGHT,
            alpha = if (isEnabled) 1f else 0.4F
        )

        if(isEnabled && isHovered(mouseX.toFloat(), mouseY.toFloat())){
            tooltipText?.let {
                context.renderTooltip(Minecraft.getInstance().font, Component.translatable(it), mouseX, mouseY)
            }
        }

        if (iconResource != null) {
            val (iconX, iconY) = getIconPosition()
            val colour = this.colour() ?: Vector3f(1F, 1F, 1F)
            blitk(
                matrixStack = matrices,
                texture = iconResource,
                x = iconX,
                y = iconY,
                width = ICON_SIZE,
                height = ICON_SIZE,
                alpha = if (isEnabled) 1F else 0.2F,
                red = colour.x,
                green = colour.y,
                blue = colour.z,
                scale = ICON_SCALE
            )
        }

        if (passedTicks % blinkInterval < blinkInterval / 2) {
            if (secondaryIconResource != null) {
                val (iconX, iconY) = getIconPosition()
                val colour = this.colour() ?: Vector3f(1F, 1F, 1F)
                blitk(
                        matrixStack = matrices,
                        texture = secondaryIconResource,
                        x = iconX,
                        y = (iconY.toFloat() - ICON_SIZE),
                        width = ICON_SIZE,
                        height = ICON_SIZE,
                        alpha = if (isEnabled) 1F else 0.4F,
                        red = colour.x,
                        green = colour.y,
                        blue = colour.z,
                        scale = ICON_SCALE
                )
            }
        }
    }

    private fun getIconPosition(): Pair<Number, Number> {
        return Pair(
            (x + ICON_OFFSET) / ICON_SCALE,
            (y + ICON_OFFSET) / ICON_SCALE
        )
    }

    override fun playDownSound(soundManager: SoundManager) {}

    private fun isHovered(mouseX: Float, mouseY: Float): Boolean {
        val xMin = x.toFloat() + 1
        val xMax = xMin + BUTTON_SIZE - 2
        val yMin = y.toFloat() + 1
        val yMax = yMin + BUTTON_SIZE - 2
        return canHover(mouseX.toDouble(), mouseY.toDouble()) && mouseX in xMin..xMax && mouseY in yMin..yMax
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        return if (isHovered(mouseX.toFloat(), mouseY.toFloat())) super.mouseClicked(mouseX, mouseY, button) else false
    }

    override fun getTooltip(): Tooltip? {
        tooltipText?.let { return Tooltip.create(Component.translatable(it)) } ?: return super.getTooltip()
    }

}