package com.cobblemon.mod.common.client.gui.pokedex.widgets.evolutiontree

import com.cobblemon.mod.common.api.gui.*
import com.cobblemon.mod.common.api.pokedex.Dexes
import com.cobblemon.mod.common.api.pokedex.PokedexEntryProgress
import com.cobblemon.mod.common.api.pokedex.entry.PokedexEntry
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import com.cobblemon.mod.common.api.pokemon.evolution.Evolution
import com.cobblemon.mod.common.api.text.bold
import com.cobblemon.mod.common.client.CobblemonResources
import com.cobblemon.mod.common.client.gui.pokedex.PokedexGUIConstants
import com.cobblemon.mod.common.client.gui.pokedex.PokedexGUIConstants.SCROLL_SLOT_SIZE
import com.cobblemon.mod.common.client.gui.pokedex.renderTooltip
import com.cobblemon.mod.common.client.gui.pokedex.widgets.PokemonSlotWidget
import com.cobblemon.mod.common.client.gui.summary.widgets.SoundlessWidget
import com.cobblemon.mod.common.client.render.drawScaledText
import com.cobblemon.mod.common.client.render.renderScaledGuiItemIcon
import com.cobblemon.mod.common.util.cobblemonResource
import com.cobblemon.mod.common.util.lang
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.network.chat.Component

