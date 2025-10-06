package com.cobblemon.mod.common.client.gui.pokedex.widgets

import com.cobblemon.mod.common.api.gui.TreeDrawingNode
import com.cobblemon.mod.common.api.gui.TreeNode
import com.cobblemon.mod.common.api.gui.createDrawingTree
import com.cobblemon.mod.common.api.pokedex.Dexes
import com.cobblemon.mod.common.api.pokedex.PokedexEntryProgress
import com.cobblemon.mod.common.api.pokedex.entry.PokedexEntry
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import com.cobblemon.mod.common.api.text.bold
import com.cobblemon.mod.common.client.CobblemonResources
import com.cobblemon.mod.common.client.gui.pokedex.PokedexGUIConstants.HALF_OVERLAY_WIDTH
import com.cobblemon.mod.common.client.gui.pokedex.PokedexGUIConstants.POKEMON_DESCRIPTION_HEIGHT
import com.cobblemon.mod.common.client.gui.pokedex.PokedexGUIConstants.SCROLL_SLOT_SIZE
import com.cobblemon.mod.common.client.gui.summary.widgets.SoundlessWidget
import com.cobblemon.mod.common.client.render.drawScaledText
import com.cobblemon.mod.common.util.cobblemonResource
import com.cobblemon.mod.common.util.lang
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.network.chat.Component
import net.minecraft.util.FastColor

class EvolutionTreeWidget(
    val pX: Int,
    val pY: Int,
) : SoundlessWidget(
    pX, pY, HALF_OVERLAY_WIDTH, POKEMON_DESCRIPTION_HEIGHT, lang("ui.pokedex.pokemon_info")
) {
    companion object {
        const val HORIZONTAL_PADDING = 24
        const val VERTICAL_PADDING = 0
        const val TREE_FORK_LENGTH = 4

        val LINE_COLOR = FastColor.ARGB32.color(255, 0, 0, 0)
    }

    private var rootTreeDrawingNode: TreeDrawingNode<EvolutionNode>? = null

    override fun renderWidget(
        guiGraphics: GuiGraphics,
        mouseX: Int,
        mouseY: Int,
        partialTick: Float,
    ) {
        drawScaledText(
            context = guiGraphics,
            font = CobblemonResources.DEFAULT_LARGE,
            text = Component.translatable("cobblemon.ui.pokedex.info.evolution_tree").bold(),
            x = pX + 9,
            y = pY - 10,
            shadow = true
        )

        rootTreeDrawingNode?.let {
            guiGraphics.enableScissor(pX, pY, pX + width, pY + height)
            renderTreeDrawingNode(guiGraphics, it)
            guiGraphics.disableScissor()
        }
    }

    fun setup(pokedexEntry: PokedexEntry? = null) {
        val rootPokedexEntry = findRootPokedexEntry(pokedexEntry)
        val rootEvolutionNode = buildEvolutionTree(rootPokedexEntry)
        rootTreeDrawingNode = createDrawingTree(rootEvolutionNode);
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

    private fun renderTreeDrawingNode(
        guiGraphics: GuiGraphics,
        treeDrawingNode: TreeDrawingNode<EvolutionNode>,
    ) {
        val (x, y) = getDrawingCoordinates(treeDrawingNode)

        if (treeDrawingNode.children.size == 1) {
            val (childX, _) = getDrawingCoordinates(treeDrawingNode.children.first())

            guiGraphics.hLine(
                x + SCROLL_SLOT_SIZE,
                childX,
                y + SCROLL_SLOT_SIZE / 2,
                LINE_COLOR,
            )
        } else if (treeDrawingNode.children.size > 1) {
            val treeForkStart = x + SCROLL_SLOT_SIZE
            val treeForkEnd = x + SCROLL_SLOT_SIZE + TREE_FORK_LENGTH
            val middleStart = y + SCROLL_SLOT_SIZE / 2

            val (_, firstChildY) = getDrawingCoordinates(treeDrawingNode.children.first())
            val (_, lastChildY) = getDrawingCoordinates(treeDrawingNode.children.last())

            guiGraphics.hLine(
                treeForkStart,
                treeForkEnd,
                middleStart,
                LINE_COLOR,
            )

            guiGraphics.vLine(
                treeForkEnd,
                firstChildY + SCROLL_SLOT_SIZE / 2,
                lastChildY + SCROLL_SLOT_SIZE / 2,
                LINE_COLOR,
            )

            treeDrawingNode.children.forEach { child ->
                val (childX, childY) = getDrawingCoordinates(child)

                guiGraphics.hLine(
                    treeForkEnd,
                    childX,
                    childY + SCROLL_SLOT_SIZE / 2,
                    LINE_COLOR,
                )
            }
        }

        PokemonSlotWidget.render(
            context = guiGraphics,
            x = x,
            y = y,
            hovered = false,
            discoveryLevel = PokedexEntryProgress.CAUGHT,
            pokedexEntry = treeDrawingNode.value.pokedexEntry,
            showDexNumber = true,
            showCaughtIcon = true,
        )

        treeDrawingNode.children.forEach { child ->
            renderTreeDrawingNode(guiGraphics, child)
        }
    }

    private fun getDrawingCoordinates(treeDrawingNode: TreeDrawingNode<EvolutionNode>): Pair<Int, Int> {
        // Swaps the x and y to draw the tree horizontally
        return Pair(
            treeDrawingNode.y * (SCROLL_SLOT_SIZE + HORIZONTAL_PADDING) + pX,
            treeDrawingNode.x * (SCROLL_SLOT_SIZE / 2 + VERTICAL_PADDING) + pY,
        )
    }

    private class EvolutionNode(
        val pokedexEntry: PokedexEntry,
        override val children: List<EvolutionNode>
    ) : TreeNode<EvolutionNode>
}