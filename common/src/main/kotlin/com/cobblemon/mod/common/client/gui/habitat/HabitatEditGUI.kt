/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.gui.habitat

import com.cobblemon.mod.common.api.habitats.ActivatedHabitatPool
import com.cobblemon.mod.common.api.habitats.HabitatPhaseOrder
import com.cobblemon.mod.common.api.habitats.NaturalHabitatPool
import com.cobblemon.mod.common.api.habitats.dto.ActivatedHabitatSettingsDTO
import com.cobblemon.mod.common.api.habitats.dto.HabitatPoolSettingsDTO
import com.cobblemon.mod.common.api.habitats.dto.HabitatSettingsDTO
import com.cobblemon.mod.common.api.habitats.dto.NaturalHabitatSettingsDTO
import com.cobblemon.mod.common.api.habitats.spawningstyle.ActivatedHabitatSpawning
import com.cobblemon.mod.common.api.spawning.SpawnBucket
import com.cobblemon.mod.common.api.text.text
import com.cobblemon.mod.common.block.habitat.HabitatBlockEntity
import com.cobblemon.mod.common.client.gui.CobblemonRenderable
import com.cobblemon.mod.common.net.messages.server.habitat.SaveHabitatBlockSettingsPacket
import com.cobblemon.mod.common.util.cobblemonResource
import com.cobblemon.mod.common.util.lang
import java.awt.Color
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.Screen
import net.minecraft.core.BlockPos
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
    /** Use this as reference data for what buckets can be chosen and how rare they are */
    val buckets: List<SpawnBucket>,
    /** Use this as reference data for pre-made activated habitat pools */
    val activatedHabitatPools: Map<ResourceLocation, ActivatedHabitatPool>,
    /** Use this as reference data for pre-made natural habitat pools */
    val naturalHabitatPools: Map<ResourceLocation, NaturalHabitatPool>,
    /** The current settings - edit the properties in here then send it back to the server. */
    val habitatSettingsDTO: HabitatSettingsDTO
) : Screen(lang("ui.edit.habitat")), CobblemonRenderable {
    private var scrollOffset = 0.0
    private lateinit var mimicCycleButton: Button
    private lateinit var phaseOrderButton: Button
    private lateinit var spawnModeButton: Button
    private lateinit var poolButton: Button
    private lateinit var levelMinInput: EditBox
    private lateinit var levelMaxInput: EditBox
    private val labels = mutableListOf<Pair<String, Pair<Int, Int>>>()

    override fun init() {
        super.init()
        labels.clear()
        var y = 10 + scrollOffset.toInt()
        val spacing = 25
        val buttonWidth = getScaledWidth() - 20

        // Common Settings
        mimicCycleButton = addRenderableWidget(
            Button.builder(getMimicText()) { 
                val currentIndex = HabitatBlockEntity.mimicCycle.indexOf(habitatSettingsDTO.mimicId)
                val nextIndex = (currentIndex + 1) % HabitatBlockEntity.mimicCycle.size
                habitatSettingsDTO.mimicId = HabitatBlockEntity.mimicCycle[nextIndex]
                refresh()
            }.bounds(10, y, buttonWidth, 20).build()
        )
        y += spacing

        labels.add("Number of Phases" to (10 to y))
        y += 12
        val phasesInput = EditBox(font, 10, y, buttonWidth, 20, "Phases".text())
        phasesInput.value = habitatSettingsDTO.numberOfPhases.toString()
        phasesInput.setResponder { value ->
            value.toIntOrNull()?.let { if (it >= 1) habitatSettingsDTO.numberOfPhases = it }
        }
        addRenderableWidget(phasesInput)
        y += spacing

        phaseOrderButton = addRenderableWidget(
            Button.builder(getPhaseOrderText()) {
                val orders = HabitatPhaseOrder.entries
                val currentIndex = orders.indexOf(habitatSettingsDTO.phaseOrder)
                habitatSettingsDTO.phaseOrder = orders[(currentIndex + 1) % orders.size]
                refresh()
            }.bounds(10, y, buttonWidth, 20).build()
        )
        y += spacing

        labels.add("Level Range (Min - Max)" to (10 to y))
        y += 12
        levelMinInput = EditBox(font, 10, y, buttonWidth / 2 - 5, 20, "Min Level".text())
        levelMinInput.value = habitatSettingsDTO.levelRange.first.toString()
        levelMinInput.setResponder { value ->
            value.toIntOrNull()?.let { min ->
                if (min >= 1) habitatSettingsDTO.levelRange = min..habitatSettingsDTO.levelRange.last
            }
        }
        addRenderableWidget(levelMinInput)

        levelMaxInput = EditBox(font, 10 + buttonWidth / 2 + 5, y, buttonWidth / 2 - 5, 20, "Max Level".text())
        levelMaxInput.value = habitatSettingsDTO.levelRange.last.toString()
        levelMaxInput.setResponder { value ->
            value.toIntOrNull()?.let { max ->
                if (max >= habitatSettingsDTO.levelRange.first) habitatSettingsDTO.levelRange = habitatSettingsDTO.levelRange.first..max
            }
        }
        addRenderableWidget(levelMaxInput)
        y += spacing

        // Spawning Mode Toggle
        spawnModeButton = addRenderableWidget(
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
            
            addRenderableWidget(
                Button.builder(getTriggerText()) {
                    val triggers = ActivatedHabitatSpawning.Trigger.entries
                    val currentIndex = triggers.indexOf(activated.trigger)
                    activated.trigger = triggers[(currentIndex + 1) % triggers.size]
                    it.message = getTriggerText()
                    refresh()
                }.bounds(10, y, buttonWidth, 20).build()
            )
            y += spacing

            labels.add("Spawn Range (blocks)" to (10 to y))
            y += 12
            val spawnRangeInput = EditBox(font, 10, y, buttonWidth, 20, "Spawn Range".text())
            spawnRangeInput.value = activated.spawnRange.toString()
            spawnRangeInput.setResponder { value ->
                value.toIntOrNull()?.let { if (it >= 0) activated.spawnRange = it }
            }
            addRenderableWidget(spawnRangeInput)
            y += spacing

            labels.add("Cancelled Natural Spawning Range (blocks)" to (10 to y))
            y += 12
            val cancelledRangeInput = EditBox(font, 10, y, buttonWidth, 20, "Cancelled Range".text())
            cancelledRangeInput.value = activated.cancelledNaturalSpawningRange.toString()
            cancelledRangeInput.setResponder { value ->
                value.toIntOrNull()?.let { if (it >= 0) activated.cancelledNaturalSpawningRange = it }
            }
            addRenderableWidget(cancelledRangeInput)
            y += spacing

            labels.add("Max Spawns (-1 for infinite)" to (10 to y))
            y += 12
            val maxSpawnsInput = EditBox(font, 10, y, buttonWidth, 20, "Max Spawns".text())
            maxSpawnsInput.value = activated.maxSpawns.toString()
            maxSpawnsInput.setResponder { value ->
                value.toIntOrNull()?.let { activated.maxSpawns = it }
            }
            addRenderableWidget(maxSpawnsInput)
            y += spacing
        } else {
            val natural = habitatSettingsDTO.naturalSettings!!
            
            addRenderableWidget(
                Button.builder(getReplaceSpawnsText()) {
                    natural.replaceSpawns = !natural.replaceSpawns
                    it.message = getReplaceSpawnsText()
                    refresh()
                }.bounds(10, y, buttonWidth, 20).build()
            )
            y += spacing

            labels.add("Range of Influence (blocks)" to (10 to y))
            y += 12
            val rangeInput = EditBox(font, 10, y, buttonWidth, 20, "Range of Influence".text())
            rangeInput.value = natural.rangeOfInfluence.toString()
            rangeInput.setResponder { value ->
                value.toIntOrNull()?.let { if (it >= 0) natural.rangeOfInfluence = it }
            }
            addRenderableWidget(rangeInput)
            y += spacing
        }

        // Pool Selection
        addRenderableWidget(
            Button.builder("Pool: Reference".text()) {
                val pool = if (habitatSettingsDTO.isActivatedSpawning) 
                    habitatSettingsDTO.activatedSettings?.habitatPool 
                else 
                    habitatSettingsDTO.naturalSettings?.habitatPool
                pool?.isReference = true
                rebuildWidgets()
            }.bounds(10, y, buttonWidth / 2 - 5, 20).build()
        )
        addRenderableWidget(
            Button.builder("Pool: Custom".text()) {
                val pool = if (habitatSettingsDTO.isActivatedSpawning) {
                    habitatSettingsDTO.activatedSettings?.habitatPool
                } else {
                    habitatSettingsDTO.naturalSettings?.habitatPool
                }
                pool?.isReference = false
                pool?.id = cobblemonResource("custom__${System.currentTimeMillis()}")
                pool?.name = "Custom"
                pool?.spawns = mutableListOf()
                rebuildWidgets()
            }.bounds(10 + buttonWidth / 2 + 5, y, buttonWidth / 2 - 5, 20).build()
        )
        y += spacing

        val currentPool = if (habitatSettingsDTO.isActivatedSpawning) {
            habitatSettingsDTO.activatedSettings?.habitatPool
        } else {
            habitatSettingsDTO.naturalSettings?.habitatPool
        }

        if (currentPool != null) {
            if (currentPool.isReference) {
                poolButton = addRenderableWidget(
                    Button.builder(getPoolText()) {
                        cyclePool()
                        refresh()
                    }.bounds(10, y, buttonWidth, 20).build()
                )
                y += spacing
            } else {
                poolButton = addRenderableWidget(
                    Button.builder("Edit Custom Pool".text()) {
                        Minecraft.getInstance().setScreen(
                            HabitatPoolEditorGUI(currentPool, habitatSettingsDTO.isActivatedSpawning, buckets, spawnablePositionTypes, maxPokemonLevel, this)
                        )
                    }.bounds(10, y, buttonWidth, 20).build()
                )
                y += spacing
            }
        }

        // Save Button (fixed at bottom)
        addRenderableWidget(
            Button.builder("Save".text()) { _ -> save() }
                .bounds(10, getScaledHeight() - 30, buttonWidth, 20)
                .build()
        )
        
        refresh()
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, scrollX: Double, scrollY: Double): Boolean {
        scrollOffset = (scrollOffset + scrollY * 10).coerceIn(-200.0, 0.0)
        rebuildWidgets()
        return true
    }

    override fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        guiGraphics.fill(0, 0, width, height, Color(0, 0, 0, 100).rgb)
        super.render(guiGraphics, mouseX, mouseY, partialTick)
        
        labels.forEach { (label, pos) ->
            guiGraphics.drawString(font, label, pos.first, pos.second, 0xFFFFFF)
        }
    }

    fun refresh() {
        mimicCycleButton.message = getMimicText()
        phaseOrderButton.message = getPhaseOrderText()
        spawnModeButton.message = getSpawnModeText()
        poolButton.message = getPoolText()
    }

    private fun getMimicText() = "Block: ${habitatSettingsDTO.mimicId}".text()
    private fun getPhaseOrderText() = "Phase Order: ${habitatSettingsDTO.phaseOrder}".text()
    private fun getSpawnModeText() = "Mode: ${if (habitatSettingsDTO.isActivatedSpawning) "Activated" else "Natural"}".text()
    private fun getTriggerText() = "Trigger: ${habitatSettingsDTO.activatedSettings?.trigger}".text()
    private fun getReplaceSpawnsText() = "Replace Spawns: ${habitatSettingsDTO.naturalSettings?.replaceSpawns ?: false}".text()
    
    private fun getPoolText(): net.minecraft.network.chat.Component {
        val pool = if (habitatSettingsDTO.isActivatedSpawning) {
            habitatSettingsDTO.activatedSettings?.habitatPool
        } else {
            habitatSettingsDTO.naturalSettings?.habitatPool
        }
        return "Pool: ${pool?.name ?: "None"}".text()
    }

    private fun cyclePool() {
        val pools = if (habitatSettingsDTO.isActivatedSpawning) {
            activatedHabitatPools.keys.toList()
        } else {
            naturalHabitatPools.keys.toList()
        }
        
        if (pools.isEmpty()) return
        
        val currentPool = if (habitatSettingsDTO.isActivatedSpawning) {
            habitatSettingsDTO.activatedSettings?.habitatPool
        } else {
            habitatSettingsDTO.naturalSettings?.habitatPool
        }
        
        val currentIndex = pools.indexOf(currentPool?.id)
        val nextIndex = (currentIndex + 1) % pools.size
        val nextPoolId = pools[nextIndex]
        
        val newPool = HabitatPoolSettingsDTO(
            if (habitatSettingsDTO.isActivatedSpawning) 
                activatedHabitatPools[nextPoolId]!! 
            else 
                naturalHabitatPools[nextPoolId]!!
        )
        newPool.isReference = true
        newPool.id = nextPoolId
        
        if (habitatSettingsDTO.isActivatedSpawning) {
            habitatSettingsDTO.activatedSettings?.habitatPool = newPool
        } else {
            habitatSettingsDTO.naturalSettings?.habitatPool = newPool
        }
    }

    fun save() {
        SaveHabitatBlockSettingsPacket(
            blockPos = blockPos,
            settings = habitatSettingsDTO
        ).sendToServer()
        Minecraft.getInstance().setScreen(null)
    }

    fun getScaledWidth() = Minecraft.getInstance().window.guiScaledWidth

    fun getScaledHeight() = Minecraft.getInstance().window.guiScaledHeight
}