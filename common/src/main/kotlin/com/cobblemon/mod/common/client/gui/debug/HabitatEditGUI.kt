/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.gui.debug

import com.cobblemon.mod.common.CobblemonNetwork
import com.cobblemon.mod.common.api.text.text
import com.cobblemon.mod.common.block.habitat.HabitatBlockEntity
import com.cobblemon.mod.common.client.gui.CobblemonRenderable
import com.cobblemon.mod.common.net.messages.server.debug.UpdateHabitatBlockPacket
import com.cobblemon.mod.common.util.lang
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.screens.Screen
import java.awt.Color
import net.minecraft.resources.ResourceLocation

class HabitatEditGUI(val habitatEntity: HabitatBlockEntity) : Screen(lang("ui.edit.habitat")), CobblemonRenderable {
    private lateinit var mimicCycleButton: Button
    private var originalMimicId: ResourceLocation = habitatEntity.mimicId

    override fun init() {
        super.init()

        mimicCycleButton = addRenderableWidget(
            Button.builder("Block Mimicking: ${habitatEntity.mimicId.namespace}:${habitatEntity.mimicId.path}".text()) { button ->
                val currentIndex = HabitatBlockEntity.mimicCycle.indexOf(habitatEntity.mimicId)
                val nextIndex = (currentIndex + 1) % HabitatBlockEntity.mimicCycle.size
                habitatEntity.mimicId = HabitatBlockEntity.mimicCycle[nextIndex]
                refresh()
            }.bounds(10, 10, getScaledWidth() - 20, 20).build()
        )

        addRenderableWidget(
            Button.builder("Save".text()) { button ->
                save()
            }.bounds(10, getScaledHeight() - 20, getScaledWidth() - 20, 20).build()
        )
        refresh()
    }

    override fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        guiGraphics.fill(0, 0, width, height, Color(0, 0, 0, 100).rgb)
        super.render(guiGraphics, mouseX, mouseY, partialTick)
    }

    fun refresh() {
        mimicCycleButton.message = "Block Mimicking: ${habitatEntity.mimicId}".text()
    }

    override fun onClose() {
        super.onClose()
        habitatEntity.mimicId = originalMimicId
    }

    fun save() {
        CobblemonNetwork.sendToServer(
            UpdateHabitatBlockPacket(
                habitatPos = habitatEntity.blockPos,
                mimicId = habitatEntity.mimicId
            )
        )
        refresh()
        Minecraft.getInstance().setScreen(null)
    }


    fun getScaledWidth() = Minecraft.getInstance().window.guiScaledWidth

    fun getScaledHeight() = Minecraft.getInstance().window.guiScaledHeight
}