/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.gui.pokeedit.widgets

import com.bedrockk.molang.runtime.MoLangRuntime
import com.cobblemon.mod.common.api.gui.blitk
import com.cobblemon.mod.common.api.molang.MoLangFunctions.setup
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import com.cobblemon.mod.common.api.text.text
import com.cobblemon.mod.common.client.ClientMoLangFunctions.setupClient
import com.cobblemon.mod.common.client.gui.ScrollingWidget
import com.cobblemon.mod.common.client.gui.drawProfilePokemon
import com.cobblemon.mod.common.client.gui.pokedex.PokedexGUIConstants.SCROLL_BASE_HEIGHT
import com.cobblemon.mod.common.client.gui.pokedex.PokedexGUIConstants.SCROLL_SLOT_SIZE
import com.cobblemon.mod.common.client.gui.pokedex.PokedexGUIConstants.SCROLL_SLOT_SPACING
import com.cobblemon.mod.common.client.gui.pokedex.PokedexGUIConstants.SCALE
import com.cobblemon.mod.common.client.render.drawScaledText
import com.cobblemon.mod.common.client.render.models.blockbench.FloatingState
import com.cobblemon.mod.common.pokemon.Gender
import com.cobblemon.mod.common.pokemon.RenderablePokemon
import com.cobblemon.mod.common.pokemon.Species
import com.cobblemon.mod.common.util.cobblemonResource
import com.cobblemon.mod.common.util.math.fromEulerXYZDegrees
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.resources.sounds.SimpleSoundInstance
import net.minecraft.network.chat.Component
import net.minecraft.util.Mth
import org.joml.Quaternionf
import org.joml.Vector3f

