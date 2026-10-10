/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.gui.pokedex.widgets

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.CobblemonItems
import com.cobblemon.mod.common.api.gui.*
import com.cobblemon.mod.common.api.pokedex.Dexes
import com.cobblemon.mod.common.api.pokedex.PokedexEntryProgress
import com.cobblemon.mod.common.api.pokedex.entry.CobblemonBiomeDexEvolutionRequirementDisplay
import com.cobblemon.mod.common.api.pokedex.entry.DexEntries
import com.cobblemon.mod.common.api.pokedex.entry.DexEvolutionDisplay
import com.cobblemon.mod.common.api.pokedex.entry.PokedexEntry
import com.cobblemon.mod.common.api.pokedex.entry.PokedexForm
import com.cobblemon.mod.common.api.pokemon.PokemonProperties
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import com.cobblemon.mod.common.api.pokemon.evolution.Evolution
import com.cobblemon.mod.common.api.text.add
import com.cobblemon.mod.common.api.text.bold
import com.cobblemon.mod.common.api.text.green
import com.cobblemon.mod.common.api.text.red
import com.cobblemon.mod.common.client.CobblemonClient
import com.cobblemon.mod.common.client.CobblemonResources
import com.cobblemon.mod.common.client.gui.pokedex.PokedexGUI
import com.cobblemon.mod.common.client.gui.pokedex.PokedexGUIConstants
import com.cobblemon.mod.common.client.gui.pokedex.PokedexGUIConstants.HALF_OVERLAY_WIDTH
import com.cobblemon.mod.common.client.gui.pokedex.PokedexGUIConstants.POKEMON_DESCRIPTION_HEIGHT
import com.cobblemon.mod.common.client.gui.pokedex.PokedexGUIConstants.SCROLL_SLOT_SIZE
import com.cobblemon.mod.common.client.gui.pokedex.ScaledButton
import com.cobblemon.mod.common.client.gui.pokedex.renderTooltip
import com.cobblemon.mod.common.client.gui.summary.widgets.SoundlessWidget
import com.cobblemon.mod.common.client.render.drawScaledText
import com.cobblemon.mod.common.client.render.renderScaledGuiItemIcon
import com.cobblemon.mod.common.pokemon.FormData
import com.cobblemon.mod.common.pokemon.Gender
import com.cobblemon.mod.common.pokemon.RenderablePokemon
import com.cobblemon.mod.common.util.asIdentifierDefaultingNamespace
import com.cobblemon.mod.common.util.cobblemonResource
import com.cobblemon.mod.common.util.lang
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.core.registries.Registries
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MutableComponent
import net.minecraft.tags.TagKey

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
        private val expandResource = cobblemonResource("textures/gui/pokedex/expand.png")
        private val minimizeResource = cobblemonResource("textures/gui/pokedex/minimize.png")

        const val X_MARGIN = 180
        const val Y_MARGIN_MINIMIZED = 135
        const val Y_MARGIN_EXPANDED = 53
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

        const val EXPAND_BUTTON_X_MARGIN = 127f
        const val EXPAND_BUTTON_Y_MARGIN = -10.5f
        const val EXPAND_BUTTON_SIZE = 20

        private var previousRootPokedexEntry: PokedexEntry? = null
        private var previousRootPokedexForm: PokedexForm? = null
    }

    private var rootTreeDrawingNode: TreeDrawingNode<EvolutionNode>? = null
    private var treeDrawingBounds: TreeDrawingBounds? = null
    private var zoomLevel: Double = 0.0
    private var panningX: Double = 0.0
    private var panningY: Double = 0.0

    private var hoveredTooltips: List<MutableComponent>? = null

    private var viewedForm: FormData? = null

    private val expandButton: ScaledButton = ScaledButton(
        0f,
        0f,
        EXPAND_BUTTON_SIZE,
        EXPAND_BUTTON_SIZE,
        resource = expandResource,
        clickAction = {
            parent.evolutionTreeExpanded = !parent.evolutionTreeExpanded
            expandButton.resource = if (parent.evolutionTreeExpanded) minimizeResource else expandResource
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
        if (pokedexEntry == null || pokedexForm == null) return

        updatePositioning()
        var rootSpecies = pokedexEntry
        var rootForm = pokedexForm
        var preEvolution = findPreEvolution(rootSpecies, rootForm)
        while (preEvolution != null) {
            rootSpecies = Dexes.findDexEntryBySpeciesId(preEvolution.fromSpecies)!!
            rootForm = rootSpecies.forms.first { it.displayForm.equals(preEvolution.fromForm, ignoreCase = true) }
            preEvolution = findPreEvolution(rootSpecies, rootForm)
        }

        val entry = Dexes.findDexEntryBySpeciesId(rootSpecies.speciesId)
            ?: throw RuntimeException("Root dex entry not found")

        val viewedFormEquivalents = Dexes.getFormEquivalents(pokedexForm.displayForm)
        viewedForm = PokemonSpecies.getByIdentifier(pokedexEntry.speciesId)
            ?.forms?.firstOrNull { viewedFormEquivalents.contains(it.name.lowercase()) }

        val formEquivalents = Dexes.getFormEquivalents(rootForm.displayForm)
        val form =
            entry.forms.firstOrNull { formEquivalents.contains(it.displayForm.lowercase()) } ?:
            entry.forms.first { it.displayForm.equals("Normal", ignoreCase = true) }

        val rootEvolutionNode = buildEvolutionTree(entry, form)

//        val isSameEntry = previousRootPokedexEntry != null && pokedexEntry?.id == previousRootPokedexEntry?.id
//        val isSameForm =
//            previousRootPokedexForm != null && pokedexForm?.displayForm == previousRootPokedexForm?.displayForm

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

    /** The evolution leading to [form]; the standard form falls back to any form of the species (no evolution leads to regular Vivillon). */
    private fun findPreEvolution(entry: PokedexEntry, form: PokedexForm): DexEvolutionDisplay? {
        return DexEntries.evolutionDisplays.firstOrNull { it.toSpecies == entry.speciesId && it.toForm == form.displayForm }
            ?: DexEntries.evolutionDisplays.takeIf { form.displayForm.equals("Normal", ignoreCase = true) }
                ?.firstOrNull { it.toSpecies == entry.speciesId }
    }

    fun updatePositioning() {
        x = pX + X_MARGIN
        y = pY + if (parent.evolutionTreeExpanded) Y_MARGIN_EXPANDED else Y_MARGIN_MINIMIZED
        height = if (parent.evolutionTreeExpanded) EXPANDED_HEIGHT else POKEMON_DESCRIPTION_HEIGHT

        panningX = 0.0
        panningY = 0.0

        expandButton.buttonX = x + EXPAND_BUTTON_X_MARGIN
        expandButton.buttonY = y + EXPAND_BUTTON_Y_MARGIN
    }

    private fun buildEvolutionTree(
        pokedexEntry: PokedexEntry,
        pokedexForm: PokedexForm,
        aspects: Set<String> = emptySet(),
    ): EvolutionNode {
        val discoveryLevel = CobblemonClient.clientPokedexData.getHighestKnowledgeFor(pokedexEntry, pokedexForm, aspects.toMutableSet())

        val formEquivalents = Dexes.getFormEquivalents(pokedexForm.displayForm)
        val evolutions = DexEntries.evolutionDisplays.filter { it.fromSpecies == pokedexEntry.speciesId && formEquivalents.contains(it.fromForm.lowercase()) }

        val children = evolutions.mapNotNull {
            val childPokedexEntry = Dexes.findDexEntryBySpeciesId(it.toSpecies)
            if (childPokedexEntry == null) {
                Cobblemon.LOGGER.warn("Dex entry not found for species: ${it.toSpecies}")
                return@mapNotNull null
            }
            val formEquivalents = Dexes.getFormEquivalents(it.toForm.lowercase())
            val childPokedexForm = childPokedexEntry.forms.first { formEquivalents.contains(it.displayForm.lowercase()) }
            buildEvolutionTree(childPokedexEntry, childPokedexForm, it.toAspects)
        }

        return EvolutionNode(
            pokedexEntry = pokedexEntry,
            pokedexForm = pokedexForm,
            aspects = aspects,
            pokedexEntryProgress = discoveryLevel,
            children = mergeEquivalentChildren(children),
            renderDataAlternatives = evolutions,
        )
    }

    private fun mergeEquivalentChildren(children: List<EvolutionNode>): List<EvolutionNode> {
        return children
            .groupBy { Triple(it.pokedexEntry.speciesId, it.pokedexForm.displayForm.lowercase(), it.aspects) }
            .values
            .map { group ->
                if (group.size == 1) return@map group.first()
                val first = group.first()
                EvolutionNode(
                    first.pokedexEntry,
                    first.pokedexForm,
                    first.aspects,
                    first.pokedexEntryProgress,
                    first.children,
                    group.flatMap { it.renderDataAlternatives }
                )
            }
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
            val lineY = treeY + SCROLL_SLOT_SIZE / 2

            val formEquivalents = Dexes.getFormEquivalents(child.value.pokedexForm.displayForm)
            val alternatives = treeDrawingNode.value.renderDataAlternatives.filter {
                it.toSpecies == child.value.pokedexEntry.speciesId && formEquivalents.contains(it.toForm.lowercase()) && it.toAspects == child.value.aspects
            }

            alternatives.takeIf { it.isNotEmpty() }?.let {
                drawEvolutionInfo(
                    guiGraphics,
                    it,
                    lineStartX,
                    childX,
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

                val lineY = childY + SCROLL_SLOT_SIZE / 2

                val formEquivalents = Dexes.getFormEquivalents(child.value.pokedexForm.displayForm)
                val alternatives = treeDrawingNode.value.renderDataAlternatives.filter {
                    it.toSpecies == child.value.pokedexEntry.speciesId && formEquivalents.contains(it.toForm.lowercase()) && it.toAspects == child.value.aspects
                }

                alternatives.takeIf { it.isNotEmpty() }?.let {
                    drawEvolutionInfo(
                        guiGraphics,
                        it,
                        treeForkEnd,
                        childX,
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

        val evolutionNode = treeDrawingNode.value
        val isSlotHovered =
            isElementHovered(mouseX, mouseY, treeX, treeX + SCROLL_SLOT_SIZE, treeY, treeY + SCROLL_SLOT_SIZE)

        val discoveryLevel = evolutionNode.pokedexEntryProgress
        if (isSlotHovered) {
            val text =
                if (discoveryLevel == PokedexEntryProgress.UNREGISTERED) {
                    lang("ui.pokedex.unknown")
                } else {
                    val species = PokemonSpecies.getByIdentifier(evolutionNode.pokedexEntry.speciesId)!!
                    val speciesComponent = species.translatedName
                    val formName = evolutionNode.pokedexForm.displayForm.lowercase()
                    val formComponent =
                        if (formName == "normal") null
                        else lang("ui.pokedex.info.form.${species}-${formName.replace("-", "")}")

                    formComponent?.let { speciesComponent.copy().add(" (").add(it).add(")") } ?: speciesComponent
                }

            guiGraphics.disableScissor()
            renderTooltip(
                context = guiGraphics,
                text = text,
                posX = mouseX,
                posY = mouseY,
                delta,
                4,
            )
            enableScissors(guiGraphics)
        }


        val pokedexEntry = evolutionNode.pokedexEntry
        val pokedexForm = evolutionNode.pokedexForm
        val species = PokemonSpecies.getByIdentifier(evolutionNode.pokedexEntry.speciesId)!!
        val encounteredForms = CobblemonClient.clientPokedexData.getEncounteredForms(pokedexEntry)
        val formToRender = encounteredForms.firstOrNull { it.displayForm.equals(pokedexForm.displayForm, ignoreCase = true) }

        val renderablePokemon =
            if (formToRender != null) {
                val seenGenders = CobblemonClient.clientPokedexData.getSeenGenders(pokedexEntry, formToRender)
                val seenShinyStates = CobblemonClient.clientPokedexData.getSeenShinyStates(pokedexEntry, formToRender)

                val aspectsToDraw = (pokedexEntry.displayAspects + evolutionNode.aspects).toMutableSet()

                val formAspects = species.forms.firstOrNull { it.name.equals(formToRender.displayForm, ignoreCase = true) }?.aspects
                if (formAspects != null) {
                    aspectsToDraw.addAll(formAspects)
                }

                if (seenGenders.contains(parent.gender)) {
                    aspectsToDraw.add(parent.gender.name.lowercase())
                } else {
                    aspectsToDraw.add((seenGenders.firstOrNull() ?: Gender.GENDERLESS).name.lowercase())
                }

                if (parent.shiny && seenShinyStates.contains("shiny")) {
                    aspectsToDraw.add("shiny")
                }

                RenderablePokemon(species, aspectsToDraw)
            } else {
                null
            }

        PokemonSlotWidget.render(
            context = guiGraphics,
            x = treeX,
            y = treeY,
            isHovered = isSlotHovered,
            species = species,
            renderablePokemon = renderablePokemon,
            discoveryLevel = discoveryLevel,
        )
    }

    private fun drawEvolutionInfo(
        guiGraphics: GuiGraphics,
        renderDataAlternatives: List<DexEvolutionDisplay>,
        lineStartX: Int,
        lineEndX: Int,
        lineY: Int,
        mouseX: Int,
        mouseY: Int,
        delta: Float,
    ) {
        val icons = renderDataAlternatives.flatMap { it.requirements.map { req -> req.icon } }.distinct()
        val playerBiome = Minecraft.getInstance().player?.let { it.level().getBiome(it.blockPosition()) }
        val alternativeTooltips = renderDataAlternatives.map { alternative ->
            alternative.requirements.map { req ->
                if (req is CobblemonBiomeDexEvolutionRequirementDisplay) {
                    val tooltip = req.tooltip.copy()
                    if (playerBiome != null) {
                        val conditionFits = req.condition?.let { if (it.tag) playerBiome.`is`(TagKey.create(Registries.BIOME, it.id)) else playerBiome.`is`(it.id) } ?: true
                        val antiConditionFits = req.antiCondition?.let { if (it.tag) playerBiome.`is`(TagKey.create(Registries.BIOME, it.id)) else playerBiome.`is`(it.id) } ?: false
                        val isMet = conditionFits && !antiConditionFits
                        tooltip.add(if (isMet) Component.literal(" ✔").green() else Component.literal(" ✘").red())
                    }
                    tooltip
                } else {
                    req.tooltip
                }
            }
        }
        val tooltips = combineAlternativeTooltips(alternativeTooltips)

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

        if (icons.isEmpty()) {
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

            val index = (System.currentTimeMillis() / MILLIS_BETWEEN_ICONS_CYCLE) % icons.size
            val item = icons[index.toInt()]

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
            iconX,
            iconX + iconSize,
            iconY,
            iconY + iconSize,
        )

        if (tooltips.isNotEmpty() &&
            (isLineHovered || isIconHovered) &&
            this.isHovered(mouseX.toDouble(), mouseY.toDouble())
        ) {
            hoveredTooltips = tooltips.map { it.copy() }
        }
    }

    private fun combineAlternativeTooltips(alternatives: List<List<Component>>): List<Component> {
        val groups = alternatives.filter { it.isNotEmpty() }
        if (groups.size <= 1) return groups.firstOrNull() ?: emptyList()

        val sharedStrings = groups.map { group -> group.map { it.string }.toSet() }.reduce { acc, strings -> acc intersect strings }
        val shared = groups.first().filter { it.string in sharedStrings }.distinctBy { it.string }
        val remainders = groups
            .map { group -> group.filter { it.string !in sharedStrings } }
            .distinctBy { group -> group.map { it.string } }

        // An alternative needing nothing beyond the shared lines makes the others irrelevant
        if (remainders.any { it.isEmpty() }) return shared
        if (remainders.size == 1) return shared + remainders.first()

        return if (remainders.size >= 3 && remainders.all { it.size == 1 }) {
            shared + lang("ui.pokedex.evolution.one_of").bold() + remainders.map { it.first() }
        } else {
            // Show each alternative in full, otherwise it's unclear where the first one starts
            groups.distinctBy { group -> group.map { it.string } }.flatMapIndexed { index, group ->
                if (index == 0) group else listOf(lang("ui.pokedex.evolution.or").bold()) + group
            }
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

        val screenX = (xCenter.toInt() + xDislocation.toInt() + panningX.toInt() + x)
        val screenY = (yCenter.toInt() + yDislocation.toInt() + panningY.toInt() + y)

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

        val minPanningX = (treeHeight - 1) * -xStep / 2.0 - (width + SCROLL_SLOT_SIZE) / 2.0
        val maxPanningX = (treeHeight - 1) * xStep / 2.0 + (width + SCROLL_SLOT_SIZE) / 2.0

        val divideBy = if (treeWidth % 2 == 0) 4.0 else 2.0
        val minPanningY = (treeWidth - 1) * -yStep / 2.0 - height / 2.0 + SCROLL_SLOT_SIZE / divideBy
        val maxPanningY = (treeWidth - 1) * yStep / 2.0 + height / 2.0 - SCROLL_SLOT_SIZE / divideBy

        panningX = panningX.coerceIn(minPanningX, maxPanningX)
        panningY = panningY.coerceIn(minPanningY, maxPanningY)

        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY)
    }

    fun isHovered(mouseX: Double, mouseY: Double): Boolean {
        return mouseX.toInt() in x..(x + width) && mouseY.toInt() in y..(y + height)
    }

    fun enableScissors(guiGraphics: GuiGraphics) {
        guiGraphics.enableScissor(x + 1, y, x + width - 1, y + height)
    }

    private class EvolutionNode(
        val pokedexEntry: PokedexEntry,
        val pokedexForm: PokedexForm,
        val aspects: Set<String>,
        val pokedexEntryProgress: PokedexEntryProgress,
        override val children: List<EvolutionNode>,
        val renderDataAlternatives: List<DexEvolutionDisplay>,
    ) : TreeNode<EvolutionNode>
}