class EvolutionTreeWidget(
    val pX: Int,
    val pY: Int,
) : SoundlessWidget(
    pX, pY,
    PokedexGUIConstants.HALF_OVERLAY_WIDTH,
    PokedexGUIConstants.POKEMON_DESCRIPTION_HEIGHT,
    lang("ui.pokedex.pokemon_info")
) {
    companion object {
        private val slotResource = cobblemonResource("textures/gui/pokedex/pokedex_slot.png")

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
    }

    private var rootTreeDrawingNode: TreeDrawingNode<EvolutionNode>? = null
    private var treeDrawingBounds: TreeDrawingBounds? = null
    private var zoomLevel: Double = 0.0
    private var panningX: Double = 0.0
    private var panningY: Double = 0.0

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
            enableScissors(guiGraphics)
            renderTreeDrawingNode(guiGraphics, it, mouseX, mouseY, partialTick)
            guiGraphics.disableScissor()
        }
    }

    fun setup(pokedexEntry: PokedexEntry? = null) {
        val rootPokedexEntry = findRootPokedexEntry(pokedexEntry)
        val rootEvolutionNode = buildEvolutionTree(rootPokedexEntry)

        createDrawingTree(rootEvolutionNode).also {
            rootTreeDrawingNode = it
            treeDrawingBounds = calculateBounds<EvolutionNode>(it)
        }

        zoomLevel = 0.0
        panningX = 0.0
        panningY = 0.0
    }

    private fun findRootPokedexEntry(pokedexEntry: PokedexEntry? = null): PokedexEntry {
        var rootSpecies = pokedexEntry?.speciesId?.let { PokemonSpecies.getByIdentifier(it) }!!
        while (rootSpecies.preEvolution != null) {
            rootSpecies = rootSpecies.preEvolution!!.species
        }

        return Dexes.findDexEntryBySpeciesId(rootSpecies.resourceIdentifier)
            ?: throw RuntimeException("Root dex entry not found")
    }

    private fun buildEvolutionTree(pokedexEntry: PokedexEntry, evolution: Evolution? = null): EvolutionNode {
        val species = PokemonSpecies.getByIdentifier(pokedexEntry.speciesId)!!
        val children = species.evolutions.map { evolution ->
            // TODO: figure out if this works for fakemon
            val childPokedexEntry =
                Dexes.findDexEntryBySpeciesId(cobblemonResource(evolution.result.species.toString()))
                    ?: throw RuntimeException("Dex entry not found")

            return@map buildEvolutionTree(childPokedexEntry, evolution)
        }

        return EvolutionNode(pokedexEntry, evolution, children)
    }

    private fun renderTreeDrawingNode(
        guiGraphics: GuiGraphics,
        treeDrawingNode: TreeDrawingNode<EvolutionNode>,
        mouseX: Int,
        mouseY: Int,
        delta: Float,
    ) {
        val (x, y) = getDrawingCoordinates(treeDrawingNode)

        if (treeDrawingNode.children.size == 1) {
            val child = treeDrawingNode.children.first()
            val (childX, _) = getDrawingCoordinates(child)

            val lineStartX = x + SCROLL_SLOT_SIZE
            val lineEndX = childX
            val lineY = y + SCROLL_SLOT_SIZE / 2

            guiGraphics.hLine(
                lineStartX,
                lineEndX,
                lineY,
                LINE_COLOR,
            )

            child.value.evolution?.let { evolution ->
                drawEvolutionInfo(
                    guiGraphics,
                    evolution,
                    lineStartX,
                    lineEndX,
                    lineY,
                    mouseX,
                    mouseY,
                    delta,
                )
            }
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

                val lineStartX = treeForkEnd
                val lineEndX = childX
                val lineY = childY + SCROLL_SLOT_SIZE / 2

                guiGraphics.hLine(
                    lineStartX,
                    lineEndX,
                    lineY,
                    LINE_COLOR,
                )

                child.value.evolution?.let { evolution ->
                    drawEvolutionInfo(
                        guiGraphics,
                        evolution,
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

        val isSlotHovered =
            isElementHovered(mouseX, mouseY, x, x + SCROLL_SLOT_SIZE, y, y + SCROLL_SLOT_SIZE)

        PokemonSlotWidget.Companion.render(
            context = guiGraphics,
            x = x,
            y = y,
            hovered = isSlotHovered,
            discoveryLevel = PokedexEntryProgress.CAUGHT,
            pokedexEntry = treeDrawingNode.value.pokedexEntry,
            showDexNumber = true,
            showCaughtIcon = true,
        )

        treeDrawingNode.children.forEach { child ->
            renderTreeDrawingNode(guiGraphics, child, mouseX, mouseY, delta)
        }
    }

    private fun drawEvolutionInfo(
        guiGraphics: GuiGraphics,
        evolution: Evolution,
        lineStartX: Int,
        lineEndX: Int,
        lineY: Int,
        mouseX: Int,
        mouseY: Int,
        delta: Float,
    ) {
        // TODO: instantiate this only once
        val evolutionVisitor = EvolutionTreeEvolutionVisitor()
        val drawData = evolutionVisitor.visit(evolution) ?: return

        val lineXMiddle = (lineStartX + lineEndX) / 2

        guiGraphics.pose().pushPose()

        if (drawData.icons.isNotEmpty()) {
            Minecraft.getInstance().let {
                it.mainRenderTarget.bindWrite(false)

                // TODO: this is not working as intended, the blurring is too glitchy
                guiGraphics.enableScissor(
                    (lineXMiddle + ICON_X_PADDING - ICON_SLOT_HALF_PADDING).toInt() + 1,
                    (lineY + ICON_Y_PADDING - ICON_SLOT_HALF_PADDING).toInt() + 1,
                    (lineXMiddle + ICON_X_PADDING - ICON_SLOT_HALF_PADDING + ICON_SLOT_BACKGROUND_SIZE + ICON_SLOT_PADDING).toInt() - 1,
                    (lineY + ICON_Y_PADDING - ICON_SLOT_HALF_PADDING + ICON_SLOT_BACKGROUND_SIZE + ICON_SLOT_PADDING).toInt() - 1,
                )

                it.gameRenderer.processBlurEffect(delta)
                guiGraphics.disableScissor()
                it.mainRenderTarget.bindWrite(true)
            }

            blitk(
                matrixStack = guiGraphics.pose(),
                texture = slotResource,
                x = lineXMiddle + ICON_X_PADDING - ICON_SLOT_HALF_PADDING,
                y = lineY + ICON_Y_PADDING - ICON_SLOT_HALF_PADDING,
                width = ICON_SLOT_BACKGROUND_SIZE + ICON_SLOT_PADDING,
                height = ICON_SLOT_BACKGROUND_SIZE + ICON_SLOT_PADDING,
            )

            val index = (System.currentTimeMillis() / MILLIS_BETWEEN_ICONS_CYCLE) % drawData.icons.size
            val item = drawData.icons[index.toInt()]

            renderScaledGuiItemIcon(
                itemStack = item.defaultInstance,
                x = lineXMiddle + ICON_X_PADDING,
                y = lineY + ICON_Y_PADDING,
                matrixStack = guiGraphics.pose(),
                scale = PokedexGUIConstants.SCALE.toDouble(),
            )
        }

        guiGraphics.pose().popPose()

        if (drawData.tooltips.isNotEmpty() &&
            isElementHovered(mouseX, mouseY, lineStartX, lineEndX, lineY - 2, lineY + 2) &&
            isHovered(mouseX.toDouble(), mouseY.toDouble())
        ) {
            guiGraphics.disableScissor()
            renderTooltip(
                guiGraphics,
                drawData.tooltips,
                mouseX,
                mouseY,
                delta,
                4,
            )
            enableScissors(guiGraphics)
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

        val screenX = (xCenter + xDislocation + panningX + pX).toInt()
        val screenY = (yCenter + yDislocation + panningY + pY).toInt()

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
        val minPanningY = (treeWidth- 1) * -yStep.toDouble() / 2.0 - height / 2.0 + SCROLL_SLOT_SIZE / divideBy
        val maxPanningY = (treeWidth - 1) * yStep.toDouble() / 2.0 + height / 2.0 - SCROLL_SLOT_SIZE / divideBy

        panningX = panningX.coerceIn(minPanningX, maxPanningX)
        panningY = panningY.coerceIn(minPanningY, maxPanningY)

        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY)
    }

    fun isHovered(mouseX: Double, mouseY: Double): Boolean {
        return mouseX.toInt() in pX..(pX + width) && mouseY.toInt() in pY..(pY + height)
    }

    fun enableScissors(guiGraphics: GuiGraphics) {
        guiGraphics.enableScissor(pX, pY, pX + width, pY + height)
    }

    private class EvolutionNode(
        val pokedexEntry: PokedexEntry,
        val evolution: Evolution?,
        override val children: List<EvolutionNode>
    ) : TreeNode<EvolutionNode>
}