class PokeEditSpeciesList(
    val pX: Int,
    val pY: Int,
    val onSelect: (Species) -> Unit
) : ScrollingWidget<PokeEditSpeciesList.SpeciesRow>(
    width = 139,
    height = SCROLL_BASE_HEIGHT,
    left = pX,
    top = pY - SCROLL_BASE_HEIGHT,
    slotHeight = SCROLL_SLOT_SIZE + 2
) {
    private var entries: List<Species> = emptyList()

    fun setEntries(species: List<Species>) {
        clearEntries()
        entries = species
        species.chunked(5).forEach { chunk ->
            addEntry(SpeciesRow(chunk, onSelect))
        }
        scrollAmount = 0.0
    }

    override fun addEntry(entry: SpeciesRow): Int = super.addEntry(entry)

    override fun getScrollbarPosition(): Int = pX + width - 3

    override fun renderScrollbar(context: GuiGraphics, mouseX: Int, mouseY: Int, delta: Float) {
        val xLeft = this.scrollbarPosition
        val xRight = xLeft + 3
        val barHeight = this.bottom - this.y

        var yBottom = ((barHeight * barHeight).toFloat() / this.maxPosition.toFloat()).toInt()
        yBottom = Mth.clamp(yBottom, 32, barHeight - 8)
        var yTop = scrollAmount.toInt() * (barHeight - yBottom) / this.maxScroll + this.y
        if (yTop < this.y) yTop = this.y

        context.fill(xLeft, this.y + 3, xRight, this.bottom - 3, 0xFF3A96B6.toInt())
        context.fill(xLeft, yTop + 3, xRight, yTop + yBottom - 3, 0xFFFCFCFC.toInt())
    }

    class SpeciesRow(
        private val speciesList: List<Species>,
        private val onSelect: (Species) -> Unit
    ) : Slot<SpeciesRow>() {
        companion object {
            private val slotResource = cobblemonResource("textures/gui/pokedex/pokedex_slot.png")
            private val slotHighlight = cobblemonResource("textures/gui/pokedex/slot_select.png")
        }

        private val runtime = MoLangRuntime().setupClient().setup().also {
            it.environment.query.addFunction("get_pokedex") { null }
        }

        private var x: Int = 0
        private var y: Int = 0

        override fun render(
            context: GuiGraphics,
            index: Int,
            y: Int,
            x: Int,
            entryWidth: Int,
            entryHeight: Int,
            mouseX: Int,
            mouseY: Int,
            hovered: Boolean,
            tickDelta: Float
        ) {
            this.x = x
            this.y = y

            speciesList.forEachIndexed { indexInRow, species ->
                val startPosX = x + ((SCROLL_SLOT_SPACING + SCROLL_SLOT_SIZE) * indexInRow)
                val startPosY = y + SCROLL_SLOT_SPACING + 1

                blitk(
                    matrixStack = context.pose(),
                    texture = slotResource,
                    x = startPosX,
                    y = startPosY,
                    width = SCROLL_SLOT_SIZE,
                    height = SCROLL_SLOT_SIZE
                )

                if (getHoveredSlotIndex(mouseX, mouseY) == indexInRow) {
                    blitk(
                        matrixStack = context.pose(),
                        texture = slotHighlight,
                        x = startPosX,
                        y = startPosY,
                        width = SCROLL_SLOT_SIZE,
                        height = SCROLL_SLOT_SIZE,
                        vOffset = SCROLL_SLOT_SIZE,
                        textureHeight = SCROLL_SLOT_SIZE * 2
                    )
                }

                val form = species.standardForm
                val aspects = mutableSetOf<String>()
                aspects.addAll(form.aspects)
                if (form.maleRatio in 0F..1F) {
                    aspects.add(Gender.MALE.name.lowercase())
                } else {
                    aspects.add(Gender.GENDERLESS.name.lowercase())
                }

                context.enableScissor(
                    startPosX + 1,
                    startPosY + 1,
                    startPosX + SCROLL_SLOT_SIZE - 1,
                    startPosY + SCROLL_SLOT_SIZE - 2
                )

                val state = FloatingState().also { it.currentAspects = aspects }
                val matrices = context.pose()
                matrices.pushPose()
                matrices.translate(startPosX + (SCROLL_SLOT_SIZE / 2.0), startPosY + 1.0, 0.0)
                matrices.scale(2.5F, 2.5F, 1F)
                drawProfilePokemon(
                    renderablePokemon = RenderablePokemon(species, aspects),
                    matrixStack = matrices,
                    rotation = Quaternionf().fromEulerXYZDegrees(Vector3f(13F, 35F, 0F)),
                    state = state,
                    partialTicks = 0F,
                    scale = 4.5F
                )
                matrices.popPose()
                context.disableScissor()

                matrices.pushPose()
                matrices.translate(0.0, 0.0, 100.0)
                val pokemonNumber = species.nationalPokedexNumber.toString().padStart(4, '0').text()
                drawScaledText(
                    context = context,
                    text = pokemonNumber,
                    x = startPosX + 1.5,
                    y = startPosY + 2.5,
                    shadow = true,
                    scale = SCALE
                )
                matrices.popPose()
            }
        }

        override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
            val hoverIndex = getHoveredSlotIndex(mouseX.toInt(), mouseY.toInt())
            if (hoverIndex in speciesList.indices) {
                onSelect(speciesList[hoverIndex])
                Minecraft.getInstance().soundManager.play(SimpleSoundInstance.forUI(com.cobblemon.mod.common.CobblemonSounds.POKEDEX_CLICK, 1.0F))
            }
            return true
        }

        private fun getHoveredSlotIndex(mouseX: Int, mouseY: Int): Int {
            speciesList.forEachIndexed { index, _ ->
                val startPosX = x + ((SCROLL_SLOT_SPACING + SCROLL_SLOT_SIZE) * index)
                val startPosY = y + SCROLL_SLOT_SPACING + 1
                if (mouseX in startPosX..(startPosX + SCROLL_SLOT_SIZE)
                    && mouseY in startPosY..(startPosY + SCROLL_SLOT_SIZE)
                ) {
                    return index
                }
            }
            return -1
        }

        override fun getNarration(): Component {
            return if (speciesList.isNotEmpty()) {
                "${speciesList.first().name}-${speciesList.last().name}".text()
            } else {
                "".text()
            }
        }
    }
}
