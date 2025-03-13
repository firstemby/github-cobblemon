/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.gui.summary.widgets.screens.transformations

import com.cobblemon.mod.common.CobblemonSounds
import com.cobblemon.mod.common.api.gui.blitk
import com.cobblemon.mod.common.api.pokemon.transformation.Transformation
import com.cobblemon.mod.common.api.pokemon.transformation.TransformationDisplay
import com.cobblemon.mod.common.api.pokemon.transformation.proxy.TransformationProxy
import com.cobblemon.mod.common.api.text.bold
import com.cobblemon.mod.common.client.CobblemonResources
import com.cobblemon.mod.common.client.gui.TypeIcon
import com.cobblemon.mod.common.client.gui.drawProfilePokemon
import com.cobblemon.mod.common.client.gui.summary.SummaryButton
import com.cobblemon.mod.common.client.gui.summary.widgets.common.SummaryScrollList
import com.cobblemon.mod.common.client.render.drawScaledText
import com.cobblemon.mod.common.client.render.models.blockbench.FloatingState
import com.cobblemon.mod.common.pokemon.FormData
import com.cobblemon.mod.common.pokemon.Pokemon
import com.cobblemon.mod.common.util.cobblemonResource
import com.cobblemon.mod.common.util.math.fromEulerXYZDegrees
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.resources.sounds.SimpleSoundInstance
import net.minecraft.network.chat.MutableComponent
import net.minecraft.sounds.SoundEvent
import org.joml.Quaternionf
import org.joml.Vector3f

abstract class EvolutionSelectScreen<C : TransformationDisplay, S : Transformation>(
    x: Int,
    y: Int,
    lang: MutableComponent,
    val pokemon: Pokemon,
): SummaryScrollList<EvolutionSelectScreen.TransformationSlot<C, S>>(
    x,
    y,
    lang,
    SLOT_HEIGHT + SLOT_SPACING
) {
    companion object {
        const val SLOT_HEIGHT = 25
        const val SLOT_SPACING = 5
        const val PORTRAIT_DIAMETER = 25

        private val slotResource = cobblemonResource("textures/gui/summary/summary_evolve_slot.png")
        private val buttonResource = cobblemonResource("textures/gui/summary/summary_evolve_select_button.png")
    }

    abstract fun getSlots(pokemon: Pokemon): Collection<TransformationSlot<C, S>>

    private var entriesCreated = false

    public override fun addEntry(entry: TransformationSlot<C, S>): Int {
        return super.addEntry(entry)
    }

    override fun renderWidget(context: GuiGraphics, mouseX: Int, mouseY: Int, partialTicks: Float) {
        if (!entriesCreated) {
            entriesCreated = true
            getSlots(pokemon).forEach { entry -> this.addEntry(entry) }
        }
        super.renderWidget(context, mouseX, mouseY, partialTicks)
    }

    abstract class TransformationSlot<C : TransformationDisplay, S : Transformation>(
        private val pokemon: Pokemon,
        private val display: C,
        text: MutableComponent
    ) : Entry<TransformationSlot<C, S>>() {
        val client: Minecraft = Minecraft.getInstance()
        val state = FloatingState()
        val form: FormData = display.species.getForm(display.aspects)
        val selectButton: SummaryButton = SummaryButton(
            buttonX = 0F,
            buttonY = 0F,
            buttonWidth = 40,
            buttonHeight = 10,
            clickAction = {
                Minecraft.getInstance().player?.clientSideCloseContainer()
                playSound(CobblemonSounds.GUI_CLICK)
                getProxy(pokemon).client().start(this.display)
            },
            text = text,
            resource = buttonResource,
            boldText = true,
            largeText = false,
            textScale = 0.5F
        )

        abstract fun getProxy(pokemon: Pokemon): TransformationProxy<C, S, *, *>

        fun playSound(soundEvent: SoundEvent) {
            Minecraft.getInstance().soundManager.play(SimpleSoundInstance.forUI(soundEvent, 1.0F))
        }

        override fun getNarration() = display.species.translatedName

        override fun render(
            context: GuiGraphics,
            index: Int,
            rowTop: Int,
            rowLeft: Int,
            rowWidth: Int,
            rowHeight: Int,
            mouseX: Int,
            mouseY: Int,
            isHovered: Boolean,
            partialTicks: Float
        ) {
            val x = rowLeft - 3
            val y = rowTop
            val matrices = context.pose()

            state.currentAspects = display.aspects

            blitk(
                matrixStack = matrices,
                texture = slotResource,
                x = x,
                y = y,
                height = SLOT_HEIGHT,
                width = rowWidth
            )

            drawScaledText(
                context = context,
                font = CobblemonResources.DEFAULT_LARGE,
                text = display.species.translatedName.bold(),
                x = x + 4,
                y = y + 2,
                shadow = true
            )

            TypeIcon(
                x = x + 12,
                y = y + 13.5,
                type = form.primaryType,
                secondaryType = form.secondaryType,
                doubleCenteredOffset = 5F,
                secondaryOffset = 9.5F,
                small = true,
                centeredX = true
            ).render(context)

            selectButton.setPosFloat(x + 23F, y + 13F)
            selectButton.render(context, mouseX, mouseY, partialTicks)

            // Render Pokémon
            matrices.pushPose()
            matrices.translate(x + (PORTRAIT_DIAMETER / 2) + 65.0, y - 5.0, 0.0)
            matrices.scale(2.5F, 2.5F, 1F)
            drawProfilePokemon(
                species = this.display.species.resourceIdentifier,
                matrixStack = matrices,
                rotation = Quaternionf().fromEulerXYZDegrees(Vector3f(13F, 35F, 0F)),
                state = state,
                scale = 6F,
                partialTicks = partialTicks
            )
            matrices.popPose()
        }

        override fun mouseClicked(d: Double, e: Double, i: Int): Boolean {
            if (selectButton.isHovered) {
                selectButton.onPress()
                return true
            }
            return false
        }
    }
}