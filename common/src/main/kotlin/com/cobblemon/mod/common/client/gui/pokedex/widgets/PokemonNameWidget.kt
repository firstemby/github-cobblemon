/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.gui.pokedex.widgets

import com.cobblemon.mod.common.api.gui.blitk
import com.cobblemon.mod.common.api.pokedex.PokedexEntryProgress
import com.cobblemon.mod.common.api.pokedex.entry.PokedexEntry
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import com.cobblemon.mod.common.api.text.bold
import com.cobblemon.mod.common.api.text.text
import com.cobblemon.mod.common.client.CobblemonClient
import com.cobblemon.mod.common.client.CobblemonResources
import com.cobblemon.mod.common.client.gui.pokedex.PokedexGUI
import com.cobblemon.mod.common.client.gui.pokedex.PokedexGUIConstants.POKEMON_PORTRAIT_HEIGHT
import com.cobblemon.mod.common.client.gui.pokedex.PokedexGUIConstants.POKEMON_PORTRAIT_WIDTH
import com.cobblemon.mod.common.client.gui.pokedex.PokedexGUIConstants.SCALE
import com.cobblemon.mod.common.client.gui.summary.widgets.SoundlessWidget
import com.cobblemon.mod.common.client.render.drawScaledText
import com.cobblemon.mod.common.util.cobblemonResource
import com.cobblemon.mod.common.util.lang
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MutableComponent

class PokemonNameWidget(
    val parent: PokedexGUI,
    val pX: Int,
    val pY: Int,
) : SoundlessWidget(
    pX,
    pY,
    POKEMON_PORTRAIT_WIDTH,
    POKEMON_PORTRAIT_HEIGHT,
    lang("ui.pokedex.pokemon_name"),
) {

    companion object {
        private val caughtIcon = cobblemonResource("textures/gui/pokedex/caught_icon.png")
    }

    var hasKnowledge: Boolean = false
    var isOwned: Boolean = false
    var speciesName: MutableComponent = Component.translatable("")
    var speciesNumber: MutableComponent = "0000".text()

    override fun renderWidget(
        context: GuiGraphics,
        mouseX: Int,
        mouseY: Int,
        partialTick: Float
    ) {
        drawScaledText(
            context = context,
            font = CobblemonResources.DEFAULT_LARGE,
            text = speciesNumber.bold(),
            x = pX + 3,
            y = pY + 1,
            shadow = true
        )

        if (hasKnowledge) {
            drawScaledText(
                context = context,
                font = CobblemonResources.DEFAULT_LARGE,
                text = speciesName.bold(),
                x = pX + 26,
                y = pY + 1,
                colour = 0x606B6E
            )
        }

        if (isOwned) {
            blitk(
                matrixStack = context.pose(),
                texture = caughtIcon,
                x = (pX + 129) / SCALE,
                y = (pY + 2) / SCALE,
                width = 14,
                height = 14,
                scale = SCALE
            )
        }
    }

    fun update(pokedexEntry: PokedexEntry) {
        val species = PokemonSpecies.getByIdentifier(pokedexEntry.speciesId) ?: return

        val progress = CobblemonClient.clientPokedexData.getKnowledgeForSpecies(pokedexEntry.speciesId)
        this.hasKnowledge =  progress != PokedexEntryProgress.UNREGISTERED
        this.isOwned = progress == PokedexEntryProgress.OWNED
        this.speciesNumber = species.nationalPokedexNumber.toString().padStart(4, '0').text()
        this.speciesName = species.translatedName
    }
}