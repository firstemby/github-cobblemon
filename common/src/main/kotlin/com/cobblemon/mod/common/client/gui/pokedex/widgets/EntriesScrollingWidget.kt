/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.gui.pokedex.widgets

import com.bedrockk.molang.runtime.MoLangRuntime
import com.cobblemon.mod.common.CobblemonSounds
import com.cobblemon.mod.common.api.gui.blitk
import com.cobblemon.mod.common.api.molang.MoLangFunctions.setup
import com.cobblemon.mod.common.api.pokedex.PokedexEntryProgress
import com.cobblemon.mod.common.api.pokedex.entry.PokedexEntry
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import com.cobblemon.mod.common.api.text.text
import com.cobblemon.mod.common.client.ClientMoLangFunctions.setupClient
import com.cobblemon.mod.common.client.CobblemonClient
import com.cobblemon.mod.common.client.gui.ScrollingWidget
import com.cobblemon.mod.common.client.gui.pokedex.PokedexGUIConstants
import com.cobblemon.mod.common.client.gui.pokedex.PokedexGUIConstants.SCALE
import com.cobblemon.mod.common.client.gui.pokedex.PokedexGUIConstants.SCROLL_BAR_WIDTH
import com.cobblemon.mod.common.client.gui.pokedex.PokedexGUIConstants.SCROLL_BASE_HEIGHT
import com.cobblemon.mod.common.client.gui.pokedex.PokedexGUIConstants.SCROLL_SLOT_SIZE
import com.cobblemon.mod.common.client.gui.pokedex.PokedexGUIConstants.SCROLL_SLOT_SPACING
import com.cobblemon.mod.common.client.gui.pokedex.widgets.EntriesScrollingWidget.PokemonScrollSlotRow
import com.cobblemon.mod.common.pokemon.Gender
import com.cobblemon.mod.common.pokemon.RenderablePokemon
import com.cobblemon.mod.common.util.cobblemonResource
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.resources.sounds.SimpleSoundInstance
import net.minecraft.network.chat.Component
import net.minecraft.util.Mth

