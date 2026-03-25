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
import com.cobblemon.mod.common.api.text.font
import com.cobblemon.mod.common.api.text.text
import com.cobblemon.mod.common.api.tms.TechnicalMachines
import com.cobblemon.mod.common.api.types.ElementalType
import com.cobblemon.mod.common.client.CobblemonClient
import com.cobblemon.mod.common.client.CobblemonResources
import com.cobblemon.mod.common.client.gui.MoveCategoryIcon
import com.cobblemon.mod.common.client.gui.ScrollingWidget
import com.cobblemon.mod.common.client.gui.TypeIcon
import com.cobblemon.mod.common.client.gui.pokedex.PokedexGUIConstants.HALF_OVERLAY_HEIGHT
import com.cobblemon.mod.common.client.gui.pokedex.PokedexGUIConstants.HALF_OVERLAY_WIDTH
import com.cobblemon.mod.common.client.gui.pokedex.PokedexGUIConstants.POKEMON_DESCRIPTION_PADDING
import com.cobblemon.mod.common.client.gui.pokedex.PokedexGUIConstants.SCALE
import com.cobblemon.mod.common.client.gui.pokedex.PokedexGUIConstants.SCROLL_BAR_WIDTH
import com.cobblemon.mod.common.client.gui.pokedex.ScaledButton
import com.cobblemon.mod.common.client.gui.pokedex.renderTooltip
import com.cobblemon.mod.common.client.settings.ServerSettings
import com.cobblemon.mod.common.client.gui.summary.widgets.SoundlessWidget
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
import net.minecraft.util.FastColor
import net.minecraft.util.Mth

