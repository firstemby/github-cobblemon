/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.gui.pokedex.widgets

import com.cobblemon.mod.common.CobblemonSounds
import com.cobblemon.mod.common.api.gui.blitk
import com.cobblemon.mod.common.api.moves.MoveTemplate
import com.cobblemon.mod.common.api.pokedex.entry.PokedexForm
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import com.cobblemon.mod.common.api.pokemon.evolution.Evolution
import com.cobblemon.mod.common.api.text.bold
import com.cobblemon.mod.common.api.text.text
import com.cobblemon.mod.common.api.tms.TechnicalMachines
import com.cobblemon.mod.common.api.types.ElementalType
import com.cobblemon.mod.common.client.CobblemonClient
import com.cobblemon.mod.common.client.CobblemonResources
import com.cobblemon.mod.common.client.gui.MoveCategoryIcon
import com.cobblemon.mod.common.client.gui.ScrollingWidget
import com.cobblemon.mod.common.client.gui.TypeIcon
import com.cobblemon.mod.common.client.gui.pokedex.PokedexGUI.Companion.arrowDownIcon
import com.cobblemon.mod.common.client.gui.pokedex.PokedexGUI.Companion.arrowUpIcon
import com.cobblemon.mod.common.client.gui.pokedex.PokedexGUI.Companion.categoryFilterIcon
import com.cobblemon.mod.common.client.gui.pokedex.PokedexGUIConstants.HALF_OVERLAY_HEIGHT
import com.cobblemon.mod.common.client.gui.pokedex.PokedexGUIConstants.HALF_OVERLAY_WIDTH
import com.cobblemon.mod.common.client.gui.pokedex.PokedexGUIConstants.SCALE
import com.cobblemon.mod.common.client.gui.pokedex.PokedexGUIConstants.SCROLL_BAR_WIDTH
import com.cobblemon.mod.common.client.gui.pokedex.PokedexGUIConstants.SCROLL_SLOT_SPACING
import com.cobblemon.mod.common.client.gui.pokedex.ScaledButton
import com.cobblemon.mod.common.client.gui.pokedex.renderTooltip
import com.cobblemon.mod.common.client.settings.ServerSettings
import com.cobblemon.mod.common.client.gui.summary.widgets.SoundlessWidget
import com.cobblemon.mod.common.client.gui.summary.widgets.screens.moves.MovesWidget.Companion.MOVE_ICON_SIZE
import com.cobblemon.mod.common.client.gui.summary.widgets.screens.moves.MovesWidget.Companion.movesAccuracyIconResource
import com.cobblemon.mod.common.client.gui.summary.widgets.screens.moves.MovesWidget.Companion.movesEffectIconResource
import com.cobblemon.mod.common.client.gui.summary.widgets.screens.moves.MovesWidget.Companion.movesPowerIconResource
import com.cobblemon.mod.common.client.render.drawScaledText
import com.cobblemon.mod.common.client.render.drawScaledTextJustifiedRight
import com.cobblemon.mod.common.pokemon.FormData
import com.cobblemon.mod.common.pokemon.Species
import com.cobblemon.mod.common.util.asIdentifierDefaultingNamespace
import com.cobblemon.mod.common.util.cobblemonResource
import com.cobblemon.mod.common.util.lang
import java.math.RoundingMode
import java.text.DecimalFormat
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.resources.sounds.SimpleSoundInstance
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MutableComponent
import net.minecraft.network.chat.Style
import net.minecraft.resources.ResourceLocation
import net.minecraft.util.Mth

