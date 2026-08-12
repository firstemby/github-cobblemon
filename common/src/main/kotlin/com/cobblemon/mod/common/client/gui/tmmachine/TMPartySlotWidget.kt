/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.gui.tmmachine

import com.cobblemon.mod.common.api.gui.blitk
import com.cobblemon.mod.common.client.gui.drawProfilePokemon
import com.cobblemon.mod.common.client.render.drawScaledText
import com.cobblemon.mod.common.client.render.drawScaledTextJustifiedRight
import com.cobblemon.mod.common.client.render.models.blockbench.FloatingState
import com.cobblemon.mod.common.client.render.renderScaledGuiItemIcon
import com.cobblemon.mod.common.pokemon.Gender
import com.cobblemon.mod.common.pokemon.Pokemon
import com.cobblemon.mod.common.util.cobblemonResource
import com.cobblemon.mod.common.util.lang
import com.cobblemon.mod.common.util.math.fromEulerXYZDegrees
import net.minecraft.client.gui.components.Button
import net.minecraft.client.sounds.SoundManager
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MutableComponent
import net.minecraft.resources.ResourceLocation
import org.joml.Quaternionf
import org.joml.Vector3f

class TMPartySlotWidget(
        pX: Number,
        pY: Number,
        val pokemon: Pokemon?,
        onPress: OnPress
) : Button(pX.toInt(), pY.toInt(), WIDTH, HEIGHT, Component.literal("PartyMember"), onPress, DEFAULT_NARRATION) {
    val state = FloatingState()
    var teachable: Int? = null
    var clickable: Boolean = true

    companion object {
        const val WIDTH = 57
        const val HEIGHT = 31
        private const val PORTRAIT_DIAMETER = 30
        const val HALF_SCALE = 0.5F

        const val CAN_LEARN = 0
        const val CANNOT_LEARN = 1
        const val LEARNED = 2

        private val slotResource = cobblemonResource("textures/gui/tmmachine/party_slot.png")
        private val slotLearnableResource = cobblemonResource("textures/gui/tmmachine/party_slot_learnable.png")
        private val slotLearnableDisabledResource = cobblemonResource("textures/gui/tmmachine/party_slot_learnable_disabled.png")
        private val slotDisabledResource = cobblemonResource("textures/gui/tmmachine/party_slot_disabled.png")
        private val slotLearnedResource = cobblemonResource("textures/gui/tmmachine/party_slot_learned.png")
        val genderIconMale = cobblemonResource("textures/gui/pc/gender_icon_male.png")
        val genderIconFemale = cobblemonResource("textures/gui/pc/gender_icon_female.png")

        private val canLearnLabel = lang("ui.tm_machine.can_learn")
        private val cannotLearnLabel = lang("ui.tm_machine.cannot_learn")
        private val learnedLabel = lang("ui.tm_machine.learned")
    }

    private fun getResource(): ResourceLocation = when (teachable) {
        CAN_LEARN -> if (clickable) slotLearnableResource else slotLearnableDisabledResource
        CANNOT_LEARN -> slotDisabledResource
        LEARNED -> slotLearnedResource
        else -> slotResource
    }

    private fun getLabel(): MutableComponent? = when (teachable) {
        CAN_LEARN -> canLearnLabel
        CANNOT_LEARN -> cannotLearnLabel
        LEARNED -> learnedLabel
        else -> null
    }

    override fun renderWidget(context: net.minecraft.client.gui.GuiGraphics, mouseX: Int, mouseY: Int, delta: Float) {
        state.currentAspects = pokemon?.aspects ?: emptySet()
        val matrices = context.pose()

        if (pokemon != null) {
            blitk(
                matrixStack = matrices,
                texture = getResource(),
                x = x,
                y = y,
                width = width,
                height = height,
                vOffset = if (clickable && isMouseOver(mouseX.toDouble(), mouseY.toDouble())) height else 0,
                textureHeight = height * 2,
            )

            val ballIcon = cobblemonResource("textures/gui/ball/" + pokemon.caughtBall.name.path + ".png")
            val ballHeight = 22
            blitk(
                matrixStack = matrices,
                texture = ballIcon,
                x = (x - 2) / HALF_SCALE,
                y = (y - 3) / HALF_SCALE,
                height = ballHeight,
                width = 18,
                textureHeight = ballHeight * 2,
                scale = HALF_SCALE
            )

            // Render Pokémon
            matrices.pushPose()
            matrices.translate(x + (PORTRAIT_DIAMETER / 2.0) - 2, y - 1.0, 0.0)
            matrices.scale(2.5F, 2.5F, 1F)
            drawProfilePokemon(
                species = pokemon.species.resourceIdentifier,
                matrixStack = matrices,
                rotation = Quaternionf().fromEulerXYZDegrees(Vector3f(13F, 35F, 0F)),
                state = state,
                scale = 4.5F,
                partialTicks = delta
            )
            matrices.popPose()

            drawScaledTextJustifiedRight(
                context = context,
                text = lang("ui.lv.number", pokemon.level),
                x = x + 54,
                y = y + 13,
                shadow = true,
                scale = HALF_SCALE
            )

            if (pokemon.gender != Gender.GENDERLESS) {
                blitk(
                    matrixStack = matrices,
                    texture = if (pokemon.gender == Gender.MALE) genderIconMale else genderIconFemale,
                    x = (x + 51) / HALF_SCALE,
                    y = (y + 19) / HALF_SCALE,
                    height = 8,
                    width = 6,
                    scale = HALF_SCALE
                )
            }

            drawScaledTextJustifiedRight(
                context = context,
                text = pokemon.getDisplayName(),
                x = x + ( if (pokemon.gender != Gender.GENDERLESS) 50 else 54),
                y = y + 19,
                shadow = true,
                scale = HALF_SCALE
            )

            getLabel()?.let { label ->
                drawScaledText(
                    context = context,
                    text = label,
                    x = x + (width / 2F),
                    y = y + 26,
                    centered = true,
                    scale = HALF_SCALE
                )
            }

            // Held Item
            val heldItem = pokemon.heldItemNoCopy()
            if (!heldItem.isEmpty) {
                renderScaledGuiItemIcon(
                    itemStack = heldItem,
                    x = x + 15.0,
                    y = y + 13.5,
                    scale = HALF_SCALE.toDouble(),
                    matrixStack = matrices
                )
            }
        } else {
            blitk(
                matrixStack = matrices,
                texture = slotDisabledResource,
                x = x + 55,
                y = y,
                width = 5,
                height = height,
                textureWidth = width,
                textureHeight = height * 2
            )
        }
    }

    override fun playDownSound(soundManager: SoundManager) {}
}
