/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.gui.pokedex.widgets.evolutiontree

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.api.gui.*
import com.cobblemon.mod.common.api.pokedex.Dexes
import com.cobblemon.mod.common.api.pokedex.PokedexEntryProgress
import com.cobblemon.mod.common.api.pokedex.entry.PokedexEntry
import com.cobblemon.mod.common.api.pokedex.entry.PokedexForm
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import com.cobblemon.mod.common.api.pokemon.evolution.Evolution
import com.cobblemon.mod.common.api.text.bold
import com.cobblemon.mod.common.client.CobblemonResources
import com.cobblemon.mod.common.client.gui.pokedex.PokedexGUI
import com.cobblemon.mod.common.client.gui.pokedex.PokedexGUIConstants
import com.cobblemon.mod.common.client.gui.pokedex.PokedexGUIConstants.HALF_OVERLAY_WIDTH
import com.cobblemon.mod.common.client.gui.pokedex.PokedexGUIConstants.POKEMON_DESCRIPTION_HEIGHT
import com.cobblemon.mod.common.client.gui.pokedex.PokedexGUIConstants.SCROLL_SLOT_SIZE
import com.cobblemon.mod.common.client.gui.pokedex.ScaledButton
import com.cobblemon.mod.common.client.gui.pokedex.renderTooltip
import com.cobblemon.mod.common.client.gui.pokedex.widgets.PokemonSlotWidget
import com.cobblemon.mod.common.client.gui.pokedex.widgets.evolutiontree.EvolutionTreeEvolutionVisitor.RenderEvolutionData
import com.cobblemon.mod.common.client.gui.summary.widgets.SoundlessWidget
import com.cobblemon.mod.common.client.render.drawScaledText
import com.cobblemon.mod.common.client.render.renderScaledGuiItemIcon
import com.cobblemon.mod.common.pokemon.Gender
import com.cobblemon.mod.common.pokemon.aspects.SHINY_ASPECT
import com.cobblemon.mod.common.util.asResource
import com.cobblemon.mod.common.util.cobblemonResource
import com.cobblemon.mod.common.util.lang
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MutableComponent