class MovesLearnsetWidget(val pX: Int, val pY: Int) : SoundlessWidget(
    pX,
    pY,
    HALF_OVERLAY_WIDTH,
    HALF_OVERLAY_HEIGHT,
    lang("ui.moves")
) {
    companion object {
        private const val LIST_HEIGHT = 60
        private const val LIST_SLOT_HEIGHT = 15

        private const val MOVE_SLOT_WIDTH = 134

        private const val DESCRIPTION_SCROLLBAR_WIDTH = 2

        private val typeBar = cobblemonResource("textures/gui/pokedex/type_bar_compact.png")
        private val doubleTypeBar = cobblemonResource("textures/gui/pokedex/type_bar_compact_double.png")

        private val arrowFormLeft = cobblemonResource("textures/gui/pokedex/forms_arrow_left_compact.png")
        private val arrowFormRight = cobblemonResource("textures/gui/pokedex/forms_arrow_right_compact.png")

        private val moveCategoryBar = cobblemonResource("textures/gui/pokedex/move_category_bar.png")

        private val moveSlot = cobblemonResource("textures/gui/pokedex/move_slot.png")
        private val moveSlotSelected = cobblemonResource("textures/gui/pokedex/move_slot_select.png")
        private val tmSprite = cobblemonResource("textures/item/tms/blank_disc.png")
        private val tmSpriteUndiscovered = cobblemonResource("textures/gui/pokedex/tm_undiscovered.png")
        private val scrollBorderTop = cobblemonResource("textures/gui/pokedex/move_scroll_border.png")

        private val moveInfoBackground = cobblemonResource("textures/gui/pokedex/info_move_background.png")
    }

    private val listWidget = LearnsetMovesScrollingWidget(
        pX,
        pY + 36,
        165,
        LIST_HEIGHT
    ) { entry -> selectMove(entry) }

    private val descriptionWidget = MoveDescriptionWidget(
        pX + 66,
        pY + 119,
        68,
        28
    )

    private var moveEntries: List<LearnsetMoveEntry> = emptyList()
    private var filteredEntries: List<LearnsetMoveEntry> = emptyList()
    private var selectedEntry: LearnsetMoveEntry? = null
    private var speciesName: MutableComponent = Component.literal("")
    private var speciesNumber: MutableComponent = "0000".text()
    private var formLabel: MutableComponent = Component.literal("")
    private var primaryType: ElementalType? = null
    private var secondaryType: ElementalType? = null
    private var availableForms: List<PokedexForm> = emptyList()
    private var onFormChange: ((Boolean) -> Unit)? = null
    private var filterIndex = 0
    private var sortMode = LearnsetSort.LEVEL

    private val decimalFormat = DecimalFormat("#.##").also { it.roundingMode = RoundingMode.CEILING }

    private val formLeftButton: ScaledButton = ScaledButton(
        pX + if (secondaryType != null) 25.5F else 15.5F,
        pY + 15F,
        8,
        16,
        arrowFormLeft,
        clickAction = { onFormChange?.invoke(false) }
    ).apply { addWidget(this) }

    private val formRightButton: ScaledButton = ScaledButton(
        pX + 130.5F,
        pY + 15F,
        8,
        16,
        arrowFormRight,
        clickAction = { onFormChange?.invoke(true) }
    ).apply { addWidget(this) }

    private val categoryUpButton: ScaledButton = ScaledButton(
        pX + 84F,
        pY + 27F,
        8,
        6,
        arrowUpIcon,
        clickAction = { cycleFilter(false) }
    ).apply { addWidget(this) }

    private val categoryDownButton: ScaledButton = ScaledButton(
        pX + 84F,
        pY + 32F,
        8,
        6,
        arrowDownIcon,
        clickAction = { cycleFilter(true) }
    ).apply { addWidget(this) }

    private val sortButtons: List<ScaledButton> = LearnsetSort.entries.mapIndexed { index, sortType ->
        ScaledButton(
            pX + 93F + (index * 12F),
            pY + 26F,
            20,
            20,
            cobblemonResource("textures/gui/pokedex/button_sort_move_${sortType.name.lowercase()}.png"),
            clickAction = {
                sortMode = sortType
                applyFilter()
                sortButtons.forEachIndexed { index, button ->
                    button.isWidgetActive = sortMode == LearnsetSort.entries[index]
                }
            }
        ).apply {
            this.isWidgetActive = sortMode == sortType
            addWidget(this)
        }
    }

    init {
        addWidget(listWidget)
        addWidget(descriptionWidget)
    }

    fun setLearnset(
        species: Species,
        form: FormData,
        availableForms: List<PokedexForm>,
        selectedForm: PokedexForm,
        onFormChange: (Boolean) -> Unit
    ) {
        speciesName = species.translatedName
        speciesNumber = species.nationalPokedexNumber.toString().padStart(4, '0').text()
        primaryType = form.primaryType
        secondaryType = form.secondaryType
        this.availableForms = availableForms
        this.onFormChange = onFormChange
        updateFormLabel(species, selectedForm)
        updateFormButtons()

        moveEntries = buildLearnsetEntries(form)
        applyFilter()
        selectMove(null)
    }

    override fun renderWidget(context: GuiGraphics, mouseX: Int, mouseY: Int, delta: Float) {
        val matrices = context.pose()

        blitk(
            matrixStack = matrices,
            texture = if (secondaryType != null) doubleTypeBar else typeBar,
            x = pX,
            y = pY + 12,
            width = HALF_OVERLAY_WIDTH,
            height = 12
        )

        if (primaryType != null) {
            TypeIcon(
                x = pX + 3,
                y = pY + 13.5,
                type = primaryType!!,
                secondaryType = secondaryType,
                secondaryOffset = 9.5F,
                small = true
            ).render(context)
        }

        if (availableForms.size > 1) {
            drawScaledText(
                context = context,
                font = CobblemonResources.DEFAULT_LARGE,
                text = formLabel.bold(),
                x = pX + if (secondaryType != null) 80 else 75,
                y = pY + 15,
                shadow = true,
                centered = true
            )

            formLeftButton.render(context, mouseX, mouseY, delta)
            formRightButton.render(context, mouseX, mouseY, delta)
        }

        blitk(
            texture = moveCategoryBar,
            matrixStack = context.pose(),
            x = pX,
            y = pY + 26,
            width = 91,
            height = 10
        )

        blitk(
            texture = categoryFilterIcon,
            matrixStack = context.pose(),
            x = (pX + 3) / SCALE,
            y = (pY + 27.5) / SCALE,
            width = 14,
            height = 14,
            scale = SCALE
        )

        drawScaledText(
            context = context,
            font = CobblemonResources.DEFAULT_LARGE,
            text = (if (currentMoveCategory() == LearnsetCategory.ALL) lang("ui.pokedex.filter.all") else lang("ui.moves.${currentMoveCategory().label}")).bold(),
            x = pX + 13,
            y = pY + 27,
            shadow = true
        )

        categoryUpButton.render(context, mouseX, mouseY, delta)
        categoryDownButton.render(context, mouseX, mouseY, delta)

        sortButtons.forEachIndexed { index, button ->
            button.render(context, mouseX, mouseY, delta)

            if (button.isButtonHovered(mouseX, mouseY)) {
                renderTooltip(context, lang("ui.sort.${LearnsetSort.entries[index].name.lowercase()}").bold(), mouseX, mouseY, delta, -14)
            }
        }

        if (filteredEntries.isNotEmpty()) listWidget.renderWidget(context, mouseX, mouseY, delta)

        renderDataSection(context)

        descriptionWidget.renderWidget(context, mouseX, mouseY, delta)

        matrices.pushPose()
        matrices.translate(0.0, 0.0, 500.0) // Translate on top of other elements
        blitk(
            texture = scrollBorderTop,
            matrixStack = context.pose(),
            x = pX,
            y = pY + 36,
            width = HALF_OVERLAY_WIDTH - SCROLL_BAR_WIDTH,
            height = 3
        )
        matrices.popPose()
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, horizontalAmount: Double, verticalAmount: Double): Boolean {
        if (mouseX.toInt() in pX..(pX + HALF_OVERLAY_WIDTH) && mouseY.toInt() in (pY + 36)..(pY + 96)) {
            listWidget.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount)
        }

        if (mouseX.toInt() in (pX + 64)..(pX + 136) && mouseY.toInt() in (pY + 119)..(pY + 147)) {
            descriptionWidget.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount)
        }

        return false
    }

    private fun renderDataSection(context: GuiGraphics) {
        val move = selectedEntry?.move
        val showMoveInfo = selectedEntry?.isDiscovered == true

        drawScaledText(
            context = context,
            font = CobblemonResources.DEFAULT_LARGE,
            text = lang("ui.moves").bold(),
            x = pX + 9,
            y = pY + 97,
            shadow = true
        )

        blitk(
            matrixStack = context.pose(),
            texture = moveInfoBackground,
            x = pX + 3,
            y = pY + 109,
            width = 133,
            height = 38
        )

        drawScaledText(
            context = context,
            text = (if (showMoveInfo && move != null) move.displayName else "—".text()).bold(),
            x = pX + 5,
            y = pY + 111,
            scale = SCALE,
            colour = 0x606B6E
        )

        if (showMoveInfo && move != null) {
            drawScaledTextJustifiedRight(
                context = context,
                text = lang("ui.moves.pp", move.pp),
                x = pX + 120,
                y = pY + 111,
                scale = SCALE,
                colour = 0x606B6E
            )

            MoveCategoryIcon(x = pX + 124, y = pY + 109, category = move.damageCategory, opacity = alpha).render(context)
        }

        blitk(
            matrixStack = context.pose(),
            texture = movesPowerIconResource,
            x = (pX + 5) / SCALE,
            y = (pY + 120.5) / SCALE,
            width = MOVE_ICON_SIZE,
            height = MOVE_ICON_SIZE,
            scale = SCALE
        )

        blitk(
            matrixStack = context.pose(),
            texture = movesAccuracyIconResource,
            x = (pX + 5) / SCALE,
            y = (pY + 130.5) / SCALE,
            width = MOVE_ICON_SIZE,
            height = MOVE_ICON_SIZE,
            scale = SCALE
        )

        blitk(
            matrixStack = context.pose(),
            texture = movesEffectIconResource,
            x = (pX + 5) / SCALE,
            y = (pY + 140.5) / SCALE,
            width = MOVE_ICON_SIZE,
            height = MOVE_ICON_SIZE,
            scale = SCALE
        )

        drawScaledText(
            context = context,
            text = lang("ui.power"),
            x = pX + 12,
            y = pY + 121,
            scale = SCALE,
            colour = 0x606B6E
        )

        drawScaledText(
            context = context,
            text = lang("ui.accuracy"),
            x = pX + 12,
            y = pY + 131,
            scale = SCALE,
            colour = 0x606B6E
        )

        drawScaledText(
            context = context,
            text = lang("ui.effect"),
            x = pX + 12,
            y = pY + 141,
            scale = SCALE,
            colour = 0x606B6E
        )

        val powerText = (if (showMoveInfo && move != null && move.power.toInt() > 0) move.power.toInt().toString() else "—").text()
        drawScaledTextJustifiedRight(
            context = context,
            text = powerText,
            x = pX + 60.5,
            y = pY + 121,
            scale = SCALE,
            colour = 0x606B6E
        )

        val accuracyText = (if (showMoveInfo && move != null) formatPercentage(move.accuracy) else "—").text()
        drawScaledTextJustifiedRight(
            context = context,
            text = accuracyText,
            x = pX + 60.5,
            y = pY + 131,
            scale = SCALE,
            colour = 0x606B6E
        )

        val effectText = (if (showMoveInfo && move != null) formatPercentage(move.effectChances.firstOrNull() ?: 0.0) else "—").text()
        drawScaledTextJustifiedRight(
            context = context,
            text = effectText,
            x = pX + 60.5,
            y = pY + 141,
            scale = SCALE,
            colour = 0x606B6E
        )
    }

    private fun formatPercentage(input: Double): String {
        if (input <= 0) return "—"
        return "${decimalFormat.format(input)}%"
    }

    private fun applyFilter() {
        val filtered = when (currentMoveCategory()) {
            LearnsetCategory.ALL -> moveEntries
            LearnsetCategory.LEVEL -> moveEntries.filter { it.source == LearnsetCategory.LEVEL }
            LearnsetCategory.TM -> moveEntries.filter { it.source == LearnsetCategory.TM }
            LearnsetCategory.EGG -> moveEntries.filter { it.source == LearnsetCategory.EGG }
        }
        val sorted = sortEntries(filtered)
        filteredEntries = sorted
        listWidget.setEntries(sorted)
        if (selectedEntry !in sorted) {
            selectMove(null)
        } else {
            listWidget.setSelectedEntry(selectedEntry)
        }
    }

    private fun sortEntries(entries: List<LearnsetMoveEntry>): List<LearnsetMoveEntry> {
        return when (sortMode) {
            LearnsetSort.NAME -> entries.sortedWith(
                compareBy(
                    { !it.isDiscovered },
                    { it.move.displayName.string.lowercase() },
                    { it.move.elementalType.displayName.string.lowercase() },
                    { it.level ?: Int.MAX_VALUE }
                )
            )
            LearnsetSort.TYPE -> entries.sortedWith(
                compareBy(
                    { it.move.elementalType.displayName.string.lowercase() },
                    { !it.isDiscovered },
                    { it.move.displayName.string.lowercase() },
                    { it.level ?: Int.MAX_VALUE }
                )
            )
            LearnsetSort.LEVEL -> entries.sortedWith(
                compareBy(
                    { sourceOrder(it.source) },
                    { it.level ?: Int.MAX_VALUE },
                    { !it.isDiscovered },
                    { it.move.elementalType.displayName.string.lowercase() },
                    { it.move.displayName.string.lowercase() }
                )
            )
            LearnsetSort.DISCOVERED -> {
                entries.sortedWith(
                    compareBy(
                        {
                            TechnicalMachines.moveToTM[it.move]?.id == null
                        },
                        {
                            val tmId = TechnicalMachines.moveToTM[it.move]?.id
                            !(tmId != null && (
                            ServerSettings.unlockAllMoveDexMovesByDefault
                                || tmId in CobblemonClient.clientTMMoveData.learnedTMs
                                || TechnicalMachines.tmMap[tmId]?.isPassivelyObtained() == true
                            ))
                        },
                        { it.move.displayName.string.lowercase() },
                        { sourceOrder(it.source) }
                    )
                )
            }
        }
    }

    private fun cycleFilter(next: Boolean) {
        val total = LearnsetCategory.entries.size
        filterIndex = if (next) {
            (filterIndex + 1) % total
        } else {
            (filterIndex - 1 + total) % total
        }
        applyFilter()
    }

    private fun sourceOrder(source: LearnsetCategory): Int {
        return when (source) {
            LearnsetCategory.TM -> 1
            LearnsetCategory.EGG -> 2
            else -> 0
        }
    }

    private fun currentMoveCategory(): LearnsetCategory {
        return LearnsetCategory.entries[filterIndex.coerceIn(0, LearnsetCategory.entries.lastIndex)]
    }

    private fun updateFormButtons() {
        val hasForms = availableForms.size > 1
        formLeftButton.isVisible = hasForms
        formRightButton.isVisible = hasForms
        formLeftButton.active = hasForms
        formRightButton.active = hasForms
        formLeftButton.buttonX = pX + if (secondaryType != null) 25.5F else 15.5F
        formLeftButton.x = formLeftButton.buttonX.toInt()
    }

    private fun updateFormLabel(species: Species, form: PokedexForm) {
        val formName = if (form.displayForm.equals("normal", ignoreCase = true)) {
            ""
        } else {
            "-${form.displayForm.lowercase().replace("-", "")}"
        }
        val label = lang("ui.pokedex.info.form.${species}${formName}")
        formLabel = if (label.string.isBlank() || label.string.startsWith("cobblemon.ui.pokedex.info.form.")) {
            lang("ui.pokedex.info.form.normal")
        } else {
            label
        }
    }

    private fun selectMove(entry: LearnsetMoveEntry?) {
        selectedEntry = if (selectedEntry == entry || entry == null) null else entry
        listWidget.setSelectedEntry(selectedEntry)

        if (selectedEntry?.isDiscovered == true) {
            val description = selectedEntry!!.move.description.string
            descriptionWidget.visible = false
            descriptionWidget.setText(listOf(description))
        } else {
            descriptionWidget.visible = true
            descriptionWidget.setText(emptyList())
        }
    }

    private fun buildEvolutionMoveLevelIndex(form: FormData): Map<ResourceLocation, Map<String, Int>> {
        val evolutionForms = collectEvolutionForms(form)
        val levelsBySpecies = mutableMapOf<ResourceLocation, MutableMap<String, Int>>()

        for (evolutionForm in evolutionForms) {
            val speciesId = evolutionForm.species.resourceIdentifier
            val moveLevels = levelsBySpecies.getOrPut(speciesId) { mutableMapOf() }
            buildMoveLevelIndex(evolutionForm).forEach { (moveName, level) ->
                val current = moveLevels[moveName]
                if (current == null || level < current) {
                    moveLevels[moveName] = level
                }
            }
        }

        return levelsBySpecies
    }

    private fun buildMoveLevelIndex(form: FormData): Map<String, Int> {
        val levels = mutableMapOf<String, Int>()
        form.moves.levelUpMoves.forEach { (level, moves) ->
            moves.forEach { move ->
                val current = levels[move.name]
                if (current == null || level < current) {
                    levels[move.name] = level
                }
            }
        }
        return levels
    }

    private fun collectEvolutionForms(rootForm: FormData): List<FormData> {
        val results = mutableListOf<FormData>()
        val visited = mutableSetOf<String>()

        fun key(form: FormData): String {
            return "${form.species.resourceIdentifier}|${form.name.lowercase()}"
        }

        fun nextEvolutions(current: FormData): Set<Evolution> {
            return if (current.evolutions.isNotEmpty()) {
                current.evolutions
            } else {
                current.species.evolutions
            }
        }

        fun traverse(current: FormData) {
            for (evolution in nextEvolutions(current)) {
                val evolutionForm = resolveEvolutionForm(evolution) ?: continue
                val evolutionKey = key(evolutionForm)
                if (!visited.add(evolutionKey)) continue
                results.add(evolutionForm)
                traverse(evolutionForm)
            }
        }

        traverse(rootForm)
        return results
    }

    private fun resolveEvolutionForm(evolution: Evolution): FormData? {
        val speciesId = evolution.result.species?.asIdentifierDefaultingNamespace() ?: return null
        val species = PokemonSpecies.getByIdentifier(speciesId) ?: return null
        val formId = evolution.result.form
        return if (formId == null) {
            species.standardForm
        } else {
            species.forms.firstOrNull {
                it.formOnlyShowdownId().equals(formId, ignoreCase = true) || it.name.equals(formId, ignoreCase = true)
            } ?: species.standardForm
        }
    }

    private fun buildLearnsetEntries(form: FormData): List<LearnsetMoveEntry> {
        val entries = linkedMapOf<String, LearnsetMoveEntry>()
        val speciesId = form.species.resourceIdentifier
        val pokedexManager = CobblemonClient.clientPokedexData
        val highestLevel = pokedexManager.getSpeciesRecord(speciesId)?.getFormRecord(form.name)?.highestLevel ?: 0
        val highestEvolutionLevel = collectEvolutionForms(form).maxOfOrNull { evolutionForm ->
            val speciesRecord = pokedexManager.getSpeciesRecord(evolutionForm.species.resourceIdentifier)
            speciesRecord?.getFormRecord(evolutionForm.name)?.highestLevel ?: speciesRecord?.highestLevel ?: 0
        } ?: 0
        val learnedTMs = CobblemonClient.clientTMMoveData.learnedTMs
        val unlockAllMoveDexMovesByDefault = ServerSettings.unlockAllMoveDexMovesByDefault

        fun isLevelUpDiscovered(level: Int): Boolean {
            if (unlockAllMoveDexMovesByDefault) return true
            if (highestLevel >= level) return true
            return highestEvolutionLevel >= level
        }

        fun addEntry(
            move: MoveTemplate,
            source: LearnsetCategory,
            level: Int? = null,
            tmLocked: Boolean = false,
            isDiscovered: Boolean = true
        ) {
            val key = move.name
            if (!entries.containsKey(key)) {
                val tmId = TechnicalMachines.moveToTM[move]?.id
                val tmUnlocked = tmId != null && (
                    unlockAllMoveDexMovesByDefault
                    || tmId in learnedTMs
                    || TechnicalMachines.tmMap[tmId]?.isPassivelyObtained() == true
                )
                val resolvedTmLocked = if (source == LearnsetCategory.TM) tmLocked else false
                entries[key] = LearnsetMoveEntry(move, source, level, resolvedTmLocked, isDiscovered, tmId, tmUnlocked)
            }
        }

        form.moves.levelUpMoves.toSortedMap().forEach { (level, moves) ->
            moves.forEach { move ->
                val discovered = isLevelUpDiscovered(level)
                addEntry(move, LearnsetCategory.LEVEL, level = level, isDiscovered = discovered)
            }
        }

        form.moves.tmMoves.sortedBy { it.displayName.string }.forEach { move ->
            val tmLocked = if (unlockAllMoveDexMovesByDefault) false else {
                val tmId = TechnicalMachines.moveToTM[move]?.id
                tmId != null
                    && tmId !in learnedTMs
                    && TechnicalMachines.tmMap[tmId]?.isPassivelyObtained() != true
            }
            addEntry(move, LearnsetCategory.TM, tmLocked = tmLocked, isDiscovered = !tmLocked)
        }

        form.moves.eggMoves.sortedBy { it.displayName.string }.forEach { move ->
            addEntry(move, LearnsetCategory.EGG)
        }

        return entries.values.toList()
    }

    private enum class LearnsetCategory(val label: String) {
        ALL("all"),
        LEVEL("level"),
        TM("tm"),
        EGG("egg")
    }

    private enum class LearnsetSort {
        LEVEL,
        NAME,
        TYPE,
        DISCOVERED
    }

    private data class LearnsetMoveEntry(
        val move: MoveTemplate,
        val source: LearnsetCategory,
        val level: Int? = null,
        val tmLocked: Boolean = false,
        val isDiscovered: Boolean = true,
        val tmId: ResourceLocation? = null,
        val tmUnlocked: Boolean = false
    )

    private class LearnsetMovesScrollingWidget(
        listX: Int,
        val listY: Int,
        listWidth: Int,
        listHeight: Int,
        val onSelect: (LearnsetMoveEntry?) -> Unit
    ) : ScrollingWidget<LearnsetMovesScrollingWidget.MoveEntrySlot>(
        width = listWidth,
        height = listHeight,
        left = listX,
        top = listY - listHeight,
        slotHeight = LIST_SLOT_HEIGHT
    ) {
        private var selectedEntry: LearnsetMoveEntry? = null

        fun setEntries(entries: List<LearnsetMoveEntry>) {
            clearEntries()
            entries.forEach { entry -> addEntry(MoveEntrySlot(entry, this)) }
            scrollAmount = 0.0
        }

        fun setSelectedEntry(entry: LearnsetMoveEntry?) {
            selectedEntry = entry
        }

        override fun getScrollbarPosition(): Int {
            return left + HALF_OVERLAY_WIDTH - SCROLL_BAR_WIDTH
        }

        override fun renderScrollbar(context: GuiGraphics, mouseX: Int, mouseY: Int, delta: Float) {
            val xLeft = this.scrollbarPosition
            val yMargin = 2
            val yStart = y + yMargin

            val barHeight = this.bottom - yMargin - yStart

            var yBottom = ((barHeight * barHeight).toFloat() / this.maxPosition.toFloat()).toInt()
            yBottom = Mth.clamp(yBottom, 32, barHeight - 8)
            var yTop = scrollAmount.toInt() * (barHeight - yBottom) / this.maxScroll + yStart
            if (yTop < yStart) yTop = yStart

            // Scroll Track
            blitk(
                texture = EntriesScrollingWidget.scrollbarTrack,
                matrixStack = context.pose(),
                x = xLeft,
                y = yStart,
                width = SCROLL_BAR_WIDTH,
                height = height - (yMargin * 2)
            )

            // Scroll Slide
            blitk(
                texture = EntriesScrollingWidget.scrollbarSlide,
                matrixStack = context.pose(),
                x = xLeft,
                y = yTop,
                width = SCROLL_BAR_WIDTH,
                height = yBottom
            )
        }

        override fun getEntry(index: Int): MoveEntrySlot {
            return children()[index] as MoveEntrySlot
        }

        inner class MoveEntrySlot(val entry: LearnsetMoveEntry, private val parentList: LearnsetMovesScrollingWidget) : Slot<MoveEntrySlot>() {
            private var lastX = 0
            private var lastY = 0

            override fun render(context: GuiGraphics, index: Int, y: Int, x: Int, entryWidth: Int, entryHeight: Int, mouseX: Int, mouseY: Int, hovered: Boolean, tickDelta: Float) {
                lastX = x
                lastY = y

                val barY = y + SCROLL_SLOT_SPACING
                blitk(
                    matrixStack = context.pose(),
                    texture = moveSlot,
                    x = x,
                    y = barY,
                    width = MOVE_SLOT_WIDTH,
                    height = 13
                )

                val isSlotSelected = entry == parentList.selectedEntry
                val isSlotHovered = hovered && (mouseX <= x + MOVE_SLOT_WIDTH)
                if (isSlotHovered || isSlotSelected) {
                    blitk(
                        matrixStack = context.pose(),
                        texture = moveSlotSelected,
                        x = x,
                        y = barY,
                        width = MOVE_SLOT_WIDTH,
                        height = 13,
                        textureHeight = 26,
                        vOffset = if (isSlotHovered && !isSlotSelected) 13 else 0
                    )
                }

                TypeIcon(
                    x = x + 3,
                    y = barY + 2,
                    type = entry.move.elementalType,
                    small = true
                ).render(context)

                val displayName = if (entry.isDiscovered) {
                    entry.move.displayName
                } else {
                    lang("ui.generic.question_marks")
                }

                drawScaledText(
                    context = context,
                    font = CobblemonResources.DEFAULT_LARGE,
                    text = displayName.bold(),
                    x = x + 14,
                    y = barY + 2,
                    shadow = true
                )

                val moveCategoryLabel = if (entry.level != null) lang("ui.lv.number", entry.level) else lang("ui.moves.${entry.source.label}")
                drawScaledTextJustifiedRight(
                    context = context,
                    font = CobblemonResources.DEFAULT_LARGE,
                    text = moveCategoryLabel.bold(),
                    x = x + 131.5,
                    y = barY + 2,
                    shadow = true
                )

                if (entry.tmId != null) {
                    val tmX = (x + 131.5 - (Minecraft.getInstance().font.width(moveCategoryLabel.bold()) + 10))
                    val tmY = (barY + 2.5)

                    blitk(
                        matrixStack = context.pose(),
                        texture = if (entry.tmUnlocked) tmSprite else tmSpriteUndiscovered,
                        x = tmX / SCALE,
                        y = tmY / SCALE,
                        width = 16,
                        height = 16,
                        scale = SCALE
                    )

                    val hovering = mouseX.toFloat() in tmX..(tmX + 8) && mouseY.toFloat() in tmY..(tmY + 8)
                    if (hovering) {
                        val posY = maxOf(mouseY, listY + 14)
                        val tooltipKey = if (entry.tmUnlocked) "ui.moves.learnset.tm.discovered" else "egg_group.undiscovered"
                        renderTooltip(context, lang(tooltipKey).bold(), mouseX, posY, tickDelta, -14)
                    }
                }
            }

            override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
                val canClick = mouseX < (lastX + (HALF_OVERLAY_WIDTH - 3))
                if (canClick) {
                    onSelect(entry)
                    Minecraft.getInstance().soundManager.play(SimpleSoundInstance.forUI(CobblemonSounds.POKEDEX_CLICK_SHORT, 1.0F))
                }
                return true
            }

            override fun getNarration(): Component {
                return if (entry.isDiscovered) entry.move.displayName else lang("ui.generic.question_marks")
            }
        }
    }

    private class MoveDescriptionWidget(pX: Int, pY: Int, width: Int, height: Int) : ScrollingWidget<MoveDescriptionWidget.TextSlot>(
        left = pX,
        top = pY - height,
        width = width,
        height = height,
        slotHeight = 7
    ) {
        fun setText(text: Collection<String>) {
            clearEntries()
            text.forEach { line ->
                Minecraft.getInstance().font.splitter.splitLines(
                    Component.literal(line),
                    ((width - DESCRIPTION_SCROLLBAR_WIDTH - 2) / SCALE).toInt(),
                    Style.EMPTY
                ).stream()
                    .map { it.string }
                    .forEach { addEntry(TextSlot(it)) }
            }
            scrollAmount = 0.0
        }

        override fun renderScrollbar(context: GuiGraphics, mouseX: Int, mouseY: Int, delta: Float) {
            val xLeft = this.scrollbarPosition
            val yMargin = 1
            val yStart = y + yMargin

            val barHeight = this.bottom - yMargin - yStart

            var yBottom = ((barHeight * barHeight).toFloat() / this.maxPosition.toFloat()).toInt()
            yBottom = Mth.clamp(yBottom, 16, barHeight - 6)
            var yTop = scrollAmount.toInt() * (barHeight - yBottom) / this.maxScroll + yStart
            if (yTop < yStart) yTop = yStart

            // Scroll Track
            blitk(
                texture = InfoTextScrollWidget.scrollbarTrack,
                matrixStack = context.pose(),
                x = xLeft + 0.5,
                y = yStart,
                width = DESCRIPTION_SCROLLBAR_WIDTH / 2,
                height = height - (yMargin * 2)
            )

            // Scroll Slide
            blitk(
                texture = InfoTextScrollWidget.scrollbarSlide,
                matrixStack = context.pose(),
                x = xLeft,
                y = yTop,
                width = DESCRIPTION_SCROLLBAR_WIDTH,
                height = yBottom
            )
        }

        override fun getScrollbarPosition(): Int {
            return left + width - DESCRIPTION_SCROLLBAR_WIDTH
        }

        class TextSlot(val text: String) : Slot<TextSlot>() {
            override fun render(context: GuiGraphics, index: Int, y: Int, x: Int, entryWidth: Int, entryHeight: Int, mouseX: Int, mouseY: Int, hovered: Boolean, tickDelta: Float) {
                drawScaledText(
                    context = context,
                    text = text.text(),
                    x = x,
                    y = y + 2,
                    scale = SCALE,
                    shadow = false,
                    colour = 0x606B6E
                )
            }

            override fun getNarration(): Component {
                return Component.literal(text)
            }
        }
    }
}
