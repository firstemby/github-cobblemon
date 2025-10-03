package com.cobblemon.mod.common.client.gui.pokedex.widgets

import com.cobblemon.mod.common.api.pokedex.Dexes
import com.cobblemon.mod.common.api.pokedex.PokedexEntryProgress
import com.cobblemon.mod.common.api.pokedex.entry.PokedexEntry
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import com.cobblemon.mod.common.api.text.bold
import com.cobblemon.mod.common.client.CobblemonResources
import com.cobblemon.mod.common.client.gui.pokedex.PokedexGUIConstants.HALF_OVERLAY_WIDTH
import com.cobblemon.mod.common.client.gui.pokedex.PokedexGUIConstants.POKEMON_DESCRIPTION_HEIGHT
import com.cobblemon.mod.common.client.gui.summary.widgets.SoundlessWidget
import com.cobblemon.mod.common.client.render.drawScaledText
import com.cobblemon.mod.common.util.cobblemonResource
import com.cobblemon.mod.common.util.lang
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.network.chat.Component

class EvolutionTreeWidget(
    val pX: Int,
    val pY: Int,
) : SoundlessWidget(
    pX,
    pY,
    HALF_OVERLAY_WIDTH,
    POKEMON_DESCRIPTION_HEIGHT,
    lang("ui.pokedex.pokemon_info")
) {
    private var rootEvolutionNode: EvolutionNode? = null

    override fun renderWidget(
        guiGraphics: GuiGraphics,
        mouseX: Int,
        mouseY: Int,
        partialTick: Float
    ) {
        drawScaledText(
            context = guiGraphics,
            font = CobblemonResources.DEFAULT_LARGE,
            text = Component.translatable("cobblemon.ui.pokedex.info.evolution_tree").bold(),
            x = pX + 9,
            y = pY - 10,
            shadow = true
        )

        PokemonSlotWidget.render(
            context = guiGraphics,
            x = pX,
            y = pY,
            hovered = false,
            discoveryLevel = PokedexEntryProgress.CAUGHT,
            pokedexEntry = rootEvolutionNode!!.value,
            showDexNumber = true,
            showCaughtIcon = false,
        )
    }

    fun setup(pokedexEntry: PokedexEntry? = null) {
        val rootPokedexEntry = findRootPokedexEntry(pokedexEntry)
        rootEvolutionNode = buildEvolutionTree(rootPokedexEntry)
    }

    private fun findRootPokedexEntry(pokedexEntry: PokedexEntry? = null): PokedexEntry {
        var rootSpecies = pokedexEntry?.speciesId?.let { PokemonSpecies.getByIdentifier(it) }!!
        while (rootSpecies.preEvolution != null) {
            rootSpecies = rootSpecies.preEvolution!!.species
        }

        return Dexes.findDexEntryBySpeciesId(rootSpecies.resourceIdentifier)
            ?: throw RuntimeException("Root dex entry not found")
    }

    private fun buildEvolutionTree(pokedexEntry: PokedexEntry): EvolutionNode {
        val species = PokemonSpecies.getByIdentifier(pokedexEntry.speciesId)!!
        val children = species.evolutions.map { child ->
            val childPokedexEntry = Dexes.findDexEntryBySpeciesId(cobblemonResource(child.result.species.toString()))
                ?: throw RuntimeException("Dex entry not found")

            return@map buildEvolutionTree(childPokedexEntry)
        }

        return EvolutionNode(pokedexEntry, children)
    }

    private class EvolutionNode(val value: PokedexEntry, val children: List<EvolutionNode>)
}