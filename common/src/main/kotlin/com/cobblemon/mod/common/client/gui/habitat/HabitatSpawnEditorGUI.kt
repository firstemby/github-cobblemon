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
import com.cobblemon.mod.common.api.spawning.SpawnBucket
import com.cobblemon.mod.common.api.text.text
import com.cobblemon.mod.common.client.gui.CobblemonRenderable
import com.cobblemon.mod.common.pokemon.Species
import com.cobblemon.mod.common.util.adapters.IntRangesAdapter
import com.cobblemon.mod.common.util.lang
import java.awt.Color
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
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
    private lateinit var positionTypeButton: Button
    private lateinit var bucketButton: Button
    private val labels = mutableListOf<Pair<String, Pair<Int, Int>>>()

    val pokemonSpeciesByTranslatedName: Map<String, Species> by lazy {
        PokemonSpecies.implemented.associateBy { I18n.get(it.translationKey)?.lowercase() ?: it.translationKey }
    }

    override fun init() {
        super.init()
        labels.clear()
        var y = 10 + scrollOffset.toInt()
        val spacing = 25
        val buttonWidth = getScaledWidth() - 20

        labels.add("Species Name" to (10 to y))
        y += 12
        val speciesInput = EditBox(font, 10, y, buttonWidth, 20, "Species".text())
        speciesInput.value = spawn.species.name
        speciesInput.setResponder { value ->
            val species = pokemonSpeciesByTranslatedName[value.lowercase()]
            if (species != null) {
                spawn.species = species
            } else {
                try {
                    PokemonSpecies.getByName(value.lowercase())?.let { spawn.species = it }
                } catch (_: Exception) {
                    // bad input ig
                }
            }
        }
        addRenderableWidget(speciesInput)
        y += spacing

        positionTypeButton = addRenderableWidget(
            Button.builder("Position Type: ${spawn.spawnablePositionType}".text()) {
                val currentIndex = spawnablePositionTypes.indexOf(spawn.spawnablePositionType)
                spawn.spawnablePositionType = spawnablePositionTypes[(currentIndex + 1) % spawnablePositionTypes.size]
                refresh()
            }.bounds(10, y, buttonWidth, 20).build()
        )
        y += spacing

        if (spawn is NaturalHabitatSpawn) {
            bucketButton = addRenderableWidget(
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
            value.toFloatOrNull()?.let { if (it > 0) spawn.weight = it }
        }
        addRenderableWidget(weightInput)
        y += spacing

        labels.add("Level Range (Min - Max)" to (10 to y))
        y += 12
        val levelMinInput = EditBox(font, 10, y, buttonWidth / 2 - 5, 20, "Min Level".text())
        levelMinInput.value = spawn.levelRange.first.toString()
        levelMinInput.setResponder { value ->
            value.toIntOrNull()?.let { min ->
                if (min >= 1) spawn.levelRange = min..spawn.levelRange.last
            }
        }
        addRenderableWidget(levelMinInput)

        val levelMaxInput = EditBox(font, 10 + buttonWidth / 2 + 5, y, buttonWidth / 2 - 5, 20, "Max Level".text())
        levelMaxInput.value = (if (spawn.levelRange.last == Int.MAX_VALUE) maxPokemonLevel else spawn.levelRange.last).toString()
        levelMaxInput.setResponder { value ->
            value.toIntOrNull()?.let { max ->
                if (max >= spawn.levelRange.first) {
                    spawn.levelRange = spawn.levelRange.first..(if (max == maxPokemonLevel) Int.MAX_VALUE else max)
                }
            }
        }
        addRenderableWidget(levelMaxInput)
        y += spacing

        labels.add("Modifiers (e.g., shiny gender=male)" to (10 to y))
        y += 12
        val modifiersInput = EditBox(font, 10, y, buttonWidth, 20, "Modifiers".text())
        modifiersInput.value = spawn.modifiers?.asString() ?: ""
        modifiersInput.setResponder { value ->
            spawn.modifiers = if (value.isBlank()) null else PokemonProperties.parse(value)
        }
        addRenderableWidget(modifiersInput)
        y += spacing

        labels.add("Phases (e.g., 1-3, 5)" to (10 to y))
        y += 12
        val phasesInput = EditBox(font, 10, y, buttonWidth, 20, "Phases".text())
        phasesInput.value = spawn.phases?.let { IntRangesAdapter.basic.serialize(it) } ?: ""
        phasesInput.setResponder { value ->
            spawn.phases = if (value.isBlank()) null else try { IntRangesAdapter.basic.deserialize(value) } catch (e: Exception) { null }
        }
        addRenderableWidget(phasesInput)
        y += spacing

        labels.add("Min Light (0-15, optional)" to (10 to y))
        y += 12
        val minLightInput = EditBox(font, 10, y, buttonWidth, 20, "Min Light".text())
        minLightInput.value = spawn.minLight?.toString() ?: ""
        minLightInput.setResponder { value ->
            spawn.minLight = value.toIntOrNull()?.coerceIn(0, 15)
        }
        addRenderableWidget(minLightInput)
        y += spacing

        labels.add("Max Light (0-15, optional)" to (10 to y))
        y += 12
        val maxLightInput = EditBox(font, 10, y, buttonWidth, 20, "Max Light".text())
        maxLightInput.value = spawn.maxLight?.toString() ?: ""
        maxLightInput.setResponder { value ->
            spawn.maxLight = value.toIntOrNull()?.coerceIn(0, 15)
        }
        addRenderableWidget(maxLightInput)
        y += spacing

        addRenderableWidget(
            Button.builder("Back".text()) {
                Minecraft.getInstance().setScreen(parentScreen)
            }.bounds(10, getScaledHeight() - 30, buttonWidth, 20).build()
        )
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, scrollX: Double, scrollY: Double): Boolean {
        scrollOffset = (scrollOffset + scrollY * 10).coerceIn(-300.0, 0.0)
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
        positionTypeButton.message = "Position Type: ${spawn.spawnablePositionType}".text()
        if (spawn is NaturalHabitatSpawn) {
            bucketButton.message = "Bucket: ${spawn.bucket.name}".text()
        }
    }

    fun getScaledWidth() = Minecraft.getInstance().window.guiScaledWidth
    fun getScaledHeight() = Minecraft.getInstance().window.guiScaledHeight
}
