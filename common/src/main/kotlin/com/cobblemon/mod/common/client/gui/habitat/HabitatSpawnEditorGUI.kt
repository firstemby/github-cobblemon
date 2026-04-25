/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.gui.habitat

import com.cobblemon.mod.common.api.habitats.HabitatSpawn
import com.cobblemon.mod.common.api.habitats.NaturalHabitatSpawn
import com.cobblemon.mod.common.api.pokemon.PokemonProperties
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import com.cobblemon.mod.common.api.spawning.IntRanges
import com.cobblemon.mod.common.api.spawning.SpawnBucket
import com.cobblemon.mod.common.api.spawning.TimeRange
import com.cobblemon.mod.common.api.text.text
import com.cobblemon.mod.common.client.gui.CobblemonRenderable
import com.cobblemon.mod.common.pokemon.Species
import com.cobblemon.mod.common.util.adapters.IntRangesAdapter
import com.cobblemon.mod.common.util.lang
import java.awt.Color
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.resources.language.I18n

class HabitatSpawnEditorGUI(
    val spawn: HabitatSpawn,
    val isActivated: Boolean,
    val maxPokemonLevel: Int,
    val buckets: List<SpawnBucket>,
    val spawnablePositionTypes: List<String>,
    val parentScreen: Screen
) : Screen(lang("ui.edit.habitat.spawn")), CobblemonRenderable {
    private var scrollOffset = 0.0
    private var minScrollOffset = 0.0
    private val headerHeight = 24
    private val footerHeight = 44
    private val optionsTopPadding = 6
    private val optionsBottomPadding = 6
    private val actionButtonHeight = 20
    private val actionButtonBottomMargin = 10
    private val validTextColor = 0xFFFFFF
    private val invalidTextColor = 0xFF5555
    private val moonPhaseRange = 0..7
    private lateinit var positionTypeButton: Button
    private lateinit var bucketButton: Button
    private lateinit var timeOfDayButton: Button
    private var timeOfDayIndex = 0
    private val timeOfDayOptions: List<Pair<String, TimeRange?>> by lazy {
        val options = mutableListOf<Pair<String, TimeRange?>>("Any" to null)
        options.addAll(
            TimeRange.timeRanges.entries
                .filter { it.key != "any" }
                .map { (key, value) -> key.replaceFirstChar(Char::uppercaseChar) to TimeRange(*value.ranges.toTypedArray()) }
        )
        options
    }
    private val optionWidgets = mutableListOf<AbstractWidget>()
    private val footerWidgets = mutableListOf<AbstractWidget>()
    private val validationErrors = linkedMapOf<String, String>()
    private var validationMessageY = 0
    private val labels = mutableListOf<Pair<String, Pair<Int, Int>>>()

    val pokemonSpeciesByTranslatedName: Map<String, Species> by lazy {
        PokemonSpecies.implemented.associateBy { I18n.get(it.translationKey)?.lowercase() ?: it.translationKey }
    }

    override fun init() {
        super.init()
        optionWidgets.clear()
        footerWidgets.clear()
        validationErrors.clear()
        scrollOffset = scrollOffset.coerceIn(minScrollOffset, 0.0)
        labels.clear()
        var y = getOptionsTop() + scrollOffset.toInt()
        val spacing = 25
        val buttonWidth = getScaledWidth() - 20

        labels.add("Species Name" to (10 to y))
        y += 12
        val speciesInput = EditBox(font, 10, y, buttonWidth, 20, "Species".text())
        speciesInput.value = spawn.species.name
        speciesInput.setResponder { value ->
            val species = parseSpecies(value)
            val valid = species != null
            if (valid) {
                spawn.species = species!!
            }
            speciesInput.setTextColor(if (valid) validTextColor else invalidTextColor)
            setFieldValidity("species", valid, "ui.edit.habitat.validation.species")
        }
        speciesInput.setTextColor(validTextColor)
        setFieldValidity("species", true, "ui.edit.habitat.validation.species")
        addOptionWidget(speciesInput)
        y += spacing

        positionTypeButton = addOptionWidget(
            Button.builder("Position Type: ${spawn.spawnablePositionType}".text()) {
                val currentIndex = spawnablePositionTypes.indexOf(spawn.spawnablePositionType)
                spawn.spawnablePositionType = spawnablePositionTypes[(currentIndex + 1) % spawnablePositionTypes.size]
                refresh()
            }.bounds(10, y, buttonWidth, 20).build()
        )
        y += spacing

        if (spawn is NaturalHabitatSpawn) {
            bucketButton = addOptionWidget(
                Button.builder("Bucket: ${spawn.bucket.name}".text()) {
                    val currentIndex = buckets.indexOf(spawn.bucket)
                    spawn.bucket = buckets[(currentIndex + 1) % buckets.size]
                    refresh()
                }.bounds(10, y, buttonWidth, 20).build()
            )
            y += spacing
        }

        labels.add("Weight" to (10 to y))
        y += 12
        val weightInput = EditBox(font, 10, y, buttonWidth, 20, "Weight".text())
        weightInput.value = spawn.weight.toString()
        weightInput.setResponder { value ->
            val parsed = value.toFloatOrNull()
            val valid = parsed != null && parsed > 0
            if (valid) {
                spawn.weight = parsed!!
            }
            weightInput.setTextColor(if (valid) validTextColor else invalidTextColor)
            setFieldValidity("weight", valid, "ui.edit.habitat.validation.weight")
        }
        weightInput.setTextColor(validTextColor)
        setFieldValidity("weight", true, "ui.edit.habitat.validation.weight")
        addOptionWidget(weightInput)
        y += spacing

        labels.add("Level Range (Min - Max)" to (10 to y))
        y += 12
        val levelMinInput = EditBox(font, 10, y, buttonWidth / 2 - 5, 20, "Min Level".text())
        levelMinInput.value = spawn.levelRange.first.toString()
        val levelMaxInput = EditBox(font, 10 + buttonWidth / 2 + 5, y, buttonWidth / 2 - 5, 20, "Max Level".text())
        levelMaxInput.value = (if (spawn.levelRange.last == Int.MAX_VALUE) maxPokemonLevel else spawn.levelRange.last).toString()
        levelMinInput.setResponder { _ ->
            validateSpawnLevelRange(levelMinInput, levelMaxInput)
        }
        levelMaxInput.setResponder { _ ->
            validateSpawnLevelRange(levelMinInput, levelMaxInput)
        }
        addOptionWidget(levelMinInput)
        addOptionWidget(levelMaxInput)
        validateSpawnLevelRange(levelMinInput, levelMaxInput)
        y += spacing

        labels.add("Modifiers (e.g., shiny gender=male)" to (10 to y))
        y += 12
        val modifiersInput = EditBox(font, 10, y, buttonWidth, 20, "Modifiers".text())
        modifiersInput.value = spawn.modifiers?.asString() ?: ""
        modifiersInput.setResponder { value ->
            if (value.isBlank()) {
                spawn.modifiers = null
                modifiersInput.setTextColor(validTextColor)
                setFieldValidity("modifiers", true, "ui.edit.habitat.validation.modifiers")
            } else {
                val parsed = runCatching { PokemonProperties.parse(value) }.getOrNull()
                val valid = parsed != null
                if (valid) {
                    spawn.modifiers = parsed
                }
                modifiersInput.setTextColor(if (valid) validTextColor else invalidTextColor)
                setFieldValidity("modifiers", valid, "ui.edit.habitat.validation.modifiers")
            }
        }
        modifiersInput.setTextColor(validTextColor)
        setFieldValidity("modifiers", true, "ui.edit.habitat.validation.modifiers")
        addOptionWidget(modifiersInput)
        y += spacing

        labels.add("Phases (e.g., 1-3, 5)" to (10 to y))
        y += 12
        val phasesInput = EditBox(font, 10, y, buttonWidth, 20, "Phases".text())
        phasesInput.value = spawn.phases?.let { IntRangesAdapter.basic.serialize(it) } ?: ""
        phasesInput.setResponder { value ->
            if (value.isBlank()) {
                spawn.phases = null
                phasesInput.setTextColor(validTextColor)
                setFieldValidity("phases_list", true, "ui.edit.habitat.validation.phases_list")
            } else {
                val parsed = parseMoonPhases(value)
                val valid = parsed != null
                if (valid) {
                    spawn.phases = parsed
                }
                phasesInput.setTextColor(if (valid) validTextColor else invalidTextColor)
                setFieldValidity("phases_list", valid, "ui.edit.habitat.validation.phases_list")
            }
        }
        val initialPhasesValid = phasesInput.value.isBlank() || parseMoonPhases(phasesInput.value) != null
        phasesInput.setTextColor(if (initialPhasesValid) validTextColor else invalidTextColor)
        setFieldValidity("phases_list", initialPhasesValid, "ui.edit.habitat.validation.phases_list")
        addOptionWidget(phasesInput)
        y += spacing

        labels.add("Time of Day" to (10 to y))
        y += 12
        timeOfDayIndex = getInitialTimeOfDayIndex()
        timeOfDayButton = addOptionWidget(
            Button.builder(getTimeOfDayText()) {
                timeOfDayIndex = (timeOfDayIndex + 1) % timeOfDayOptions.size
                applyTimeOfDaySelection()
                it.message = getTimeOfDayText()
            }.bounds(10, y, buttonWidth, 20).build()
        )
        applyTimeOfDaySelection()
        y += spacing

        labels.add("Min Light (0-15, optional)" to (10 to y))
        y += 12
        val minLightInput = EditBox(font, 10, y, buttonWidth, 20, "Min Light".text())
        minLightInput.value = spawn.minLight?.toString() ?: ""
        minLightInput.setResponder { value ->
            val parsed = value.toIntOrNull()
            val valid = value.isBlank() || (parsed != null && parsed in 0..15)
            if (valid) {
                spawn.minLight = parsed
            }
            minLightInput.setTextColor(if (valid) validTextColor else invalidTextColor)
            setFieldValidity("min_light", valid, "ui.edit.habitat.validation.min_light")
        }
        minLightInput.setTextColor(validTextColor)
        setFieldValidity("min_light", true, "ui.edit.habitat.validation.min_light")
        addOptionWidget(minLightInput)
        y += spacing

        labels.add("Max Light (0-15, optional)" to (10 to y))
        y += 12
        val maxLightInput = EditBox(font, 10, y, buttonWidth, 20, "Max Light".text())
        maxLightInput.value = spawn.maxLight?.toString() ?: ""
        maxLightInput.setResponder { value ->
            val parsed = value.toIntOrNull()
            val valid = value.isBlank() || (parsed != null && parsed in 0..15)
            if (valid) {
                spawn.maxLight = parsed
            }
            maxLightInput.setTextColor(if (valid) validTextColor else invalidTextColor)
            setFieldValidity("max_light", valid, "ui.edit.habitat.validation.max_light")
        }
        maxLightInput.setTextColor(validTextColor)
        setFieldValidity("max_light", true, "ui.edit.habitat.validation.max_light")
        addOptionWidget(maxLightInput)
        y += spacing

        val contentBottomAtZeroOffset = y - scrollOffset.toInt()
        updateScrollBounds(contentBottomAtZeroOffset)
        if (scrollOffset < minScrollOffset) {
            scrollOffset = minScrollOffset
            rebuildWidgets()
            return
        }

        addFooterWidget(
            Button.builder("Back".text()) {
                Minecraft.getInstance().setScreen(parentScreen)
            }.bounds(10, getScaledHeight() - (actionButtonHeight + actionButtonBottomMargin), buttonWidth, actionButtonHeight).build()
        )
        validationMessageY = getFooterTop() + 6
        updateOptionWidgetInteractivity()
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, scrollX: Double, scrollY: Double): Boolean {
        if (mouseY < getOptionsTop() || mouseY > getOptionsBottom()) {
            return false
        }
        scrollOffset = (scrollOffset + scrollY * 10).coerceIn(minScrollOffset, 0.0)
        rebuildWidgets()
        return true
    }

    override fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        guiGraphics.fill(0, 0, width, height, Color(0, 0, 0, 100).rgb)
        guiGraphics.fill(0, 0, width, headerHeight, Color(0, 0, 0, 150).rgb)
        guiGraphics.fill(0, getFooterTop(), width, height, Color(0, 0, 0, 150).rgb)
        guiGraphics.drawCenteredString(font, title, width / 2, 8, 0xFFFFFF)

        guiGraphics.enableScissor(0, getOptionsTop(), width, getOptionsBottom())
        optionWidgets.forEach { widget ->
            widget.render(guiGraphics, mouseX, mouseY, partialTick)
        }

        labels.forEach { (label, pos) ->
            if (pos.second >= getOptionsTop() && pos.second + font.lineHeight <= getOptionsBottom()) {
                guiGraphics.drawString(font, label, pos.first, pos.second, 0xFFFFFF)
            }
        }
        guiGraphics.disableScissor()

        footerWidgets.forEach { widget ->
            widget.render(guiGraphics, mouseX, mouseY, partialTick)
        }

        validationErrors.values.firstOrNull()?.let { errorKey ->
            guiGraphics.drawString(font, lang(errorKey), 10, validationMessageY, invalidTextColor)
        }
    }

    fun refresh() {
        positionTypeButton.message = "Position Type: ${spawn.spawnablePositionType}".text()
        if (spawn is NaturalHabitatSpawn) {
            bucketButton.message = "Bucket: ${spawn.bucket.name}".text()
        }
    }

    // data validation for error handling
    private fun validateSpawnLevelRange(levelMinInput: EditBox, levelMaxInput: EditBox) {
        val min = levelMinInput.value.toIntOrNull()
        val max = levelMaxInput.value.toIntOrNull()
        val minInRange = min != null && min in 1..maxPokemonLevel
        val maxInRange = max != null && max in 1..maxPokemonLevel
        val ordered = min != null && max != null && min <= max
        val valid = minInRange && maxInRange && ordered

        val orderInvalid = minInRange && maxInRange && !ordered
        levelMinInput.setTextColor(if (minInRange && !orderInvalid) validTextColor else invalidTextColor)
        levelMaxInput.setTextColor(if (maxInRange && !orderInvalid) validTextColor else invalidTextColor)

        if (valid) {
            spawn.levelRange = min!!..(if (max == maxPokemonLevel) Int.MAX_VALUE else max!!)
        }
        setFieldValidity("level_range", valid, "ui.edit.habitat.validation.level_range")
    }

    private fun parseSpecies(value: String): Species? {
        val lowered = value.lowercase()
        pokemonSpeciesByTranslatedName[lowered]?.let { return it }
        return runCatching { PokemonSpecies.getByName(lowered) }.getOrNull()
    }

    private fun parseMoonPhases(raw: String): IntRanges? {
        val ranges = mutableListOf<IntRange>()
        val tokens = raw.split(",").map(String::trim)
        if (tokens.isEmpty() || tokens.any { it.isEmpty() }) {
            return null
        }

        for (token in tokens) {
            val parts = token.split("-").map(String::trim)
            val range = when (parts.size) {
                1 -> {
                    val value = parts[0].toIntOrNull() ?: return null
                    if (value !in moonPhaseRange) return null
                    value..value
                }
                2 -> {
                    val start = parts[0].toIntOrNull() ?: return null
                    val end = parts[1].toIntOrNull() ?: return null
                    if (start !in moonPhaseRange || end !in moonPhaseRange || start > end) {
                        return null
                    }
                    start..end
                }
                else -> return null
            }
            ranges.add(range)
        }

        return IntRanges(*ranges.toTypedArray())
    }

    private fun getInitialTimeOfDayIndex(): Int {
        val current = spawn.timeRange ?: return 0
        val serialized = TimeRange.adapter.serialize(current)
        val matched = timeOfDayOptions.indexOfFirst { (_, value) ->
            value != null && TimeRange.adapter.serialize(value) == serialized
        }
        return if (matched == -1) 0 else matched
    }

    private fun applyTimeOfDaySelection() {
        spawn.timeRange = timeOfDayOptions[timeOfDayIndex].second?.let { TimeRange(*it.ranges.toTypedArray()) }
    }

    private fun getTimeOfDayText() = "Time of Day: ${timeOfDayOptions[timeOfDayIndex].first}".text()

    private fun setFieldValidity(field: String, valid: Boolean, errorKey: String) {
        if (valid) {
            validationErrors.remove(field)
        } else {
            validationErrors[field] = errorKey
        }
    }

    private fun updateScrollBounds(contentBottomAtZeroOffset: Int) {
        minScrollOffset = minOf(0.0, (getOptionsBottom() - contentBottomAtZeroOffset).toDouble())
    }

    private fun getOptionsTop(): Int {
        return headerHeight + optionsTopPadding
    }

    private fun getFooterTop(): Int {
        return getScaledHeight() - footerHeight
    }

    private fun getOptionsBottom(): Int {
        return getFooterTop() - optionsBottomPadding
    }

    private fun <T : AbstractWidget> addOptionWidget(widget: T): T {
        optionWidgets.add(widget)
        return addRenderableWidget(widget)
    }

    private fun <T : AbstractWidget> addFooterWidget(widget: T): T {
        footerWidgets.add(widget)
        return addRenderableWidget(widget)
    }

    private fun updateOptionWidgetInteractivity() {
        val optionsTop = getOptionsTop()
        val optionsBottom = getOptionsBottom()
        optionWidgets.forEach { widget ->
            val widgetTop = widget.y
            val widgetBottom = widget.y + widget.height
            val inViewport = widgetBottom > optionsTop && widgetTop < optionsBottom
            widget.visible = inViewport
            widget.active = inViewport
            if (!inViewport) {
                widget.setFocused(false)
            }
        }
    }

    fun getScaledWidth() = Minecraft.getInstance().window.guiScaledWidth
    fun getScaledHeight() = Minecraft.getInstance().window.guiScaledHeight
}
