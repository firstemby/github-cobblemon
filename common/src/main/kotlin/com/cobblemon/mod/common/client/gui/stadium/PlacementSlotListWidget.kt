/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.gui.stadium

import com.cobblemon.mod.common.battles.stadium.StadiumBlockLayout
import com.cobblemon.mod.common.client.gui.stadium.widget.StadiumSlotEntryWidget
import com.cobblemon.mod.common.client.render.drawScaledText
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.narration.NarrationElementOutput
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.world.phys.Vec3

class PlacementSlotListWidget(
    x: Int,
    y: Int,
    width: Int,
    height: Int,
    private val stadiumBlockPos: BlockPos,
    private val layoutType: String,
    private val layout: StadiumBlockLayout,
    private val onRequestWand: (String, Int, String, Int) -> Unit,
    private val onClearSlot: (Int, String, Int) -> Unit,
    private val onAddSlot: (Int, String) -> Unit,
    private val rebuildScreen: () -> Unit
) : AbstractWidget(x, y, width, height, Component.empty()) {

    companion object {
        private const val SECTION_HEADER_HEIGHT = 14
        private const val ADD_BUTTON_HEIGHT = 16
        private const val SECTION_GAP = 6
        const val SIDE1_ACTOR_COLOR = 0xFF4488FF.toInt()
        const val SIDE1_POKEMON_COLOR = 0xFF44DDFF.toInt()
        const val SIDE2_ACTOR_COLOR = 0xFFFF4444.toInt()
        const val SIDE2_POKEMON_COLOR = 0xFFFF8844.toInt()
    }

    private val slotEntries = mutableListOf<StadiumSlotEntryWidget>()
    private val childButtons = mutableListOf<Button>()
    private var selectedFlatIndex: Int? = null

    var onSlotSelected: ((Int) -> Unit)? = null

    fun buildSlotWidgets(addWidget: (AbstractWidget) -> AbstractWidget) {
        slotEntries.clear()
        childButtons.clear()
        var currentY = y + 2
        val slotWidth = width - 6
        val slotX = x + 3

        var flatIndex = 0

        // -- Side 1 --
        currentY += SECTION_HEADER_HEIGHT

        // Side 1 actors
        for (i in layout.side1.actors.indices) {
            val placement = layout.side1.actors[i]
            val idx = flatIndex
            val entry = StadiumSlotEntryWidget(
                x = slotX, y = currentY, width = slotWidth,
                label = "Actor ${i + 1}",
                position = placement.position,
                indicatorColor = SIDE1_ACTOR_COLOR,
                isSelected = selectedFlatIndex == idx,
                onWand = { onRequestWand(layoutType, 1, "actors", i) },
                onDelete = { onClearSlot(1, "actors", i) },
                onSelect = {
                    selectSlot(idx)
                    onSlotSelected?.invoke(idx)
                }
            )
            addWidget(entry)
            slotEntries.add(entry)
            currentY += StadiumSlotEntryWidget.ENTRY_HEIGHT
            flatIndex++
        }

        // + Add Actor button for Side 1
        val addActor1 = Button.builder(Component.literal("+ Add Actor")) {
            onAddSlot(1, "actors")
        }.bounds(slotX, currentY, slotWidth, ADD_BUTTON_HEIGHT - 2).build()
        addWidget(addActor1)
        childButtons.add(addActor1)
        currentY += ADD_BUTTON_HEIGHT

        // Side 1 pokemon
        for (i in layout.side1.pokemon.indices) {
            val placement = layout.side1.pokemon[i]
            val idx = flatIndex
            val entry = StadiumSlotEntryWidget(
                x = slotX, y = currentY, width = slotWidth,
                label = "Pokemon ${i + 1}",
                position = placement.position,
                indicatorColor = SIDE1_POKEMON_COLOR,
                isSelected = selectedFlatIndex == idx,
                onWand = { onRequestWand(layoutType, 1, "pokemon", i) },
                onDelete = { onClearSlot(1, "pokemon", i) },
                onSelect = {
                    selectSlot(idx)
                    onSlotSelected?.invoke(idx)
                }
            )
            addWidget(entry)
            slotEntries.add(entry)
            currentY += StadiumSlotEntryWidget.ENTRY_HEIGHT
            flatIndex++
        }

        // + Add Pokemon button for Side 1
        val addPkmn1 = Button.builder(Component.literal("+ Add Pokemon")) {
            onAddSlot(1, "pokemon")
        }.bounds(slotX, currentY, slotWidth, ADD_BUTTON_HEIGHT - 2).build()
        addWidget(addPkmn1)
        childButtons.add(addPkmn1)
        currentY += ADD_BUTTON_HEIGHT + SECTION_GAP

        // -- Side 2 --
        currentY += SECTION_HEADER_HEIGHT

        // Side 2 actors
        for (i in layout.side2.actors.indices) {
            val placement = layout.side2.actors[i]
            val idx = flatIndex
            val entry = StadiumSlotEntryWidget(
                x = slotX, y = currentY, width = slotWidth,
                label = "Actor ${i + 1}",
                position = placement.position,
                indicatorColor = SIDE2_ACTOR_COLOR,
                isSelected = selectedFlatIndex == idx,
                onWand = { onRequestWand(layoutType, 2, "actors", i) },
                onDelete = { onClearSlot(2, "actors", i) },
                onSelect = {
                    selectSlot(idx)
                    onSlotSelected?.invoke(idx)
                }
            )
            addWidget(entry)
            slotEntries.add(entry)
            currentY += StadiumSlotEntryWidget.ENTRY_HEIGHT
            flatIndex++
        }

        // + Add Actor button for Side 2
        val addActor2 = Button.builder(Component.literal("+ Add Actor")) {
            onAddSlot(2, "actors")
        }.bounds(slotX, currentY, slotWidth, ADD_BUTTON_HEIGHT - 2).build()
        addWidget(addActor2)
        childButtons.add(addActor2)
        currentY += ADD_BUTTON_HEIGHT

        // Side 2 pokemon
        for (i in layout.side2.pokemon.indices) {
            val placement = layout.side2.pokemon[i]
            val idx = flatIndex
            val entry = StadiumSlotEntryWidget(
                x = slotX, y = currentY, width = slotWidth,
                label = "Pokemon ${i + 1}",
                position = placement.position,
                indicatorColor = SIDE2_POKEMON_COLOR,
                isSelected = selectedFlatIndex == idx,
                onWand = { onRequestWand(layoutType, 2, "pokemon", i) },
                onDelete = { onClearSlot(2, "pokemon", i) },
                onSelect = {
                    selectSlot(idx)
                    onSlotSelected?.invoke(idx)
                }
            )
            addWidget(entry)
            slotEntries.add(entry)
            currentY += StadiumSlotEntryWidget.ENTRY_HEIGHT
            flatIndex++
        }

        // + Add Pokemon button for Side 2
        val addPkmn2 = Button.builder(Component.literal("+ Add Pokemon")) {
            onAddSlot(2, "pokemon")
        }.bounds(slotX, currentY, slotWidth, ADD_BUTTON_HEIGHT - 2).build()
        addWidget(addPkmn2)
        childButtons.add(addPkmn2)
    }

    fun selectSlot(index: Int) {
        selectedFlatIndex = index
        for ((i, entry) in slotEntries.withIndex()) {
            entry.isSelected = (i == index)
        }
    }

    override fun renderWidget(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        // Background
        guiGraphics.fill(x, y, x + width, y + height, 0xFF252540.toInt())
        guiGraphics.renderOutline(x, y, width, height, 0xFF5C5C7A.toInt())

        val slotX = x + 3
        var currentY = y + 2

        // Side 1 header
        drawScaledText(
            context = guiGraphics,
            text = Component.literal("Side 1"),
            x = slotX,
            y = currentY,
            scale = 0.9f,
            colour = SIDE1_ACTOR_COLOR,
            shadow = true
        )
        currentY += SECTION_HEADER_HEIGHT

        // Skip past Side 1 actor entries + add button
        currentY += layout.side1.actors.size * StadiumSlotEntryWidget.ENTRY_HEIGHT
        currentY += ADD_BUTTON_HEIGHT

        // Skip past Side 1 pokemon entries + add button + gap
        currentY += layout.side1.pokemon.size * StadiumSlotEntryWidget.ENTRY_HEIGHT
        currentY += ADD_BUTTON_HEIGHT + SECTION_GAP

        // Side 2 header
        drawScaledText(
            context = guiGraphics,
            text = Component.literal("Side 2"),
            x = slotX,
            y = currentY,
            scale = 0.9f,
            colour = SIDE2_ACTOR_COLOR,
            shadow = true
        )
    }

    // This widget is render-only for headers; don't consume mouse events so child widgets receive them
    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean = false
    override fun mouseReleased(mouseX: Double, mouseY: Double, button: Int): Boolean = false

    override fun updateWidgetNarration(narrationElementOutput: NarrationElementOutput) {
        defaultButtonNarrationText(narrationElementOutput)
    }
}