class EntriesScrollingWidget(val pX: Int, val pY: Int, val setPokedexEntry: (PokedexEntry) -> (Unit)): ScrollingWidget<PokemonScrollSlotRow>(
    width = PokedexGUIConstants.HALF_OVERLAY_WIDTH,
    height = SCROLL_BASE_HEIGHT,
    left = pX,
    top = pY - SCROLL_BASE_HEIGHT,
    slotHeight = SCROLL_SLOT_SIZE + 2
) {
    companion object {
        val scrollbarSlide = cobblemonResource("textures/gui/pokedex/scrollbar_slide.png")
        val scrollbarTrack = cobblemonResource("textures/gui/pokedex/scrollbar_track.png")
    }

    fun createEntries(filteredPokedex: Collection<PokedexEntry>) {
        filteredPokedex.chunked(5).forEachIndexed { index, listChunk ->
            val discoveryList = listChunk.map { CobblemonClient.clientPokedexData.getHighestKnowledgeFor(it) }.toMutableList()
            val newEntry = PokemonScrollSlotRow(
                listChunk.toMutableList(),
                discoveryList,
                setPokedexEntry
            )
            addEntry(newEntry)
        }
    }

    override fun addEntry(entry: PokemonScrollSlotRow): Int {
        return super.addEntry(entry)
    }

    override fun getScrollbarPosition(): Int {
        return pX + width - SCROLL_BAR_WIDTH
    }

    override fun renderScrollbar(context: GuiGraphics, mouseX: Int, mouseY: Int, delta: Float) {
        val xLeft = this.scrollbarPosition
        val yMargin = 3
        val yStart = y + yMargin

        val barHeight = this.bottom - yMargin - yStart

        var yBottom = ((barHeight * barHeight).toFloat() / this.maxPosition.toFloat()).toInt()
        yBottom = Mth.clamp(yBottom, 32, barHeight - 8)
        var yTop = scrollAmount.toInt() * (barHeight - yBottom) / this.maxScroll + yStart
        if (yTop < yStart) yTop = yStart

        // Scroll Track
        blitk(
            texture = scrollbarTrack,
            matrixStack = context.pose(),
            x = xLeft,
            y = yStart,
            width = SCROLL_BAR_WIDTH,
            height = height - (yMargin * 2)
        )

        // Scroll Slide
        blitk(
            texture = scrollbarSlide,
            matrixStack = context.pose(),
            x = xLeft,
            y = yTop,
            width = SCROLL_BAR_WIDTH,
            height = yBottom
        )
    }

    override fun renderItem(
        context: GuiGraphics,
        mouseX: Int,
        mouseY: Int,
        delta: Float,
        index: Int,
        x: Int,
        y: Int,
        entryWidth: Int,
        entryHeight: Int
    ) {
        val entry =  this.getEntry(index)
        entry.x = x
        entry.y = y
        entry.render(
            context, index, y, x, entryWidth, entryHeight, mouseX, mouseY,
            focused == entry, delta
        )
    }

    override fun getEntry(index: Int): PokemonScrollSlotRow {
        return children()[index] as PokemonScrollSlotRow
    }

    class PokemonScrollSlotRow(
        val dexDataList:  MutableList<PokedexEntry>,
        val discoveryLevelList: MutableList<PokedexEntryProgress>,
        val setPokedexEntry : (PokedexEntry) -> (Unit)
    ): Slot<PokemonScrollSlotRow>() {
        val runtime = MoLangRuntime().setupClient().setup().also {
            it.environment.query.addFunction("get_pokedex") { CobblemonClient.clientPokedexData.struct }
        }

        var x: Int = 0
        var y: Int = 0

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
            dexDataList.forEachIndexed { index, pokedexEntry ->
                val species = PokemonSpecies.getByIdentifier(pokedexEntry.speciesId)
                val discoveryLevel = discoveryLevelList[index]
                val firstVisibleForm = CobblemonClient.clientPokedexData.getEncounteredForms(pokedexEntry).firstOrNull()

                if (species == null) {
                    return@forEachIndexed
                }

                val renderablePokemon =
                    if (firstVisibleForm != null) {
                        val firstVisibleGender = CobblemonClient.clientPokedexData.getSeenGenders(pokedexEntry, firstVisibleForm).firstOrNull()
                        val shinyStates = CobblemonClient.clientPokedexData.getSeenShinyStates(pokedexEntry, firstVisibleForm)
                        val firstVisibleShiny = shinyStates.count() == 1 && shinyStates.first() == "shiny"
                        val formAspects = species.forms.firstOrNull { it.name.equals(firstVisibleForm.displayForm, ignoreCase = true) }?.aspects ?: species.standardForm.aspects
                        val seenAspects = CobblemonClient.clientPokedexData.getSeenAspects(pokedexEntry)
                        val variationAspects = pokedexEntry.variations.mapNotNull { it.aspects.firstOrNull { it in seenAspects } }
                        val aspectsToDraw = (pokedexEntry.displayAspects + variationAspects + formAspects + (firstVisibleGender ?: Gender.GENDERLESS).name.lowercase()).toMutableSet()
                        if (firstVisibleShiny) {
                            aspectsToDraw.add("shiny")
                        }

                        RenderablePokemon(species, aspectsToDraw)
                    } else {
                        null
                    }

                PokemonSlotWidget.render(
                    context,
                    x = x + (SCROLL_SLOT_SPACING + SCROLL_SLOT_SIZE) * index,
                    y = y + SCROLL_SLOT_SPACING + 1,
                    getHoveredSlotIndex(mouseX, mouseY) == index,
                    species,
                    renderablePokemon,
                    discoveryLevel,
                )
            }
        }

        override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
            val hoverIndex = getHoveredSlotIndex(mouseX.toInt(), mouseY.toInt())
            if (hoverIndex > -1 && hoverIndex < dexDataList.size) {
                setPokedexEntry.invoke(dexDataList[hoverIndex])
                Minecraft.getInstance().soundManager.play(SimpleSoundInstance.forUI(CobblemonSounds.POKEDEX_CLICK, 1.0F))
            }

            return true
        }

        private fun getHoveredSlotIndex(mouseX: Int, mouseY: Int): Int {
            dexDataList.forEachIndexed { index, _ ->
                val startPosX = x + ((SCROLL_SLOT_SPACING + SCROLL_SLOT_SIZE) * index)
                val startPosY = y + SCROLL_SLOT_SPACING + 1

                if (mouseX in startPosX..(startPosX + SCROLL_SLOT_SIZE)
                    && mouseY in startPosY..(startPosY + SCROLL_SLOT_SIZE)) {
                    return index
                }
            }
            return -1
        }

        override fun getNarration(): Component {
            if (dexDataList.isNotEmpty()) {
                return "${dexDataList[0]}-${dexDataList[dexDataList.size - 1]}".text()
            }
            return "".text()
        }
    }
}