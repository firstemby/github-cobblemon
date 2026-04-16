/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.gui.trade

import com.cobblemon.mod.common.api.gui.blitk
import com.cobblemon.mod.common.client.gui.CobblemonRenderable
import com.cobblemon.mod.common.client.gui.drawProfilePokemon
import com.cobblemon.mod.common.client.render.drawScaledText
import com.cobblemon.mod.common.client.render.models.blockbench.FloatingState
import com.cobblemon.mod.common.client.render.renderScaledGuiItemIcon
import com.cobblemon.mod.common.net.messages.client.trade.TradeStartedPacket
import com.cobblemon.mod.common.pokemon.Gender
import com.cobblemon.mod.common.util.cobblemonResource
import com.cobblemon.mod.common.util.lang
import com.cobblemon.mod.common.util.math.fromEulerXYZDegrees
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.Button
import net.minecraft.client.sounds.SoundManager
import net.minecraft.network.chat.Component
import org.joml.Quaternionf
import org.joml.Vector3f

open class PartySlot(
    x: Int, y: Int,
    private val pokemon: TradeStartedPacket.TradeablePokemon?,
    private val parent: TradeGUI,
    private val isOpposing: Boolean = false,
    onPress: OnPress
) : Button(x, y, SIZE, SIZE, Component.literal("PartySlot"), onPress, DEFAULT_NARRATION), CobblemonRenderable {
    val state = FloatingState()
    companion object {
        const val SIZE = 25

        private val hoverBackgroundResource = cobblemonResource("textures/gui/trade/trade_party_slot_hover.png")
        private val genderIconMale = cobblemonResource("textures/gui/pc/gender_icon_male.png")
        private val genderIconFemale = cobblemonResource("textures/gui/pc/gender_icon_female.png")
        private val selectPointerResource = cobblemonResource("textures/gui/pc/pc_pointer.png")
        private val untradeableResource = cobblemonResource("textures/gui/trade/trade_slot_icon_locked.png")
    }

    override fun playDownSound(soundManager: SoundManager) {
    }

    override fun extractContents(context: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        val matrices = context.pose()
        if (!isOpposing && isHovered(mouseX, mouseY)) {
            blitk(
                matrixStack = matrices,
                texture = hoverBackgroundResource,
                x = x,
                y = y,
                width = SIZE,
                height = SIZE
            )
        }

        if (pokemon != null) {
            drawProfilePokemon(
                renderablePokemon = pokemon.asRenderablePokemon(),
                context = context,
                left = x - 4,
                top = y - 8,
                right = x + SIZE + 4,
                bottom = y + SIZE + 4,
                anchorX = x + SIZE / 2F,
                anchorY = y + 1F,
                rotation = Quaternionf().fromEulerXYZDegrees(Vector3f(13F, 35F, 0F)),
                state = state,
                scale = 4.5F,
                partialTicks = delta
            )
            // Level
            drawScaledText(
                context = context,
                text = lang("ui.lv.number", pokemon.level),
                x = x + 1,
                y = y + 1,
                shadow = true,
                scale = TradeGUI.SCALE
            )

            if (pokemon.gender != Gender.GENDERLESS) {
                blitk(
                    matrixStack = matrices,
                    texture = if (pokemon.gender == Gender.MALE) genderIconMale else genderIconFemale,
                    x = (x + 21) / TradeGUI.SCALE,
                    y = (y + 1) / TradeGUI.SCALE,
                    width = 6,
                    height = 8,
                    scale = TradeGUI.SCALE
                )
            }
            if (!pokemon.tradeable) {
                blitk(
                    matrixStack = matrices,
                    texture = untradeableResource,
                    x = (x + 8) / TradeGUI.SCALE,
                    y = (y + 8) / TradeGUI.SCALE,
                    width = 20,
                    height = 20,
                    scale = TradeGUI.SCALE
                )
            }
            if (hasSelected()) {
                blitk(
                    matrixStack = matrices,
                    texture = selectPointerResource,
                    x = (x + 10) / TradeGUI.SCALE,
                    y = ((y - 3) / TradeGUI.SCALE) - parent.selectPointerOffsetY,
                    width = 11,
                    height = 8,
                    scale = TradeGUI.SCALE
                )
            }

            // Held Item
            val heldItem = pokemon.heldItem
            if (!heldItem.isEmpty) {
                renderScaledGuiItemIcon(
                    context = context,
                    itemStack = heldItem,
                    x = x + 16.0,
                    y = y + 16.0,
                    scale = 0.5
                )
            }
        }
    }

    open fun hasSelected(): Boolean {
        val offeredPokemon = if (isOpposing) parent.trade.oppositeOffer.get() else parent.trade.myOffer.get()
        return pokemon?.pokemonId == offeredPokemon?.uuid && pokemon != null
    }

    fun isHovered(mouseX: Int, mouseY: Int) = pokemon?.tradeable != false && mouseX.toFloat() in (x.toFloat()..(x.toFloat() + SIZE)) && mouseY.toFloat() in (y.toFloat()..(y.toFloat() + SIZE))
}