class EvolutionTreeWidget(
    val parent: PokedexGUI,
    val pX: Int,
    val pY: Int,
) : SoundlessWidget(
    pX, pY,
    HALF_OVERLAY_WIDTH,
    POKEMON_DESCRIPTION_HEIGHT,
    lang("ui.pokedex.pokemon_info")
) {
    companion object {
        private val slotResource = cobblemonResource("textures/gui/pokedex/pokedex_slot.png")

        const val X_MARGIN = 180
        const val Y_MARGIN_MINIMIZED = 135
        const val Y_MARGIN_EXPANDED = 52
        const val EXPANDED_HEIGHT = 125

        const val HORIZONTAL_PADDING = 38
        const val VERTICAL_PADDING = 2
        const val TREE_FORK_LENGTH = 4

        const val ICON_X_PADDING = -4.0
        const val ICON_Y_PADDING = -4.0
        const val MILLIS_BETWEEN_ICONS_CYCLE = 1000

        const val ICON_SLOT_BACKGROUND_SIZE = 8
        const val ICON_SLOT_PADDING = 4
        const val ICON_SLOT_HALF_PADDING = ICON_SLOT_PADDING / 2

        const val LINE_COLOR = 0xff1d6b85.toInt()

        const val EXPAND_BUTTON_X_MARGIN = 128f
        const val EXPAND_BUTTON_Y_MARGIN = -11f
        const val EXPAND_BUTTON_SIZE = 20

        private var previousRootPokedexEntry: PokedexEntry? = null
        private var previousRootPokedexForm: PokedexForm? = null

        private val evolutionVisitor = EvolutionTreeEvolutionVisitor()
    }

    private var rootTreeDrawingNode: TreeDrawingNode<EvolutionNode>? = null
    private var treeDrawingBounds: TreeDrawingBounds? = null
    private var zoomLevel: Double = 0.0
    private var panningX: Double = 0.0
    private var panningY: Double = 0.0

    private var hoveredTooltips: List<MutableComponent>? = null

    private val expandButton = ScaledButton(
        100f,
        100f,
        EXPAND_BUTTON_SIZE,
        EXPAND_BUTTON_SIZE,
        resource = slotResource,
        clickAction = {
            parent.evolutionTreeExpanded = !parent.evolutionTreeExpanded
            updatePositioning()
        }
    ).apply { addWidget(this) }

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
            x = x + 9,
            y = y - 10,
            shadow = true
        )

        expandButton.render(guiGraphics, mouseX, mouseY, partialTick)

        rootTreeDrawingNode?.let {
            enableScissors(guiGraphics)
            renderTreeDrawingNode(guiGraphics, it, mouseX, mouseY, partialTick)
            guiGraphics.disableScissor()

            hoveredTooltips?.let { tooltips ->
                renderTooltip(
                    guiGraphics,
                    tooltips,
                    mouseX,
                    mouseY,
                    partialTick,
                    4,
                )
            }

            hoveredTooltips = null
        }
    }

    fun setup(pokedexEntry: PokedexEntry?, pokedexForm: PokedexForm?) {
        updatePositioning()

        val rootPokedexEntry = findRootPokedexEntry(pokedexEntry)
        val rootEvolutionNode = buildEvolutionTree(rootPokedexEntry, pokedexForm, null)

        val isSameEntry = previousRootPokedexEntry != null && pokedexEntry?.id == previousRootPokedexEntry?.id
        val isSameForm =
            previousRootPokedexForm != null && pokedexForm?.displayForm == previousRootPokedexForm?.displayForm

        // TODO: figure out how to prevent pokedex from resetting all the widgets state
        // if (isSameEntry && isSameForm) {
        //    return
        // }

        previousRootPokedexEntry = pokedexEntry
        previousRootPokedexForm = pokedexForm

        createDrawingTree(rootEvolutionNode).also {
            rootTreeDrawingNode = it
            treeDrawingBounds = calculateBounds<EvolutionNode>(it)
        }

        zoomLevel = 0.0
        panningX = 0.0
        panningY = 0.0
    }

    fun updatePositioning() {
        x = pX + X_MARGIN
        y = pY + if (parent.evolutionTreeExpanded) Y_MARGIN_EXPANDED else Y_MARGIN_MINIMIZED
        height = if (parent.evolutionTreeExpanded) EXPANDED_HEIGHT else POKEMON_DESCRIPTION_HEIGHT

        panningX = 0.0
        panningY = 0.0

        // TODO: refactor ScaledButton?
        expandButton.buttonX = x + EXPAND_BUTTON_X_MARGIN
        expandButton.x = x + EXPAND_BUTTON_X_MARGIN.toInt()
        expandButton.buttonY = y + EXPAND_BUTTON_Y_MARGIN
        expandButton.y = y + EXPAND_BUTTON_Y_MARGIN.toInt()
    }

    private fun findRootPokedexEntry(pokedexEntry: PokedexEntry? = null): PokedexEntry {
        var rootSpecies = pokedexEntry?.speciesId?.let { PokemonSpecies.getByIdentifier(it) }!!
        while (rootSpecies.preEvolution != null) {
            rootSpecies = rootSpecies.preEvolution!!.species
        }

        return Dexes.findDexEntryBySpeciesId(rootSpecies.resourceIdentifier)
            ?: throw RuntimeException("Root dex entry not found")
    }

    private fun buildEvolutionTree(
        pokedexEntry: PokedexEntry,
        pokedexForm: PokedexForm? = null,
        evolution: Evolution? = null
    ): EvolutionNode {
        val species = PokemonSpecies.getByIdentifier(pokedexEntry.speciesId)!!
        val form = pokedexForm?.let { form -> species.forms.firstOrNull { it.name == form.displayForm } }
        val evolutions = form?.evolutions ?: species.evolutions
        val drawData = evolution?.let { evolutionVisitor.visit(evolution) }

        val aspects = evolution?.result?.aspects?.toMutableSet() ?: mutableSetOf()
        form?.aspects?.let { aspects.addAll(it) }

        // TODO: figure out if this works for fakemon
        val children = evolutions.mapNotNull { evolution ->
            val speciesId = cobblemonResource(evolution.result.species!!)
            val childPokedexEntry =
                Dexes.findDexEntryBySpeciesId(speciesId)

            if (childPokedexEntry == null) {
                Cobblemon.LOGGER.warn("Dex entry not found for species: $speciesId")
                return@mapNotNull null
            }

            val childPokedexForm =
                childPokedexEntry.forms.find { it.displayForm == evolution.result.create().form.name }

            return@mapNotNull buildEvolutionTree(childPokedexEntry, childPokedexForm, evolution)
        }

        return EvolutionNode(pokedexEntry, pokedexForm, aspects, evolution, children, drawData)
    }

    private fun renderTreeDrawingNode(
        guiGraphics: GuiGraphics,
        treeDrawingNode: TreeDrawingNode<EvolutionNode>,
        mouseX: Int,
        mouseY: Int,
        delta: Float,
    ) {
        val (treeX, treeY) = getDrawingCoordinates(treeDrawingNode)

        if (treeDrawingNode.children.size == 1) {
            val child = treeDrawingNode.children.first()
            val (childX, _) = getDrawingCoordinates(child)

            val lineStartX = treeX + SCROLL_SLOT_SIZE
            val lineEndX = childX
            val lineY = treeY + SCROLL_SLOT_SIZE / 2

            child.value.renderData?.let { renderData ->
                drawEvolutionInfo(
                    guiGraphics,
                    renderData,
                    lineStartX,
                    lineEndX,
                    lineY,
                    mouseX,
                    mouseY,
                    delta,
                )
            }
        } else if (treeDrawingNode.children.size > 1) {
            val treeForkStart = treeX + SCROLL_SLOT_SIZE
            val treeForkEnd = treeX + SCROLL_SLOT_SIZE + TREE_FORK_LENGTH
            val middleStart = treeY + SCROLL_SLOT_SIZE / 2

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

                val lineStartX = treeForkEnd
                val lineEndX = childX
                val lineY = childY + SCROLL_SLOT_SIZE / 2

                child.value.renderData?.let { renderData ->
                    drawEvolutionInfo(
                        guiGraphics,
                        renderData,
                        lineStartX,
                        lineEndX,
                        lineY,
                        mouseX,
                        mouseY,
                        delta,
                    )
                }
            }
        }

        treeDrawingNode.children.forEach { child ->
            renderTreeDrawingNode(guiGraphics, child, mouseX, mouseY, delta)
        }

        val isVisible =
            treeX >= x - SCROLL_SLOT_SIZE &&
                    treeX <= x + width &&
                    treeY >= y - SCROLL_SLOT_SIZE &&
                    treeY <= y + height

        if (!isVisible) return

        val isSlotHovered =
            isElementHovered(mouseX, mouseY, treeX, treeX + SCROLL_SLOT_SIZE, treeY, treeY + SCROLL_SLOT_SIZE)

        val aspects = treeDrawingNode.value.aspects.toMutableSet()
        if (parent.shiny) aspects.add(SHINY_ASPECT.aspect)

        if (parent.gender == Gender.FEMALE) {
            aspects.add("female")
        } else if (parent.gender == Gender.MALE) {
            aspects.add("male")
        }

        PokemonSlotWidget.Companion.render(
            context = guiGraphics,
            x = treeX,
            y = treeY,
            hovered = isSlotHovered,
            discoveryLevel = PokedexEntryProgress.CAUGHT,
            pokedexEntry = treeDrawingNode.value.pokedexEntry,
            pokedexForm = treeDrawingNode.value.pokedexForm,
            showDexNumber = true,
            showCaughtIcon = true,
            aspects = aspects,
        )
    }

    private fun drawEvolutionInfo(
        guiGraphics: GuiGraphics,
        renderData: RenderEvolutionData,
        lineStartX: Int,
        lineEndX: Int,
        lineY: Int,
        mouseX: Int,
        mouseY: Int,
        delta: Float,
    ) {
        val lineXMiddle = (lineStartX + lineEndX) / 2
        val iconX = (lineXMiddle + ICON_X_PADDING - ICON_SLOT_HALF_PADDING).toInt()
        val iconY = (lineY + ICON_Y_PADDING - ICON_SLOT_HALF_PADDING).toInt()
        val iconSize = ICON_SLOT_BACKGROUND_SIZE + ICON_SLOT_PADDING

        val isVisible =
            iconX >= x - iconSize &&
            iconX <= x + width &&
            iconY >= y - iconSize &&
            iconY <= y + height

        if (!isVisible) {
            return
        }

        if (renderData.icons.isEmpty()) {
            guiGraphics.hLine(
                lineStartX,
                lineEndX,
                lineY,
                LINE_COLOR,
            )
        } else {
            guiGraphics.hLine(
                lineStartX,
                iconX - 1,
                lineY,
                LINE_COLOR,
            )

            guiGraphics.hLine(
                iconX + iconSize,
                lineEndX,
                lineY,
                LINE_COLOR,
            )

            blitk(
                matrixStack = guiGraphics.pose(),
                texture = slotResource,
                x = iconX,
                y = iconY,
                width = iconSize,
                height = iconSize,
            )

            val index = (System.currentTimeMillis() / MILLIS_BETWEEN_ICONS_CYCLE) % renderData.icons.size
            val item = renderData.icons[index.toInt()]

            renderScaledGuiItemIcon(
                itemStack = item.defaultInstance,
                x = lineXMiddle + ICON_X_PADDING,
                y = lineY + ICON_Y_PADDING,
                matrixStack = guiGraphics.pose(),
                scale = PokedexGUIConstants.SCALE.toDouble(),
            )
        }

        val isLineHovered = isElementHovered(mouseX, mouseY, lineStartX, lineEndX, lineY - 1, lineY + 1)
        val isIconHovered = isElementHovered(
            mouseX,
            mouseY,
            iconX.toInt(),
            iconX.toInt() + iconSize,
            iconY.toInt(),
            iconY.toInt() + iconSize,
        )

        if (renderData.tooltips.isNotEmpty() &&
            (isLineHovered || isIconHovered) &&
            this.isHovered(mouseX.toDouble(), mouseY.toDouble())
        ) {
            hoveredTooltips = renderData.tooltips
        }
    }

    private fun getDrawingCoordinates(node: TreeDrawingNode<EvolutionNode>): Pair<Int, Int> {
        val halfSlot = SCROLL_SLOT_SIZE / 2f

        val xStep = halfSlot + HORIZONTAL_PADDING
        val yStep = halfSlot + VERTICAL_PADDING

        // Swaps the x and y to draw the tree horizontally
        val xDislocation = (node.y - treeDrawingBounds!!.yCenter) * xStep
        val yDislocation = (node.x - treeDrawingBounds!!.xCenter) * yStep

        val xCenter = width / 2f - halfSlot
        val yCenter = height / 2f - halfSlot

        val screenX = (xCenter.toInt() + xDislocation.toInt() + panningX.toInt() + x).toInt()
        val screenY = (yCenter.toInt() + yDislocation.toInt() + panningY.toInt() + y).toInt()

        return screenX to screenY
    }

    private fun isElementHovered(
        mouseX: Int,
        mouseY: Int,
        startX: Int,
        endX: Int,
        startY: Int,
        endY: Int,
    ): Boolean {
        return mouseX >= startX && mouseX <= endX && mouseY >= startY && mouseY <= endY
    }

    override fun mouseDragged(
        mouseX: Double,
        mouseY: Double,
        button: Int,
        deltaX: Double,
        deltaY: Double
    ): Boolean {
        panningX += deltaX
        panningY += deltaY

        val bounds = treeDrawingBounds ?: return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY)
        val treeHeight = bounds.height.coerceAtLeast(1)
        val treeWidth = bounds.width.coerceAtLeast(1)

        val xStep = HORIZONTAL_PADDING + SCROLL_SLOT_SIZE / 2.0
        val yStep = VERTICAL_PADDING + SCROLL_SLOT_SIZE / 2.0

        val minPanningX = (treeHeight - 1) * -xStep.toDouble() / 2.0 - (width + SCROLL_SLOT_SIZE) / 2.0
        val maxPanningX = (treeHeight - 1) * xStep.toDouble() / 2.0 + (width + SCROLL_SLOT_SIZE) / 2.0

        val divideBy = if (treeWidth % 2 == 0) 4.0 else 2.0
        val minPanningY = (treeWidth - 1) * -yStep.toDouble() / 2.0 - height / 2.0 + SCROLL_SLOT_SIZE / divideBy
        val maxPanningY = (treeWidth - 1) * yStep.toDouble() / 2.0 + height / 2.0 - SCROLL_SLOT_SIZE / divideBy

        panningX = panningX.coerceIn(minPanningX, maxPanningX)
        panningY = panningY.coerceIn(minPanningY, maxPanningY)

        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY)
    }

    fun isHovered(mouseX: Double, mouseY: Double): Boolean {
        return mouseX.toInt() in x..(x + width) && mouseY.toInt() in y..(y + height)
    }

    fun enableScissors(guiGraphics: GuiGraphics) {
        val yStartScissor = if (parent.evolutionTreeExpanded) 1 else 0
        guiGraphics.enableScissor(x + 1, y + yStartScissor, x + width - 1, y + height)
    }

    private class EvolutionNode(
        val pokedexEntry: PokedexEntry,
        val pokedexForm: PokedexForm?,
        val aspects: Set<String>,
        val evolution: Evolution?,
        override val children: List<EvolutionNode>,
        val renderData: RenderEvolutionData?,
    ) : TreeNode<EvolutionNode>
}