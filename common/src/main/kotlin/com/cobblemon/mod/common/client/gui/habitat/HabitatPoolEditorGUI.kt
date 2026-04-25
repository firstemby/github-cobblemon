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
import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.screens.Screen

class HabitatPoolEditorGUI(
    val pool: HabitatPoolSettingsDTO,
    val isActivated: Boolean,
    val buckets: List<SpawnBucket>,
    val spawnablePositionTypes: List<String>,
    val maxPokemonLevel: Int,
    val parentScreen: Screen,
    val editable: Boolean = true
) : Screen(lang("ui.edit.habitat.pool")), CobblemonRenderable {
    private var scrollOffset = 0.0
    private var minScrollOffset = 0.0
    private val headerHeight = 24
    private val footerHeight = 44
    private val optionsTopPadding = 6
    private val optionsBottomPadding = 6
    private val actionButtonHeight = 20
    private val actionButtonBottomMargin = 10
    private val optionWidgets = mutableListOf<AbstractWidget>()
    private val footerWidgets = mutableListOf<AbstractWidget>()
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
        optionWidgets.clear()
        footerWidgets.clear()
        headers.clear()
        spawnRows.clear()
        scrollOffset = scrollOffset.coerceIn(minScrollOffset, 0.0)
        var y = getOptionsTop() + scrollOffset.toInt()
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
            
            if (editable) {
                addOptionWidget(
                    Button.builder("E".text()) {
                        Minecraft.getInstance().setScreen(HabitatSpawnEditorGUI(spawn, isActivated, maxPokemonLevel, buckets, spawnablePositionTypes, this))
                    }.bounds(getScaledWidth() - 55, rowY, 20, 20).build()
                )

                addOptionWidget(
                    Button.builder("X".text()) {
                        pool.spawns!!.removeAt(index)
                        rebuildWidgets()
                    }.bounds(getScaledWidth() - 30, rowY, 20, 20).build()
                )
            }
            
            y += spacing
        }

        if (editable) {
            addOptionWidget(
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
        }

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
        
        val buttonWidth = getScaledWidth() - 20
        val colWidth = if (isActivated) buttonWidth / 5 else buttonWidth / 6
        val optionsTop = getOptionsTop()
        val optionsBottom = getOptionsBottom()
        
        guiGraphics.enableScissor(0, optionsTop, width, optionsBottom)

        // Draw headers
        headers.forEach { (header, x) ->
            val y = optionsTop + 5 + scrollOffset.toInt()
            if (y >= optionsTop && y + font.lineHeight <= optionsBottom) {
                guiGraphics.drawString(font, header, x, y, 0xFFFFFF)
            }
        }
        
        // Draw spawn rows
        spawnRows.forEach { row ->
            if (row.y < optionsTop || row.y + 20 > optionsBottom) return@forEach
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

        optionWidgets.forEach { widget ->
            widget.render(guiGraphics, mouseX, mouseY, partialTick)
        }

        guiGraphics.disableScissor()

        footerWidgets.forEach { widget ->
            widget.render(guiGraphics, mouseX, mouseY, partialTick)
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
