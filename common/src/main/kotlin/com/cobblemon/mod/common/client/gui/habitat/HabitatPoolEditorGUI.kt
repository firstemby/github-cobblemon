/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.gui.habitat

import com.cobblemon.mod.common.api.habitats.ActivatedHabitatSpawn
import com.cobblemon.mod.common.api.habitats.NaturalHabitatSpawn
import com.cobblemon.mod.common.api.habitats.dto.HabitatPoolSettingsDTO
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import com.cobblemon.mod.common.api.spawning.SpawnBucket
import com.cobblemon.mod.common.api.text.text
import com.cobblemon.mod.common.client.gui.CobblemonRenderable
import com.cobblemon.mod.common.util.adapters.IntRangesAdapter
import com.cobblemon.mod.common.util.lang
import java.awt.Color
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.screens.Screen

class HabitatPoolEditorGUI(
    val pool: HabitatPoolSettingsDTO,
    val isActivated: Boolean,
    val buckets: List<SpawnBucket>,
    val spawnablePositionTypes: List<String>,
    val maxPokemonLevel: Int,
    val parentScreen: Screen
) : Screen(lang("ui.edit.habitat.pool")), CobblemonRenderable {
    private var scrollOffset = 0.0
    private val headers = mutableListOf<Pair<String, Int>>()
    private val spawnRows = mutableListOf<SpawnRowData>()

    data class SpawnRowData(
        val species: String,
        val levels: String,
        val phases: String,
        val position: String,
        val weight: String,
        val bucket: String?,
        val y: Int
    )

    override fun init() {
        super.init()
        headers.clear()
        spawnRows.clear()
        var y = 10 + scrollOffset.toInt()
        val spacing = 25
        val buttonWidth = getScaledWidth() - 20

        if (pool.spawns == null) {
            pool.spawns = mutableListOf()
        }

        // Headers
        val colWidth = if (isActivated) buttonWidth / 5 else buttonWidth / 6
        var x = 10
        headers.add("Species" to x)
        x += colWidth
        headers.add("Levels" to x)
        x += colWidth
        headers.add("Phases" to x)
        x += colWidth
        headers.add("Position" to x)
        x += colWidth
        headers.add("Weight" to x)
        if (!isActivated) {
            x += colWidth - 10
            headers.add("Bucket" to x)
        }
        y += 20

        var spawns = pool.spawns!!
        if (!isActivated) {
            spawns = spawns.sortedBy { (it as? NaturalHabitatSpawn)?.bucket?.name?.first() }.toMutableList()
        }

        spawns.forEachIndexed { index, spawn ->
            val rowY = y

            var levelMax = spawn.levelRange.last
            if (levelMax == Int.MAX_VALUE) {
                levelMax = maxPokemonLevel
            }
            
            spawnRows.add(SpawnRowData(
                species = spawn.species.name,
                levels = "${spawn.levelRange.first}-$levelMax",
                phases = spawn.phases?.let { IntRangesAdapter.basic.serialize(it) } ?: "All",
                position = spawn.spawnablePositionType,
                weight = spawn.weight.toString(),
                bucket = if (!isActivated && spawn is NaturalHabitatSpawn) spawn.bucket.name.first().uppercaseChar().toString() else null,
                y = rowY
            ))
            
            // Edit button
            addRenderableWidget(
                Button.builder("E".text()) {
                    Minecraft.getInstance().setScreen(HabitatSpawnEditorGUI(spawn, isActivated, maxPokemonLevel, buckets, spawnablePositionTypes, this))
                }.bounds(getScaledWidth() - 55, rowY, 20, 20).build()
            )
            
            // Delete button
            addRenderableWidget(
                Button.builder("X".text()) {
                    pool.spawns!!.removeAt(index)
                    rebuildWidgets()
                }.bounds(getScaledWidth() - 30, rowY, 20, 20).build()
            )
            
            y += spacing
        }

        addRenderableWidget(
            Button.builder("Add Spawn".text()) {
                val newSpawn = if (isActivated) ActivatedHabitatSpawn() else NaturalHabitatSpawn()
                newSpawn.species = PokemonSpecies.implemented.random()
                newSpawn.spawnablePositionType = spawnablePositionTypes.firstOrNull() ?: "grounded"
                if (newSpawn is NaturalHabitatSpawn) {
                    newSpawn.bucket = buckets.firstOrNull() ?: return@builder
                }
                pool.spawns!!.add(newSpawn)
                rebuildWidgets()
            }.bounds(10, y, buttonWidth, 20).build()
        )
        y += spacing

        addRenderableWidget(
            Button.builder("Back".text()) {
                Minecraft.getInstance().setScreen(parentScreen)
            }.bounds(10, getScaledHeight() - 30, buttonWidth, 20).build()
        )
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, scrollX: Double, scrollY: Double): Boolean {
        scrollOffset = (scrollOffset + scrollY * 10).coerceIn(-500.0, 0.0)
        rebuildWidgets()
        return true
    }

    override fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        guiGraphics.fill(0, 0, width, height, Color(0, 0, 0, 100).rgb)
        
        val buttonWidth = getScaledWidth() - 20
        val colWidth = if (isActivated) buttonWidth / 5 else buttonWidth / 6
        
        // Draw headers
        headers.forEach { (header, x) ->
            guiGraphics.drawString(font, header, x, 15 + scrollOffset.toInt(), 0xFFFFFF)
        }
        
        // Draw spawn rows
        spawnRows.forEach { row ->
            var x = 10
            guiGraphics.drawString(font, row.species, x, row.y + 5, 0xFFFFFF)
            x += colWidth
            guiGraphics.drawString(font, row.levels, x, row.y + 5, 0xFFFFFF)
            x += colWidth
            guiGraphics.drawString(font, row.phases, x, row.y + 5, 0xFFFFFF)
            x += colWidth
            guiGraphics.drawString(font, row.position, x, row.y + 5, 0xFFFFFF)
            x += colWidth
            guiGraphics.drawString(font, row.weight, x, row.y + 5, 0xFFFFFF)
            if (row.bucket != null) {
                x += colWidth - 10
                guiGraphics.drawString(font, row.bucket, x, row.y + 5, 0xFFFFFF)
            }
        }
        
        super.render(guiGraphics, mouseX, mouseY, partialTick)
    }

    fun getScaledWidth() = Minecraft.getInstance().window.guiScaledWidth
    fun getScaledHeight() = Minecraft.getInstance().window.guiScaledHeight
}
