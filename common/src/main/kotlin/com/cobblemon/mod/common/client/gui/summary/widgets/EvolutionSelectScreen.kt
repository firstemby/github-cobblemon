/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.gui.summary.widgets

import com.cobblemon.mod.common.CobblemonSounds
import com.cobblemon.mod.common.api.gui.blitk
import com.cobblemon.mod.common.api.pokemon.evolution.EvolutionDisplay
import com.cobblemon.mod.common.api.text.bold
import com.cobblemon.mod.common.client.CobblemonResources
import com.cobblemon.mod.common.client.gui.TypeIcon
import com.cobblemon.mod.common.client.gui.drawLegacyLitProfilePokemon
import com.cobblemon.mod.common.client.gui.summary.SummaryButton
import com.cobblemon.mod.common.client.gui.summary.widgets.common.SummaryScrollList
import com.cobblemon.mod.common.client.render.drawScaledText
import com.cobblemon.mod.common.client.render.models.blockbench.FloatingState
import com.cobblemon.mod.common.pokemon.FormData
import com.cobblemon.mod.common.pokemon.Pokemon
import com.cobblemon.mod.common.util.cobblemonResource
import com.cobblemon.mod.common.util.lang
import com.cobblemon.mod.common.util.math.fromEulerXYZDegrees
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.client.resources.sounds.SimpleSoundInstance
import net.minecraft.sounds.SoundEvent
import org.joml.Quaternionf
import org.joml.Vector3f

class EvolutionSelectScreen(
    x: Int,
    y: Int,
    val pokemon: Pokemon
): SummaryScrollList<EvolutionSelectScreen.EvolveSlot>(
    x,
    y,
    lang("ui.evolution"),
    SLOT_HEIGHT + SLOT_SPACING
) {
    companion object {
        const val SLOT_HEIGHT = 25
        const val SLOT_SPACING = 5
        const val PORTRAIT_DIAMETER = 25

        private val slotResource = cobblemonResource("textures/gui/summary/summary_evolve_slot.png")
        private val buttonResource = cobblemonResource("textures/gui/summary/summary_evolve_select_button.png")
    }

    private var entriesCreated = false

    public override fun addEntry(entry: EvolveSlot): Int {
        return super.addEntry(entry)
    }

    override fun extractWidgetRenderState(context: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTicks: Float) {
        if (!entriesCreated) {
            entriesCreated = true
            pokemon.evolutionProxy.client().map { EvolveSlot(pokemon, it) }.forEach { entry -> this.addEntry(entry) }
        }
        super.extractWidgetRenderState(context, mouseX, mouseY, partialTicks)
    }

    class EvolveSlot(private val pokemon: Pokemon, private val evolution: EvolutionDisplay) : Entry<EvolveSlot>() {
        val client: Minecraft = Minecraft.getInstance()
        val state = FloatingState()
        val form: FormData = evolution.species.getForm(evolution.aspects)
        val selectButton: SummaryButton = SummaryButton(
            buttonX = 0F,
            buttonY = 0F,
            buttonWidth = 40,
            buttonHeight = 10,
            clickAction = {
                Minecraft.getInstance().player?.clientSideCloseContainer()
                playSound(CobblemonSounds.GUI_CLICK)
                pokemon.evolutionProxy.client().start(this.evolution)
            },
            text = lang("ui.evolve"),
            resource = buttonResource,
            boldText = true,
            largeText = false,
            textScale = 0.5F
        )

        fun playSound(soundEvent: SoundEvent) {
            Minecraft.getInstance().soundManager.play(SimpleSoundInstance.forUI(soundEvent, 1.0F))
        }

        override fun getNarration() = evolution.species.translatedName

        override fun extractContent(context: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, isHovered: Boolean, partialTicks: Float) {
            val x = this.x - 3
            val y = this.y
            val matrices = context.pose()

            state.currentAspects = evolution.aspects

            blitk(
                matrixStack = matrices,
                texture = slotResource,
                x = x,
                y = y,
                height = SLOT_HEIGHT,
                width = width
            )

            drawScaledText(
                context = context,
                font = CobblemonResources.DEFAULT_LARGE,
                text = evolution.species.translatedName.bold(),
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
            selectButton.extractRenderState(context, mouseX, mouseY, partialTicks)

            // Render Pokémon
            matrices.pushMatrix()
            matrices.translate(x + (PORTRAIT_DIAMETER / 2F) + 65F, y - 5F)
            matrices.scale(2.5F, 2.5F)
            drawLegacyLitProfilePokemon(
                species = this.evolution.species.resourceIdentifier,
                matrixStack = matrices,
                context = context,
                rotation = Quaternionf().fromEulerXYZDegrees(Vector3f(13F, 35F, 0F)),
                state = state,
                scale = 6F,
                partialTicks = partialTicks
            )
            matrices.popMatrix()
        }

        override fun mouseClicked(mouseButtonEvent: MouseButtonEvent, doubleClick: Boolean): Boolean {
            if (selectButton.mouseClicked(mouseButtonEvent, doubleClick)) {
                return true
            }
            return false
        }
    }
}
