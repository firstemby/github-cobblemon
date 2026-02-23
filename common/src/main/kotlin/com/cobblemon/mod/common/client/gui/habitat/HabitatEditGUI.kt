/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.gui.habitat

import com.cobblemon.mod.common.api.habitats.ActivatedHabitatPool
import com.cobblemon.mod.common.api.habitats.ActivatedHabitatSpawn
import com.cobblemon.mod.common.api.habitats.HabitatPhaseOrder
import com.cobblemon.mod.common.api.habitats.NaturalHabitatPool
import com.cobblemon.mod.common.api.habitats.dto.HabitatPoolSettingsDTO
import com.cobblemon.mod.common.api.habitats.dto.HabitatSettingsDTO
import com.cobblemon.mod.common.api.habitats.dto.NaturalHabitatSettingsDTO
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import com.cobblemon.mod.common.api.spawning.SpawnBucket
import com.cobblemon.mod.common.api.text.text
import com.cobblemon.mod.common.block.habitat.HabitatBlockEntity
import com.cobblemon.mod.common.client.gui.CobblemonRenderable
import com.cobblemon.mod.common.net.messages.server.habitat.SaveHabitatBlockSettingsPacket
import com.cobblemon.mod.common.util.cobblemonResource
import com.cobblemon.mod.common.util.lang
import com.cobblemon.mod.common.util.toProperties
import java.awt.Color
import kotlin.random.Random
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
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
    private lateinit var mimicCycleButton: Button

    override fun init() {
        super.init()

        mimicCycleButton = addRenderableWidget(
            Button.builder("Block Mimicking: ${habitatSettingsDTO.mimicId}".text()) { button ->
                val currentIndex = HabitatBlockEntity.mimicCycle.indexOf(habitatSettingsDTO.mimicId)
                val nextIndex = (currentIndex + 1) % HabitatBlockEntity.mimicCycle.size
                habitatSettingsDTO.mimicId = HabitatBlockEntity.mimicCycle[nextIndex]
                refresh()
            }.bounds(10, 10, getScaledWidth() - 20, 20).build()
        )

        addRenderableWidget(
            Button.builder("Save".text()) { _ -> save() }
                .bounds(10, getScaledHeight() - 20, getScaledWidth() - 20, 20)
                .build()
        )
        refresh()
    }

    override fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        guiGraphics.fill(0, 0, width, height, Color(0, 0, 0, 100).rgb)
        super.render(guiGraphics, mouseX, mouseY, partialTick)
    }

    fun refresh() {
        mimicCycleButton.message = "Block Mimicking: ${habitatSettingsDTO.mimicId}".text()
    }

    fun save() {

        // -------- TEST CODE START
        val i = Random.nextInt(3)

        val speciesSets = arrayOf(
            arrayOf(PokemonSpecies.getByName("bulbasaur")!!, PokemonSpecies.getByName("ivysaur")!!, PokemonSpecies.getByName("venusaur")!!),
            arrayOf(PokemonSpecies.getByName("charmander")!!, PokemonSpecies.getByName("charmeleon")!!, PokemonSpecies.getByName("charizard")!!),
            arrayOf(PokemonSpecies.getByName("squirtle")!!, PokemonSpecies.getByName("wartortle")!!, PokemonSpecies.getByName("blastoise")!!)
        )

        val speciesSet = speciesSets[i]


        val activatedSettings = habitatSettingsDTO.activatedSettings
        if (activatedSettings != null) {
            activatedSettings.spawnRange = 10
            activatedSettings.cancelledNaturalSpawningRange = 36
            activatedSettings.habitatPool.spawns = speciesSet.map {
                val entry = ActivatedHabitatSpawn()
                entry.species = it
                entry.modifiers = "shiny=true".toProperties()
                entry.spawnablePositionType = "grounded"
                entry
            }
        }


        val naturalSettings = habitatSettingsDTO.naturalSettings ?: NaturalHabitatSettingsDTO()
        naturalSettings.habitatPool = HabitatPoolSettingsDTO(NaturalHabitatPool.default())
        naturalSettings.habitatPool.isReference = true
        naturalSettings.habitatPool.id = cobblemonResource("natural_charmander_line")
        naturalSettings.replaceSpawns = true
        naturalSettings.rangeOfInfluence = 32
//        naturalSettings.habitatPool.spawns = speciesSet.map {
//            val entry = NaturalHabitatSpawn()
//            entry.species = it
//            entry.bucket = Cobblemon.bestSpawner.config.buckets.maxBy { it.weight }
//            entry.modifiers = "shiny=true".toProperties()
//            entry.spawnablePositionType = SpawnablePosition.getByName("grounded")!!
//            entry
//        }
        habitatSettingsDTO.isActivatedSpawning = false
        habitatSettingsDTO.naturalSettings = naturalSettings

        habitatSettingsDTO.phaseOrder = HabitatPhaseOrder.FULL_RANDOM
        habitatSettingsDTO.numberOfPhases = 4
        // -------- TEST CODE END

        SaveHabitatBlockSettingsPacket(
            blockPos = blockPos,
            settings = habitatSettingsDTO
        ).sendToServer()
        Minecraft.getInstance().setScreen(null)
    }

    fun getScaledWidth() = Minecraft.getInstance().window.guiScaledWidth

    fun getScaledHeight() = Minecraft.getInstance().window.guiScaledHeight
}