class MovesLearnsetWidget(val pX: Int, val pY: Int) : SoundlessWidget(
    pX,
    pY,
    HALF_OVERLAY_WIDTH,
    HALF_OVERLAY_HEIGHT,
    Component.literal("MovesLearnset")
) {
    companion object {
        private const val LIST_TOP_OFFSET = -15
        private const val LIST_HEIGHT = 54
        private const val LIST_SLOT_HEIGHT = 10
        private const val LIST_SIDE_PADDING = 4
        private const val LIST_TM_ICON_RENDER_SIZE = 8
        private const val LIST_TM_ICON_OFFSET = LIST_TM_ICON_RENDER_SIZE + 1
        private const val LIST_TM_ICON_TEXTURE_SIZE = 16
        private const val LIST_TYPE_ICON_SIZE = 18
        private val LIST_BACKGROUND_COLOR = FastColor.ARGB32.color(255, 239, 253, 255)

        private const val DATA_TOP_OFFSET = 108
        private const val DATA_ROW_HEIGHT = 10
        private const val DATA_LABEL_OFFSET_X = 14
        private const val DATA_ICON_SIZE = 10
        private const val DATA_INFO_TOP_OFFSET = 7

        private const val DESCRIPTION_TOP_OFFSET = 71
        private const val DESCRIPTION_HEIGHT = 38
        private const val DESCRIPTION_SCROLLBAR_WIDTH = 3
        private const val DESCRIPTION_SCROLLBAR_OFFSET = 13
        private const val DESCRIPTION_LEFT_OFFSET = 62
        private const val DESCRIPTION_DIVIDER_OFFSET = 6

        private val overlayResource = cobblemonResource("textures/gui/pokedex/pokedex_screen_info_overlay.png")
        private val arrowFormLeft = cobblemonResource("textures/gui/pokedex/forms_arrow_left.png")
        private val arrowFormRight = cobblemonResource("textures/gui/pokedex/forms_arrow_right.png")
        private val typeBar = cobblemonResource("textures/gui/pokedex/type_bar.png")
        private val typeBarDouble = cobblemonResource("textures/gui/pokedex/type_bar_double.png")
        private val filterArrowLeft = cobblemonResource("textures/gui/pokedex/info_arrow_left.png")
        private val filterArrowRight = cobblemonResource("textures/gui/pokedex/info_arrow_right.png")
        private val sortAlphaIcon = cobblemonResource("textures/gui/pokedex/moves_sort_alpha.png")
        private val sortTypeIcon = cobblemonResource("textures/gui/pokedex/moves_sort_type.png")
        private val sortSourceIcon = cobblemonResource("textures/gui/pokedex/moves_sort_source.png")
        private val sortDiscoveredIcon = cobblemonResource("textures/gui/pokedex/moves_sort_discovered.png")
        private val moveDexTypeIcons = cobblemonResource("textures/gui/pokedex/types_small_dex.png")
        private val tmDiscIcon = cobblemonResource("textures/item/tms/tm.png")
        private val movesPowerIconResource = cobblemonResource("textures/gui/summary/summary_moves_icon_power.png")
        private val movesAccuracyIconResource = cobblemonResource("textures/gui/summary/summary_moves_icon_accuracy.png")
        private val movesCategoryIconResource = cobblemonResource("textures/gui/summary/summary_moves_icon_category.png")

        // Match PokemonInfoWidget placement
        private const val TYPE_BAR_Y = 14
        private const val TYPE_BAR_HEIGHT = 25
        private const val TYPE_ICON_X = 3
        private const val TYPE_ICON_Y = 17
        private const val FORM_LABEL_X = 85
        private const val FORM_LABEL_Y = 14
        private const val FORM_ARROW_LEFT_X = 56F
        private const val FORM_ARROW_RIGHT_X = 107F
        private const val FORM_ARROW_Y = 15F
        private const val FORM_ARROW_WIDTH = 10
        private const val FORM_ARROW_HEIGHT = 16

        // Adjustable filter arrow placement
        private const val FILTER_ARROW_LEFT_X = 56F
        private const val FILTER_ARROW_RIGHT_X = 107F
        private const val FILTER_ARROW_Y = 28F
        private const val FILTER_ARROW_WIDTH = 7
        private const val FILTER_ARROW_HEIGHT = 10
        private const val FILTER_LABEL_X = 84
        private const val FILTER_LABEL_Y = 26

        // Sort button placement
        private const val SORT_BUTTON_X = 125F
        private const val SORT_BUTTON_Y = 27.5F
        private const val SORT_BUTTON_WIDTH = 20
        private const val SORT_BUTTON_HEIGHT = 20
    }

    private val listWidget = LearnsetMovesScrollingWidget(
        pX + LIST_SIDE_PADDING,
        pY + LIST_TOP_OFFSET,
        HALF_OVERLAY_WIDTH - (LIST_SIDE_PADDING * 2),
        LIST_HEIGHT
    ) { entry -> selectMove(entry) }

    private val descriptionWidget = MoveDescriptionWidget(
        pX + LIST_SIDE_PADDING + DESCRIPTION_LEFT_OFFSET,
        pY + DESCRIPTION_TOP_OFFSET,
        HALF_OVERLAY_WIDTH - (LIST_SIDE_PADDING * 2) - 50,
        DESCRIPTION_HEIGHT
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
    private var sortMode = LearnsetSort.SOURCE

    private val decimalFormat = DecimalFormat("#.##").also { it.roundingMode = RoundingMode.CEILING }

    private val formLeftButton: ScaledButton = ScaledButton(
        pX + FORM_ARROW_LEFT_X,
        pY + FORM_ARROW_Y,
        FORM_ARROW_WIDTH,
        FORM_ARROW_HEIGHT,
        arrowFormLeft,
        clickAction = { onFormChange?.invoke(false) }
    ).apply { addWidget(this) }

    private val formRightButton: ScaledButton = ScaledButton(
        pX + FORM_ARROW_RIGHT_X,
        pY + FORM_ARROW_Y,
        FORM_ARROW_WIDTH,
        FORM_ARROW_HEIGHT,
        arrowFormRight,
        clickAction = { onFormChange?.invoke(true) }
    ).apply { addWidget(this) }

    private val filterLeftButton: ScaledButton = ScaledButton(
        pX + FILTER_ARROW_LEFT_X,
        pY + FILTER_ARROW_Y,
        FILTER_ARROW_WIDTH,
        FILTER_ARROW_HEIGHT,
        filterArrowLeft,
        clickAction = { cycleFilter(false) }
    ).apply { addWidget(this) }

    private val filterRightButton: ScaledButton = ScaledButton(
        pX + FILTER_ARROW_RIGHT_X,
        pY + FILTER_ARROW_Y,
        FILTER_ARROW_WIDTH,
        FILTER_ARROW_HEIGHT,
        filterArrowRight,
        clickAction = { cycleFilter(true) }
    ).apply { addWidget(this) }

    private val sortButton: ScaledButton = ScaledButton(
        pX + SORT_BUTTON_X,
        pY + SORT_BUTTON_Y,
        SORT_BUTTON_WIDTH,
        SORT_BUTTON_HEIGHT,
        sortSourceIcon,
        clickAction = { toggleSort() }
    ).apply { addWidget(this) }

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
            texture = overlayResource,
            x = pX,
            y = pY,
            width = HALF_OVERLAY_WIDTH,
            height = HALF_OVERLAY_HEIGHT
        )

        drawScaledText(
            context = context,
            font = CobblemonResources.DEFAULT_LARGE,
            text = speciesNumber.bold(),
            x = pX + 3,
            y = pY + 1,
            shadow = true
        )

        if (!speciesName.string.isBlank()) {
            drawScaledText(
                context = context,
                font = CobblemonResources.DEFAULT_LARGE,
                text = speciesName.bold(),
                x = pX + 26,
                y = pY + 1,
                colour = 0x606B6E
            )
        }

        blitk(
            matrixStack = context.pose(),
            texture = if (secondaryType != null) typeBarDouble else typeBar,
            x = pX,
            y = pY + TYPE_BAR_Y,
            width = HALF_OVERLAY_WIDTH,
            height = TYPE_BAR_HEIGHT
        )

        if (primaryType != null) {
            TypeIcon(
                x = pX + TYPE_ICON_X,
                y = pY + TYPE_ICON_Y,
                type = primaryType!!,
                secondaryType = secondaryType
            ).render(context)
        }

        drawScaledText(
            context = context,
            font = CobblemonResources.DEFAULT_LARGE,
            text = formLabel.bold(),
            x = pX + FORM_LABEL_X,
            y = pY + FORM_LABEL_Y,
            shadow = true,
            centered = true
        )

        formLeftButton.render(context, mouseX, mouseY, delta)
        formRightButton.render(context, mouseX, mouseY, delta)

        drawScaledText(
            context = context,
            font = CobblemonResources.DEFAULT_LARGE,
            text = currentFilter().label.bold(),
            x = pX + FILTER_LABEL_X,
            y = pY + FILTER_LABEL_Y,
            shadow = true,
            centered = true
        )

        filterLeftButton.render(context, mouseX, mouseY, delta)
        filterRightButton.render(context, mouseX, mouseY, delta)
        sortButton.render(context, mouseX, mouseY, delta)

        if (sortButton.isButtonHovered(mouseX, mouseY)) {
            val sortKey = "ui.moves.learnset.sort.${sortMode.name.lowercase()}"
            renderTooltip(context, lang(sortKey).bold(), mouseX, mouseY, delta, -14)
        }

        /*drawScaledText(
            context = context,
            font = CobblemonResources.DEFAULT_LARGE,
            text = lang("ui.moves").bold(),
            x = pX + 4,
            y = pY + 11,
            shadow = true
        )*/

        renderListBackground(context)
        if (filteredEntries.isEmpty()) {
            drawScaledText(
                context = context,
                text = Component.literal("No moves available."),
                x = pX + (HALF_OVERLAY_WIDTH / 2) - 20,
                y = pY + LIST_TOP_OFFSET + (LIST_HEIGHT / 2) + 30,
                shadow = false,
                colour = 0x606B6E,
                scale = SCALE,
                centered = true
            )
        } else {
            listWidget.renderWidget(context, mouseX, mouseY, delta)
        }

        renderDescriptionDivider(context)
        renderDataSection(context)

        descriptionWidget.renderWidget(context, mouseX, mouseY, delta)
    }

    private fun renderListBackground(context: GuiGraphics) {
        val left = pX + LIST_SIDE_PADDING - 3
        val top = pY + LIST_TOP_OFFSET + 54
        val right = left + listWidget.width + 6
        val bottom = top + LIST_HEIGHT
        context.fill(left, top, right, bottom, LIST_BACKGROUND_COLOR)
    }

    private fun renderDescriptionDivider(context: GuiGraphics) {
        val dividerX = pX + LIST_SIDE_PADDING + DESCRIPTION_LEFT_OFFSET + DESCRIPTION_DIVIDER_OFFSET
        val dividerTop = pY + DESCRIPTION_TOP_OFFSET + 39
        val dividerBottom = pY + DESCRIPTION_TOP_OFFSET + DESCRIPTION_HEIGHT + 37
        context.fill(dividerX, dividerTop, dividerX + 1, dividerBottom, FastColor.ARGB32.color(255, 126, 231, 229))
    }

    private fun renderDataSection(context: GuiGraphics) {
        val dataTop = pY + DATA_TOP_OFFSET
        val dataRight = pX + HALF_OVERLAY_WIDTH - 70
        val entry = selectedEntry
        val move = entry?.move
        val showMoveInfo = entry?.isDiscovered == true

        drawScaledText(
            context = context,
            font = CobblemonResources.DEFAULT_LARGE,
            text = Component.literal("Data").bold(),
            x = pX + 4,
            y = dataTop - 10,
            shadow = true
        )

        blitk(
            matrixStack = context.pose(),
            texture = movesPowerIconResource,
            x = (pX + 6) / SCALE,
            y = (dataTop + 1 + DATA_INFO_TOP_OFFSET) / SCALE,
            width = DATA_ICON_SIZE,
            height = DATA_ICON_SIZE,
            scale = SCALE
        )

        blitk(
            matrixStack = context.pose(),
            texture = movesAccuracyIconResource,
            x = (pX + 6) / SCALE,
            y = (dataTop + 1 + DATA_ROW_HEIGHT + DATA_INFO_TOP_OFFSET) / SCALE,
            width = DATA_ICON_SIZE,
            height = DATA_ICON_SIZE,
            scale = SCALE
        )

        blitk(
            matrixStack = context.pose(),
            texture = movesCategoryIconResource,
            x = (pX + 6) / SCALE,
            y = (dataTop + 1 + (DATA_ROW_HEIGHT * 2) + DATA_INFO_TOP_OFFSET) / SCALE,
            width = DATA_ICON_SIZE,
            height = DATA_ICON_SIZE,
            scale = SCALE
        )

        drawScaledText(
            context = context,
            text = lang("ui.power"),
            x = pX + DATA_LABEL_OFFSET_X,
            y = dataTop + 1 + DATA_INFO_TOP_OFFSET,
            scale = SCALE,
            shadow = true
        )

        drawScaledText(
            context = context,
            text = lang("ui.accuracy"),
            x = pX + DATA_LABEL_OFFSET_X,
            y = dataTop + 1 + DATA_ROW_HEIGHT + DATA_INFO_TOP_OFFSET,
            scale = SCALE,
            shadow = true
        )

        drawScaledText(
            context = context,
            text = Component.literal("ui.category"),
            x = pX + DATA_LABEL_OFFSET_X,
            y = dataTop + 1 + (DATA_ROW_HEIGHT * 2) + DATA_INFO_TOP_OFFSET,
            scale = SCALE,
            shadow = true
        )

        val powerText = if (showMoveInfo && move != null && move.power.toInt() > 0) move.power.toInt().toString() else "-"
        drawScaledTextJustifiedRight(
            context = context,
            text = powerText.text(),
            x = dataRight,
            y = dataTop + 1  + DATA_INFO_TOP_OFFSET,
            scale = SCALE,
            shadow = true
        )

        val accuracyText = if (showMoveInfo && move != null) formatAccuracy(move.accuracy) else "-"
        drawScaledTextJustifiedRight(
            context = context,
            text = accuracyText.text(),
            x = dataRight,
            y = dataTop + 1 + DATA_ROW_HEIGHT + DATA_INFO_TOP_OFFSET,
            scale = SCALE,
            shadow = true
        )

        val categoryText = if (showMoveInfo && move != null) move.damageCategory.displayName else "-".text()
        drawScaledTextJustifiedRight(
            context = context,
            text = categoryText as MutableComponent,
            x = dataRight,
            y = dataTop + 1 + (DATA_ROW_HEIGHT * 2) + DATA_INFO_TOP_OFFSET,
            scale = SCALE,
            shadow = true
        )

        /*if (showMoveInfo && move != null) {
            MoveCategoryIcon(
                x = pX + 6,
                y = dataTop + 2 + (DATA_ROW_HEIGHT * 2) + DATA_INFO_TOP_OFFSET,
                category = move.damageCategory
            ).render(context)
        }*/
    }

    private fun formatAccuracy(input: Double): String {
        if (input <= 0) return "-"
        return "${decimalFormat.format(input)}%"
    }

    private fun applyFilter() {
        val filtered = when (currentFilter()) {
            LearnsetFilter.ALL -> moveEntries
            LearnsetFilter.LEVEL_UP -> moveEntries.filter { it.source == LearnsetSource.LEVEL_UP }
            LearnsetFilter.TM -> moveEntries.filter { it.source == LearnsetSource.TM }
            LearnsetFilter.EGG -> moveEntries.filter { it.source == LearnsetSource.EGG }
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
        val baseEntries = if (sortMode == LearnsetSort.DISCOVERED) {
            entries.filter { it.isDiscovered }
        } else {
            entries
        }
        return when (sortMode) {
            LearnsetSort.ALPHABETICAL -> baseEntries.sortedWith(
                compareBy(
                    { it.move.displayName.string.lowercase() },
                    { it.level ?: Int.MAX_VALUE }
                )
            )
            LearnsetSort.TYPE -> baseEntries.sortedWith(
                compareBy(
                    { it.move.elementalType.displayName.string.lowercase() },
                    { it.move.displayName.string.lowercase() },
                    { it.level ?: Int.MAX_VALUE }
                )
            )
            LearnsetSort.SOURCE,
            LearnsetSort.DISCOVERED -> baseEntries.sortedWith(
                compareBy(
                    { sourceOrder(it.source) },
                    { it.level ?: Int.MAX_VALUE },
                    { it.move.displayName.string.lowercase() }
                )
            )
        }
    }

    private fun cycleFilter(next: Boolean) {
        val total = LearnsetFilter.entries.size
        filterIndex = if (next) {
            (filterIndex + 1) % total
        } else {
            (filterIndex - 1 + total) % total
        }
        applyFilter()
    }

    private fun toggleSort() {
        sortMode = when (sortMode) {
            LearnsetSort.ALPHABETICAL -> LearnsetSort.TYPE
            LearnsetSort.TYPE -> LearnsetSort.SOURCE
            LearnsetSort.SOURCE -> LearnsetSort.DISCOVERED
            LearnsetSort.DISCOVERED -> LearnsetSort.ALPHABETICAL
        }
        sortButton.resource = when (sortMode) {
            LearnsetSort.ALPHABETICAL -> sortAlphaIcon
            LearnsetSort.TYPE -> sortTypeIcon
            LearnsetSort.SOURCE -> sortSourceIcon
            LearnsetSort.DISCOVERED -> sortDiscoveredIcon
        }
        applyFilter()
    }

    private fun sourceOrder(source: LearnsetSource): Int {
        return when (source) {
            LearnsetSource.LEVEL_UP -> 0
            LearnsetSource.TM -> 1
            LearnsetSource.EGG -> 2
        }
    }

    private fun currentFilter(): LearnsetFilter {
        return LearnsetFilter.entries[filterIndex.coerceIn(0, LearnsetFilter.entries.lastIndex)]
    }

    private fun updateFormButtons() {
        val hasForms = availableForms.size > 1
        formLeftButton.isVisible = hasForms
        formRightButton.isVisible = hasForms
        formLeftButton.active = hasForms
        formRightButton.active = hasForms
    }

    private fun updateFormLabel(species: Species, form: PokedexForm) {
        val formName = if (form.displayForm.equals("normal", ignoreCase = true)) {
            ""
        } else {
            "-${form.displayForm.lowercase().replace("-", "")}"
        }
        val label = lang("ui.pokedex.info.form.${species}${formName}")
        formLabel = if (label.string.isBlank() || label.string.startsWith("cobblemon.ui.pokedex.info.form.")) {
            Component.literal(form.displayForm)
        } else {
            label
        }
    }

    private fun selectMove(entry: LearnsetMoveEntry?) {
        selectedEntry = if (selectedEntry == entry || entry == null) null else entry
        listWidget.setSelectedEntry(selectedEntry)

        if (selectedEntry?.isDiscovered == true) {
            val description = selectedEntry!!.move.description.string
            descriptionWidget.showPlaceholder = false
            descriptionWidget.setText(listOf(description))
        } else {
            descriptionWidget.showPlaceholder = true
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
        val evolutionMoveLevels = buildEvolutionMoveLevelIndex(form)
        val learnedTMs = CobblemonClient.clientTMMoveData.learnedTMs
        val unlockAllMoveDexMovesByDefault = ServerSettings.unlockAllMoveDexMovesByDefault

        fun isLevelUpDiscovered(move: MoveTemplate, level: Int): Boolean {
            if (unlockAllMoveDexMovesByDefault) return true
            if (highestLevel >= level) return true

            for ((evolutionSpeciesId, moveLevels) in evolutionMoveLevels) {
                val evolutionLevel = moveLevels[move.name] ?: continue
                val evolutionHighest = pokedexManager.getSpeciesRecord(evolutionSpeciesId)?.highestLevel ?: 0
                if (evolutionHighest >= evolutionLevel) return true
            }

            return false
        }

        fun addEntry(
            move: MoveTemplate,
            source: LearnsetSource,
            level: Int? = null,
            tmLocked: Boolean = false,
            isDiscovered: Boolean = true
        ) {
            val key = move.name
            if (!entries.containsKey(key)) {
                val tmId = TechnicalMachines.moveToTM[move]?.id
                val tmUnlocked = source == LearnsetSource.TM && tmId != null && tmId in learnedTMs
                val resolvedTmLocked = if (source == LearnsetSource.TM) tmLocked else false
                entries[key] = LearnsetMoveEntry(move, source, level, resolvedTmLocked, isDiscovered, tmId, tmUnlocked)
            }
        }

        form.moves.levelUpMoves.toSortedMap().forEach { (level, moves) ->
            moves.forEach { move ->
                val discovered = isLevelUpDiscovered(move, level)
                addEntry(move, LearnsetSource.LEVEL_UP, level = level, isDiscovered = discovered)
            }
        }

        form.moves.tmMoves.sortedBy { it.displayName.string }.forEach { move ->
            val tmLocked = if (unlockAllMoveDexMovesByDefault) false else {
                val tmId = TechnicalMachines.moveToTM[move]?.id
                tmId != null && tmId !in learnedTMs
            }
            addEntry(move, LearnsetSource.TM, tmLocked = tmLocked, isDiscovered = !tmLocked)
        }

        form.moves.eggMoves.sortedBy { it.displayName.string }.forEach { move ->
            addEntry(move, LearnsetSource.EGG)
        }

        return entries.values.toList()
    }

    private enum class LearnsetSource(val label: String) {
        LEVEL_UP("Lv"),
        TM("TM"),
        EGG("Egg")
    }

    private enum class LearnsetFilter(val label: MutableComponent) {
        ALL(Component.literal("All")),
        LEVEL_UP(Component.literal("Level-Up")),
        TM(Component.literal("TM")),
        EGG(Component.literal("Egg"))
    }

    private enum class LearnsetSort {
        ALPHABETICAL,
        TYPE,
        SOURCE,
        DISCOVERED
    }

    private data class LearnsetMoveEntry(
        val move: MoveTemplate,
        val source: LearnsetSource,
        val level: Int? = null,
        val tmLocked: Boolean = false,
        val isDiscovered: Boolean = true,
        val tmId: ResourceLocation? = null,
        val tmUnlocked: Boolean = false
    )

    // todo fix the scrolling area height
    private class LearnsetMovesScrollingWidget(
        val pX: Int,
        val pY: Int,
        listWidth: Int,
        listHeight: Int,
        val onSelect: (LearnsetMoveEntry?) -> Unit
    ) : ScrollingWidget<LearnsetMovesScrollingWidget.MoveEntrySlot>(
        width = listWidth,
        height = listHeight,
        left = pX,
        top = pY,
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
            return left + width - 3
        }

        override fun renderScrollbar(context: GuiGraphics, mouseX: Int, mouseY: Int, delta: Float) {
            val xLeft = this.scrollbarPosition
            val xRight = xLeft + 3
            val yStart = y + 1

            val barHeight = this.bottom - yStart

            var yBottom = ((barHeight * barHeight).toFloat() / this.maxPosition.toFloat()).toInt()
            yBottom = Mth.clamp(yBottom, 32, barHeight - 8)
            var yTop = scrollAmount.toInt() * (barHeight - yBottom) / this.maxScroll + yStart
            if (yTop < yStart) yTop = yStart

            context.fill(xLeft + 1, yStart, xRight - 1, this.bottom, FastColor.ARGB32.color(255, 126, 231, 229)) // track
            context.fill(xLeft, yTop, xRight, yTop + yBottom, FastColor.ARGB32.color(255, 58, 150, 182)) // bar
        }

        override fun getEntry(index: Int): MoveEntrySlot {
            return children()[index] as MoveEntrySlot
        }

        inner class MoveEntrySlot(
            val entry: LearnsetMoveEntry,
            private val parentList: LearnsetMovesScrollingWidget
        ) : Slot<MoveEntrySlot>() {
            private var lastX = 0
            private var lastY = 0

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
                lastX = x
                lastY = y

                val isSelected = entry == parentList.selectedEntry
                if (hovered || isSelected) {
                    val color = if (isSelected) {
                        FastColor.ARGB32.color(140, 58, 150, 182)
                    } else {
                        FastColor.ARGB32.color(90, 126, 231, 229)
                    }
                    context.fill(x, y, x + entryWidth - 3, y + entryHeight, color)
                }

                val leftOffset = LIST_TM_ICON_OFFSET
                if (entry.tmId != null) {
                    val (red, green, blue) = if (entry.tmUnlocked) {
                        Triple(0.32F, 0.82F, 0.46F)
                    } else {
                        Triple(0.6F, 0.6F, 0.6F)
                    }
                    val iconX = x + 1
                    val iconY = y + 1
                    blitk(
                        matrixStack = context.pose(),
                        texture = tmDiscIcon,
                        x = iconX / SCALE,
                        y = iconY / SCALE,
                        width = LIST_TM_ICON_TEXTURE_SIZE,
                        height = LIST_TM_ICON_TEXTURE_SIZE,
                        red = red,
                        green = green,
                        blue = blue,
                        alpha = 1F,
                        scale = SCALE
                    )
                    val hoveringIcon = mouseX in iconX..(iconX + LIST_TM_ICON_RENDER_SIZE) &&
                        mouseY in iconY..(iconY + LIST_TM_ICON_RENDER_SIZE)
                    if (hoveringIcon) {
                        val tooltipKey = if (entry.tmUnlocked) {
                            "ui.moves.learnset.tm.discovered"
                        } else {
                            "egg_group.undiscovered"
                        }
                        val tooltipText = lang(tooltipKey)
                        val textWidth = Minecraft.getInstance().font.width(tooltipText.font(CobblemonResources.DEFAULT_LARGE))
                        val tooltipWidth = textWidth + 6
                        val listLeft = parentList.left + 2
                        val listRight = parentList.left + parentList.width - 6
                        val minCenter = listLeft + (tooltipWidth / 2)
                        val maxCenter = listRight - (tooltipWidth / 2)
                        val tooltipX = if (minCenter <= maxCenter) {
                            Mth.clamp(mouseX, minCenter, maxCenter)
                        } else {
                            mouseX
                        }
                        renderTooltip(context, tooltipText, tooltipX, mouseY, tickDelta, -14)
                    }
                }

                blitk(
                    matrixStack = context.pose(),
                    texture = moveDexTypeIcons,
                    x = (x + 1 + leftOffset) / SCALE,
                    y = (y + 1) / SCALE,
                    width = LIST_TYPE_ICON_SIZE,
                    height = LIST_TYPE_ICON_SIZE,
                    uOffset = LIST_TYPE_ICON_SIZE * entry.move.elementalType.textureXMultiplier.toFloat() + 0.1,
                    textureWidth = LIST_TYPE_ICON_SIZE * 18,
                    textureHeight = LIST_TYPE_ICON_SIZE,
                    scale = SCALE
                )

                val displayName = if (entry.isDiscovered) {
                    entry.move.displayName
                } else {
                    Component.literal("?????")
                }

                drawScaledText(
                    context = context,
                    text = displayName,
                    x = x + 12 + leftOffset,
                    y = y + 2,
                    scale = SCALE,
                    shadow = false,
                    colour = if (entry.isDiscovered) 0x606B6E else 0x8A8F91
                )

                val rightLabel = when {
                    entry.level != null -> "Lv.${entry.level}"
                    entry.source == LearnsetSource.TM -> entry.source.label
                    else -> entry.source.label
                }

                drawScaledTextJustifiedRight(
                    context = context,
                    text = rightLabel.text(),
                    x = x + entryWidth - 6,
                    y = y + 2,
                    scale = SCALE,
                    shadow = false,
                    colour = if (entry.tmLocked || !entry.isDiscovered) 0x8A8F91 else 0x606B6E
                )

            }

            override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
                val canClick = mouseX < (lastX + (parentList.width - 3))
                if (canClick) {
                    onSelect(entry)
                    Minecraft.getInstance().soundManager.play(SimpleSoundInstance.forUI(CobblemonSounds.POKEDEX_CLICK_SHORT, 1.0F))
                }
                return true
            }

            override fun getNarration(): Component {
                return if (entry.isDiscovered) entry.move.displayName else Component.literal("?????")
            }
        }
    }

    private class MoveDescriptionWidget(
        val pX: Int,
        val pY: Int,
        width: Int,
        height: Int
    ) : ScrollingWidget<MoveDescriptionWidget.TextSlot>(
        left = pX,
        top = pY,
        width = width,
        height = height,
        slotHeight = LIST_SLOT_HEIGHT,
        scrollBarWidth = DESCRIPTION_SCROLLBAR_WIDTH
    ) {
        var showPlaceholder = true

        override fun renderWidget(context: GuiGraphics, mouseX: Int, mouseY: Int, delta: Float) {
            if (showPlaceholder) {
                /*drawScaledText(
                    context = context,
                    text = Component.literal("Select a move."),
                    x = pX + (width / 2),
                    y = pY + (height / 2) - 3,
                    shadow = false,
                    colour = 0x606B6E,
                    scale = SCALE,
                    centered = true
                )*/
                return
            }
            super.renderWidget(context, mouseX, mouseY, delta)
        }

        fun setText(text: Collection<String>) {
            clearEntries()
            text.forEach { line ->
                Minecraft.getInstance().font.splitter.splitLines(
                    Component.literal(line),
                    ((width - DESCRIPTION_SCROLLBAR_OFFSET - (POKEMON_DESCRIPTION_PADDING * 2)) / SCALE).toInt(),
                    Style.EMPTY
                ).stream()
                    .map { it.string }
                    .forEach { addEntry(TextSlot(it)) }
            }
            scrollAmount = 0.0
        }

        override fun renderScrollbar(context: GuiGraphics, mouseX: Int, mouseY: Int, delta: Float) {
            val xLeft = this.scrollbarPosition
            val xRight = xLeft + scrollBarWidth
            val yStart = y + 1

            val barHeight = this.bottom - yStart

            var yBottom = ((barHeight * barHeight).toFloat() / this.maxPosition.toFloat()).toInt()
            yBottom = Mth.clamp(yBottom, 16, barHeight - 6)
            var yTop = scrollAmount.toInt() * (barHeight - yBottom) / this.maxScroll + yStart
            if (yTop < yStart) yTop = yStart

            context.fill(xLeft + 1, yStart, xRight - 1, this.bottom, FastColor.ARGB32.color(255, 126, 231, 229)) // track
            context.fill(xLeft, yTop, xRight, yTop + yBottom, FastColor.ARGB32.color(255, 58, 150, 182)) // bar
        }

        override fun getScrollbarPosition(): Int {
            return left + width - DESCRIPTION_SCROLLBAR_OFFSET
        }

        class TextSlot(val text: String) : Slot<TextSlot>() {
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
                drawScaledText(
                    context = context,
                    text = text.text(),
                    x = x + POKEMON_DESCRIPTION_PADDING,
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
