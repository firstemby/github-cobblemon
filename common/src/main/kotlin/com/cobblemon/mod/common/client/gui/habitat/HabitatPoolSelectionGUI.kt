/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.gui.habitat

import com.cobblemon.mod.common.api.habitats.ActivatedHabitatPool
import com.cobblemon.mod.common.api.habitats.NaturalHabitatPool
import com.cobblemon.mod.common.api.text.text
import com.cobblemon.mod.common.client.gui.CobblemonRenderable
import java.awt.Color
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.ObjectSelectionList
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation

class HabitatPoolSelectionGUI(
    private val isActivated: Boolean,
    private val activatedPools: Map<ResourceLocation, ActivatedHabitatPool>,
    private val naturalPools: Map<ResourceLocation, NaturalHabitatPool>,
    private val selectedPoolId: ResourceLocation?,
    private val parentScreen: Screen,
    private val onSelect: (ResourceLocation?) -> Unit
) : Screen("Select Spawn Pool".text()), CobblemonRenderable {
    private val headerHeight = 24
    private val footerHeight = 44
    private val listPadding = 6
    private val backButtonHeight = 20
    private val backButtonBottomMargin = 10
    private lateinit var poolList: PoolList

    override fun init() {
        super.init()
        val listTop = headerHeight + listPadding
        val listBottom = getFooterTop() - listPadding
        val listHeight = (listBottom - listTop).coerceAtLeast(24)
        val listWidth = width - 20
        val options = getOptions()

        poolList = addRenderableWidget(
            PoolList(
                left = 10,
                top = listTop,
                listWidth = listWidth,
                listHeight = listHeight,
                options = options,
                selectedPoolId = selectedPoolId,
                onSelect = { poolId ->
                    onSelect(poolId)
                    Minecraft.getInstance().setScreen(parentScreen)
                }
            )
        )

        addRenderableWidget(
            Button.builder("Back".text()) {
                Minecraft.getInstance().setScreen(parentScreen)
            }.bounds(10, height - (backButtonHeight + backButtonBottomMargin), listWidth, backButtonHeight).build()
        )
    }

    override fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        guiGraphics.fill(0, 0, width, height, Color(0, 0, 0, 100).rgb)
        guiGraphics.fill(0, 0, width, headerHeight, Color(0, 0, 0, 150).rgb)
        guiGraphics.fill(0, getFooterTop(), width, height, Color(0, 0, 0, 150).rgb)
        super.render(guiGraphics, mouseX, mouseY, partialTick)
        guiGraphics.drawCenteredString(font, title, width / 2, 8, 0xFFFFFF)
    }

    private fun getFooterTop(): Int {
        return height - footerHeight
    }

    private fun getOptions(): List<Pair<ResourceLocation?, String>> {
        val options = mutableListOf<Pair<ResourceLocation?, String>>()
        options.add(null to "Custom")
        val pools = if (isActivated) {
            activatedPools.entries.map { it.key to it.value.name }
        } else {
            naturalPools.entries.map { it.key to it.value.name }
        }.sortedBy { it.second.lowercase() }
        options.addAll(pools)
        return options
    }

    private class PoolList(
        private val left: Int,
        private val top: Int,
        private val listWidth: Int,
        private val listHeight: Int,
        options: List<Pair<ResourceLocation?, String>>,
        selectedPoolId: ResourceLocation?,
        private val onSelect: (ResourceLocation?) -> Unit
    ) : ObjectSelectionList<PoolList.PoolEntry>(
        Minecraft.getInstance(),
        listWidth,
        listHeight,
        0,
        20
    ), CobblemonRenderable {
        init {
            x = left
            y = top
            setRectangle(listWidth, listHeight, left, top)
            options.forEach { (poolId, poolName) ->
                addEntry(PoolEntry(poolId, poolName, onSelect))
            }
            setSelected(children().firstOrNull { it.poolId == selectedPoolId })
        }

        override fun renderListBackground(guiGraphics: GuiGraphics) {}

        override fun getRowWidth(): Int {
            return listWidth - 8
        }

        override fun getScrollbarPosition(): Int {
            return left + listWidth - 6
        }

        private class PoolEntry(
            val poolId: ResourceLocation?,
            private val poolName: String,
            private val onSelect: (ResourceLocation?) -> Unit
        ) : ObjectSelectionList.Entry<PoolEntry>() {
            override fun render(
                guiGraphics: GuiGraphics,
                index: Int,
                rowTop: Int,
                rowLeft: Int,
                rowWidth: Int,
                rowHeight: Int,
                mouseX: Int,
                mouseY: Int,
                hovered: Boolean,
                partialTick: Float
            ) {
                guiGraphics.drawString(Minecraft.getInstance().font, poolName, rowLeft + 4, rowTop + 6, 0xFFFFFF)
            }

            override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
                if (!isMouseOver(mouseX, mouseY)) {
                    return false
                }
                onSelect(poolId)
                return true
            }

            override fun getNarration(): Component {
                return poolName.text()
            }
        }
    }
}
