/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.gui.habitat

import com.cobblemon.mod.common.api.habitats.HabitatPhaseOrder
import com.cobblemon.mod.common.api.habitats.HabitatPool
import com.cobblemon.mod.common.api.habitats.dto.ActivatedHabitatSettingsDTO
import com.cobblemon.mod.common.api.habitats.dto.HabitatPoolSettingsDTO
import com.cobblemon.mod.common.api.habitats.dto.HabitatSettingsDTO
import com.cobblemon.mod.common.api.habitats.dto.NaturalHabitatSettingsDTO
import com.cobblemon.mod.common.api.habitats.spawningstyle.ActivatedHabitatSpawning
import com.cobblemon.mod.common.api.tags.CobblemonBlockTags
import com.cobblemon.mod.common.api.text.text
import com.cobblemon.mod.common.client.gui.CobblemonRenderable
import com.cobblemon.mod.common.net.messages.server.habitat.SaveHabitatBlockSettingsPacket
import com.cobblemon.mod.common.util.asTranslated
import com.cobblemon.mod.common.util.cobblemonResource
import com.cobblemon.mod.common.util.lang
import java.awt.Color
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.Screen
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.MutableComponent
import net.minecraft.resources.ResourceLocation

class HabitatEditGUI(
    /** The position of the habitat block in the client player's world */
    val blockPos: BlockPos,
    /** How many Pokémon are alive nearby as a result of this block, fyi */
    val currentlySpawned: Int,
    /** The current phase of the habitat block, fyi */
    val currentPhase: Int,
    /** The highest level possible for a Pokémon according to the server's config */
    val maxPokemonLevel: Int,
    /** Use this as reference data for what spawnable position types exist. */
    val spawnablePositionTypes: List<String>,
    /** Use this as reference data for what buckets can be chosen for natural spawns */
    val worldBuckets: List<String>,
    /** Use this as reference data for what buckets can be chosen for activated spawns */
    val activatedHabitatBuckets: List<String>,
    /** Use this as reference data for pre-made habitat pools */
    val pools: Map<ResourceLocation, HabitatPool>,
    /** The current settings - edit the properties in here then send it back to the server. */
    val habitatSettingsDTO: HabitatSettingsDTO
) : Screen(lang("ui.edit.habitat")), CobblemonRenderable {
    private var scrollOffset = 0.0
    private var minScrollOffset = 0.0
    private val headerHeight = 24
    private val footerHeight = 44
    private val optionsTopPadding = 6
    private val saveButtonHeight = 20
    private val saveButtonBottomMargin = 10
    private val optionsBottomPadding = 6
    private val validTextColor = 0xFFFFFF
    private val invalidTextColor = 0xFF5555
    private lateinit var mimicInput: EditBox
    private lateinit var phaseOrderButton: Button
    private lateinit var spawnModeButton: Button
    private lateinit var poolSelectorButton: Button
    private lateinit var viewPoolButton: Button
    private lateinit var levelMinInput: EditBox
    private lateinit var levelMaxInput: EditBox
    private lateinit var saveButton: Button
    private val optionWidgets = mutableListOf<AbstractWidget>()
    private val footerWidgets = mutableListOf<AbstractWidget>()
    private val validationErrors = linkedMapOf<String, String>()
    private var validationMessageY = 0
    private val labels = mutableListOf<Pair<MutableComponent, Pair<Int, Int>>>()

    override fun init() {
        super.init()
        validationErrors.clear()
        optionWidgets.clear()
        footerWidgets.clear()
        scrollOffset = scrollOffset.coerceIn(minScrollOffset, 0.0)
        labels.clear()
        var y = getOptionsTop() + scrollOffset.toInt()
        val spacing = 25
        val buttonWidth = getScaledWidth() - 20

        // Common Settings
        labels.add(lang("ui.edit.habitat.mimic_id") to (10 to y))
        y += 12
        mimicInput = EditBox(font, 10, y, buttonWidth, 20, "Mimic Block".text())
        mimicInput.setMaxLength(80)
        mimicInput.value = habitatSettingsDTO.mimicId.toString()
        mimicInput.setResponder { value ->
            val id = ResourceLocation.tryParse(value)
            val valid = id != null &&
                BuiltInRegistries.BLOCK.containsKey(id) &&
                BuiltInRegistries.BLOCK.get(id).builtInRegistryHolder().`is`(CobblemonBlockTags.HABITAT_MIMICS)
            if (valid) {
                habitatSettingsDTO.mimicId = id
            }
            mimicInput.setTextColor(if (valid) validTextColor else invalidTextColor)
            setFieldValidity("mimic", valid, "ui.edit.habitat.validation.mimic")
        }
        val initialMimicId = ResourceLocation.tryParse(mimicInput.value)
        val initialMimicValid = initialMimicId != null &&
            BuiltInRegistries.BLOCK.containsKey(initialMimicId) &&
            BuiltInRegistries.BLOCK.get(initialMimicId).builtInRegistryHolder().`is`(CobblemonBlockTags.HABITAT_MIMICS)
        mimicInput.setTextColor(if (initialMimicValid) validTextColor else invalidTextColor)
        setFieldValidity("mimic", initialMimicValid, "ui.edit.habitat.validation.mimic")
        addOptionWidget(mimicInput)
        y += spacing

        labels.add(lang("ui.edit.habitat.phase_number") to (10 to y))
        y += 12
        val phasesInput = EditBox(font, 10, y, buttonWidth, 20, "Phases".text())
        phasesInput.value = habitatSettingsDTO.numberOfPhases.toString()
        phasesInput.setResponder { value ->
            val phases = value.toIntOrNull()
            val valid = phases != null && phases > 0
            if (valid) {
                habitatSettingsDTO.numberOfPhases = phases
            }
            phasesInput.setTextColor(if (valid) validTextColor else invalidTextColor)
            setFieldValidity("phases", valid, "ui.edit.habitat.validation.phases")
        }
        val initialPhases = phasesInput.value.toIntOrNull()
        val initialPhasesValid = initialPhases != null && initialPhases > 0
        phasesInput.setTextColor(if (initialPhasesValid) validTextColor else invalidTextColor)
        setFieldValidity("phases", initialPhasesValid, "ui.edit.habitat.validation.phases")
        addOptionWidget(phasesInput)
        y += spacing

        phaseOrderButton = addOptionWidget(
            Button.builder(getPhaseOrderText()) {
                val orders = HabitatPhaseOrder.entries
                val currentIndex = orders.indexOf(habitatSettingsDTO.phaseOrder)
                habitatSettingsDTO.phaseOrder = orders[(currentIndex + 1) % orders.size]
                refresh()
            }.bounds(10, y, buttonWidth, 20).build()
        )
        y += spacing

        labels.add(lang("ui.edit.habitat.level_range") to (10 to y))
        y += 12
        levelMinInput = EditBox(font, 10, y, buttonWidth / 2 - 5, 20, "Min Level".text())
        levelMinInput.value = habitatSettingsDTO.levelRange.first.toString()
        levelMinInput.setResponder { value ->
            validateLevelRangeInputs()
        }
        addOptionWidget(levelMinInput)

        levelMaxInput = EditBox(font, 10 + buttonWidth / 2 + 5, y, buttonWidth / 2 - 5, 20, "Max Level".text())
        levelMaxInput.value = habitatSettingsDTO.levelRange.last.toString()
        levelMaxInput.setResponder { value ->
            validateLevelRangeInputs()
        }
        addOptionWidget(levelMaxInput)
        validateLevelRangeInputs()
        y += spacing

        // Spawning Mode Toggle
        spawnModeButton = addOptionWidget(
            Button.builder(getSpawnModeText()) {
                habitatSettingsDTO.isActivatedSpawning = !habitatSettingsDTO.isActivatedSpawning
                if (habitatSettingsDTO.isActivatedSpawning && habitatSettingsDTO.activatedSettings == null) {
                    habitatSettingsDTO.activatedSettings = ActivatedHabitatSettingsDTO()
                }
                if (!habitatSettingsDTO.isActivatedSpawning && habitatSettingsDTO.naturalSettings == null) {
                    habitatSettingsDTO.naturalSettings = NaturalHabitatSettingsDTO()
                }
                rebuildWidgets()
            }.bounds(10, y, buttonWidth, 20).build()
        )
        y += spacing

        // Mode-Specific Settings
        if (habitatSettingsDTO.isActivatedSpawning) {
            val activated = habitatSettingsDTO.activatedSettings!!
            
            addOptionWidget(
                Button.builder(getTriggerText()) {
                    val triggers = ActivatedHabitatSpawning.Trigger.entries
                    val currentIndex = triggers.indexOf(activated.trigger)
                    activated.trigger = triggers[(currentIndex + 1) % triggers.size]
                    it.message = getTriggerText()
                    refresh()
                }.bounds(10, y, buttonWidth, 20).build()
            )
            y += spacing

            labels.add(lang("ui.edit.habitat.spawn_range") to (10 to y))
            y += 12
            val spawnRangeInput = EditBox(font, 10, y, buttonWidth, 20, "Spawn Range".text())
            spawnRangeInput.value = activated.spawnRange.toString()
            spawnRangeInput.setResponder { value ->
                val parsed = value.toIntOrNull()
                val valid = parsed != null && parsed >= 0
                if (valid) {
                    activated.spawnRange = parsed!!
                }
                spawnRangeInput.setTextColor(if (valid) validTextColor else invalidTextColor)
                setFieldValidity("spawn_range", valid, "ui.edit.habitat.validation.spawn_range")
            }
            val initialSpawnRange = spawnRangeInput.value.toIntOrNull()
            val initialSpawnRangeValid = initialSpawnRange != null && initialSpawnRange >= 0
            spawnRangeInput.setTextColor(if (initialSpawnRangeValid) validTextColor else invalidTextColor)
            setFieldValidity("spawn_range", initialSpawnRangeValid, "ui.edit.habitat.validation.spawn_range")
            addOptionWidget(spawnRangeInput)
            y += spacing

            labels.add(lang("ui.edit.habitat.cancelled_range") to (10 to y))
            y += 12
            val cancelledRangeInput = EditBox(font, 10, y, buttonWidth, 20, "Cancelled Range".text())
            cancelledRangeInput.value = activated.cancelledNaturalSpawningRange.toString()
            cancelledRangeInput.setResponder { value ->
                val parsed = value.toIntOrNull()
                val valid = parsed != null && (parsed == -1 || parsed in 0..64)
                if (valid) {
                    activated.cancelledNaturalSpawningRange = parsed!!
                }
                cancelledRangeInput.setTextColor(if (valid) validTextColor else invalidTextColor)
                setFieldValidity("cancelled_range", valid, "ui.edit.habitat.validation.cancelled_range")
            }
            val initialCancelledRange = cancelledRangeInput.value.toIntOrNull()
            val initialCancelledRangeValid = initialCancelledRange != null && (initialCancelledRange == -1 || initialCancelledRange in 0..64)
            cancelledRangeInput.setTextColor(if (initialCancelledRangeValid) validTextColor else invalidTextColor)
            setFieldValidity("cancelled_range", initialCancelledRangeValid, "ui.edit.habitat.validation.cancelled_range")
            addOptionWidget(cancelledRangeInput)
            y += spacing

            labels.add(lang("ui.edit.habitat.max_spawns") to (10 to y))
            y += 12
            val maxSpawnsInput = EditBox(font, 10, y, buttonWidth, 20, "Max Spawns".text())
            maxSpawnsInput.value = activated.maxSpawns.toString()
            maxSpawnsInput.setResponder { value ->
                val parsed = value.toIntOrNull()
                val valid = parsed != null && parsed >= -1
                if (valid) {
                    activated.maxSpawns = parsed!!
                }
                maxSpawnsInput.setTextColor(if (valid) validTextColor else invalidTextColor)
                setFieldValidity("max_spawns", valid, "ui.edit.habitat.validation.max_spawns")
            }
            val initialMaxSpawns = maxSpawnsInput.value.toIntOrNull()
            val initialMaxSpawnsValid = initialMaxSpawns != null && initialMaxSpawns >= -1
            maxSpawnsInput.setTextColor(if (initialMaxSpawnsValid) validTextColor else invalidTextColor)
            setFieldValidity("max_spawns", initialMaxSpawnsValid, "ui.edit.habitat.validation.max_spawns")
            addOptionWidget(maxSpawnsInput)
            y += spacing
        } else {
            val natural = habitatSettingsDTO.naturalSettings!!
            
            addOptionWidget(
                Button.builder(getReplaceSpawnsText()) {
                    natural.replaceSpawns = !natural.replaceSpawns
                    it.message = getReplaceSpawnsText()
                    refresh()
                }.bounds(10, y, buttonWidth, 20).build()
            )
            y += spacing

            labels.add(lang("ui.edit.habitat.influence_range") to (10 to y))
            y += 12
            val rangeInput = EditBox(font, 10, y, buttonWidth, 20, "Range of Influence".text())
            rangeInput.value = natural.rangeOfInfluence.toString()
            rangeInput.setResponder { value ->
                val parsed = value.toIntOrNull()
                val valid = parsed != null && parsed >= 0
                if (valid) {
                    natural.rangeOfInfluence = parsed!!
                }
                rangeInput.setTextColor(if (valid) validTextColor else invalidTextColor)
                setFieldValidity("influence_range", valid, "ui.edit.habitat.validation.influence_range")
            }
            val initialInfluenceRange = rangeInput.value.toIntOrNull()
            val initialInfluenceRangeValid = initialInfluenceRange != null && initialInfluenceRange >= 0
            rangeInput.setTextColor(if (initialInfluenceRangeValid) validTextColor else invalidTextColor)
            setFieldValidity("influence_range", initialInfluenceRangeValid, "ui.edit.habitat.validation.influence_range")
            addOptionWidget(rangeInput)
            y += spacing
        }

        // Pool Selection
        poolSelectorButton = addOptionWidget(
            Button.builder(getPoolText()) {
                val currentPool = habitatSettingsDTO.pool
                val selectedPoolId = if (currentPool?.isReference == true) currentPool.id else null
                Minecraft.getInstance().setScreen(
                    HabitatPoolSelectionGUI(
                        isActivated = habitatSettingsDTO.isActivatedSpawning,
                        pools = pools,
                        selectedPoolId = selectedPoolId,
                        parentScreen = this
                    ) { poolId ->
                        applyPoolSelection(poolId)
                    }
                )
            }.bounds(10, y, buttonWidth, 20).build()
        )
        y += spacing

        viewPoolButton = addOptionWidget(
            Button.builder(getViewPoolText()) {
                val pool = habitatSettingsDTO.pool
                val buckets = if (habitatSettingsDTO.isActivatedSpawning) activatedHabitatBuckets else worldBuckets

                if (!pool.isReference) {
                    Minecraft.getInstance().setScreen(
                        HabitatPoolEditorGUI(pool, habitatSettingsDTO.isActivatedSpawning, buckets, spawnablePositionTypes, maxPokemonLevel, this, editable = true)
                    )
                    return@builder
                }

                val sourcePool = pools[pool.id] ?: return@builder

                val viewPool = HabitatPoolSettingsDTO(sourcePool).apply {
                    isReference = true
                    id = sourcePool.id
                    name = sourcePool.name
                    spawns = sourcePool.spawns.toMutableList()
                }

                Minecraft.getInstance().setScreen(
                    HabitatPoolEditorGUI(viewPool, habitatSettingsDTO.isActivatedSpawning, buckets, spawnablePositionTypes, maxPokemonLevel, this, editable = false)
                )
            }.bounds(10, y, buttonWidth, 20).build()
        )
        y += spacing

        val contentBottomAtZeroOffset = y - scrollOffset.toInt()
        updateScrollBounds(contentBottomAtZeroOffset)
        if (scrollOffset < minScrollOffset) {
            scrollOffset = minScrollOffset
            rebuildWidgets()
            return
        }

        // Save Button (fixed at bottom)
        saveButton = addFooterWidget(
            Button.builder(lang("ui.generic.save")) { _ -> save() }
                .bounds(10, getScaledHeight() - (saveButtonHeight + saveButtonBottomMargin), buttonWidth, saveButtonHeight)
                .build()
        )
        validationMessageY = getFooterTop() + 6
        updateSaveButtonState()
        updateOptionWidgetInteractivity()
        
        refresh()
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
        val optionsBottom = getOptionsBottom()
        labels.forEach { (label, pos) ->
            if (pos.second >= getOptionsTop() && pos.second + font.lineHeight <= optionsBottom) {
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
        phaseOrderButton.message = getPhaseOrderText()
        spawnModeButton.message = getSpawnModeText()
        poolSelectorButton.message = getPoolText()
        viewPoolButton.message = getViewPoolText()
    }

    private fun getPhaseOrderText() = lang("ui.edit.habitat.phase_order", habitatSettingsDTO.phaseOrder)
    private fun getSpawnModeText() = lang(
        "ui.edit.habitat.mode",
        lang(
            if (habitatSettingsDTO.isActivatedSpawning)
                "ui.edit.habitat.mode.activated"
            else
                "ui.edit.habitat.mode.natural"
        )
    )
    private fun getTriggerText() = lang("ui.edit.habitat.trigger", habitatSettingsDTO.activatedSettings?.trigger ?: "")
    private fun getReplaceSpawnsText() = lang("ui.edit.habitat.replace_spawns", habitatSettingsDTO.naturalSettings?.replaceSpawns ?: false)
    
    private fun getPoolText(): net.minecraft.network.chat.Component {
        val pool = habitatSettingsDTO.pool
        if (pool == null || !pool.isReference) {
            return lang("ui.edit.habitat.pool", lang("ui.edit.habitat.pool.custom"))
        }
        val poolName = pools[pool.id]?.name?.asTranslated()
        return lang("ui.edit.habitat.pool", poolName ?: pool.id)
    }

    private fun getViewPoolText(): net.minecraft.network.chat.Component {
        val pool = habitatSettingsDTO.pool
        return if (pool.isReference) {
            lang("ui.edit.habitat.view_pool")
        } else {
            lang("ui.edit.habitat.edit_pool")
        }
    }

    private fun applyPoolSelection(poolId: ResourceLocation?) {
        val currentPool = habitatSettingsDTO.pool
        if (poolId == null) {
            if (currentPool.isReference) {
                val newCustomPool = HabitatPoolSettingsDTO().apply {
                    id = cobblemonResource("custom__${System.currentTimeMillis()}")
                    isReference = false
                    name = "Custom"
                    spawns = mutableListOf()
                }
                habitatSettingsDTO.pool = newCustomPool
            }
            return
        }

        val sourcePool = pools[poolId] ?: return

        val selectedPool = HabitatPoolSettingsDTO(sourcePool).apply {
            isReference = true
            id = poolId
            name = sourcePool.name
        }

        habitatSettingsDTO.pool = selectedPool
    }

    private fun validateLevelRangeInputs() {
        val min = levelMinInput.value.toIntOrNull()
        val max = levelMaxInput.value.toIntOrNull()
        val minInRange = min != null && min in 1..maxPokemonLevel
        val maxInRange = max != null && max in 1..maxPokemonLevel
        val ordered = min != null && max != null && min <= max
        val bothValid = minInRange && maxInRange && ordered

        val orderInvalid = minInRange && maxInRange && !ordered
        levelMinInput.setTextColor(if (minInRange && !orderInvalid) validTextColor else invalidTextColor)
        levelMaxInput.setTextColor(if (maxInRange && !orderInvalid) validTextColor else invalidTextColor)

        if (bothValid) {
            habitatSettingsDTO.levelRange = min!!..max!!
        }
        setFieldValidity("level_range", bothValid, "ui.edit.habitat.validation.level_range")
    }

    private fun setFieldValidity(field: String, valid: Boolean, errorKey: String) {
        if (valid) {
            validationErrors.remove(field)
        } else {
            validationErrors[field] = errorKey
        }
        updateSaveButtonState()
    }

    private fun updateSaveButtonState() {
        if (::saveButton.isInitialized) {
            saveButton.active = validationErrors.isEmpty()
        }
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

    fun save() {
        SaveHabitatBlockSettingsPacket(
            blockPos = blockPos,
            settings = habitatSettingsDTO
        ).sendToServer()
        Minecraft.getInstance().setScreen(null)
    }

    private fun updateScrollBounds(contentBottomAtZeroOffset: Int) {
        val optionsBottom = getOptionsBottom()
        minScrollOffset = minOf(0.0, (optionsBottom - contentBottomAtZeroOffset).toDouble())
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

    fun getScaledWidth() = Minecraft.getInstance().window.guiScaledWidth

    fun getScaledHeight() = Minecraft.getInstance().window.guiScaledHeight
}
