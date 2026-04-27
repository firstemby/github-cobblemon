/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.gui.interact.moveselect

import com.cobblemon.mod.common.api.gui.blitk
import com.cobblemon.mod.common.api.moves.MoveTemplate
import com.cobblemon.mod.common.api.text.bold
import com.cobblemon.mod.common.api.text.gold
import com.cobblemon.mod.common.api.text.red
import com.cobblemon.mod.common.api.text.text
import com.cobblemon.mod.common.client.CobblemonResources
import com.cobblemon.mod.common.client.gui.CobblemonRenderable
import com.cobblemon.mod.common.client.gui.MoveCategoryIcon
import com.cobblemon.mod.common.client.gui.TypeIcon
import com.cobblemon.mod.common.client.render.drawScaledText
import com.cobblemon.mod.common.util.cobblemonResource
import com.cobblemon.mod.common.util.lang
import com.cobblemon.mod.common.util.math.toRGB
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.sounds.SoundManager
import net.minecraft.network.chat.Component
import net.minecraft.util.Mth

class MoveSlotButton(
    x: Int, y: Int,
    var move: MoveTemplate? = null,
    var pp: Int = 0,
    var ppMax: Int = 0,
    var ppAsFraction: Boolean = true,
    var enabled: Boolean = true,
    var showOverlayBar: Boolean = true,
    onPress: OnPress
) : Button(x, y, WIDTH, HEIGHT, move?.name?.text() ?: lang("ui.moves"), onPress, CreateNarration { move?.name?.text() ?: lang("ui.moves") }), CobblemonRenderable {

    companion object {
        val moveResource = cobblemonResource("textures/gui/summary/summary_move.png")
        val moveDisabledResource = cobblemonResource("textures/gui/summary/summary_move_disabled.png")
        val moveOverlayResource = cobblemonResource("textures/gui/summary/summary_move_overlay.png")
        val moveOverlayBarResource = cobblemonResource("textures/gui/summary/summary_move_overlay_bar.png")

        const val WIDTH = 108
        const val HEIGHT = 22
    }

    override fun renderWidget(context: GuiGraphics, mouseX: Int, mouseY: Int, pPartialTicks: Float) {
        move?.let {
            isHovered = isMouseOver(mouseX.toDouble(), mouseY.toDouble()) && enabled
            val rgb = it.elementalType.hue.toRGB()
            val alpha = if (enabled) 1.0F else 0.7F
            val matrices = context.pose()

            blitk(
                matrixStack = matrices,
                texture = if (enabled) moveResource else moveDisabledResource,
                x = x,
                y = y,
                width = WIDTH,
                height = HEIGHT,
                vOffset = if (isHovered) HEIGHT else 0,
                textureHeight = HEIGHT * 2,
                red = rgb.first,
                green = rgb.second,
                blue = rgb.third
            )

            blitk(
                matrixStack = matrices,
                texture = moveOverlayResource,
                x = x,
                y = y,
                width = WIDTH,
                height = HEIGHT
            )

            if (showOverlayBar) {
                blitk(
                    matrixStack = matrices,
                    texture = moveOverlayBarResource,
                    x = x + 60,
                    y = y + 13,
                    width = 47,
                    height = 8
                )

                if (pp != -1 && ppMax != -1) {
                    var movePPText = lang("ui.moves.pp", pp).bold()
                    if (ppAsFraction) {
                        movePPText = Component.literal("$pp/$ppMax").bold()

                        if (pp <= Mth.floor(ppMax / 2F)) {
                            movePPText = if (pp == 0) movePPText.red() else movePPText.gold()
                        }
                    }

                    drawScaledText(
                        context = context,
                        font = CobblemonResources.DEFAULT_LARGE,
                        text = movePPText,
                        x = x + 93,
                        y = y + 13,
                        centered = true,
                        opacity = alpha
                    )
                }

                // Move Category
                MoveCategoryIcon(x = x + 66, y = y + 13.5, category = it.damageCategory, opacity = alpha).render(context)
            }

            // Type Icon
            TypeIcon(x = x + 2, y = y + 2, type = it.elementalType, opacity = alpha).render(context)

            // Move Name
            drawScaledText(
                context = context,
                font = CobblemonResources.DEFAULT_LARGE,
                text = it.displayName.bold(),
                x = x + 28,
                y = y + 2,
                shadow = true,
                opacity = alpha
            )
        }
    }

    override fun playDownSound(soundManager: SoundManager) {}
}
