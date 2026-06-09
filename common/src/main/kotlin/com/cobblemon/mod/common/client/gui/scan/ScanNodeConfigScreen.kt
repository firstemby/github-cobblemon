/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.gui.scan

import com.cobblemon.mod.common.api.pokedex.scan.ScannedDataCategory
import com.cobblemon.mod.common.api.text.text
import com.cobblemon.mod.common.entity.scan.ScanNodeEntity
import com.cobblemon.mod.common.net.messages.server.pokedex.scanner.SaveScanNodeConfigPacket
import com.cobblemon.mod.common.util.lang
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import kotlin.math.max

/** Builder config screen for a ScanNode: pick a category, set the scan id, and up to four frame fields. */
class ScanNodeConfigScreen(
    private val entityId: Int,
    initialCategory: String,
    initialScanId: String,
    initialFrames: List<String>
) : Screen(lang("ui.scan_node.title")) {

    companion object {
        private const val FIELD_WIDTH = 200
        private const val FIELD_HEIGHT = 20
        private const val ROW_GAP = 24
    }

    private var categoryIndex = max(0, ScannedDataCategory.entries.indexOfFirst { it.id == initialCategory })
    private var scanIdValue = initialScanId
    private val frameValues = MutableList(ScanNodeEntity.FRAME_COUNT) { initialFrames.getOrElse(it) { "" } }

    private val category get() = ScannedDataCategory.entries[categoryIndex]

    override fun init() {
        super.init()
        val left = width / 2 - FIELD_WIDTH / 2
        var y = height / 2 - 80

        addRenderableWidget(
            Button.builder(categoryLabel()) {
                categoryIndex = (categoryIndex + 1) % ScannedDataCategory.entries.size
                it.message = categoryLabel()
            }.bounds(left, y, FIELD_WIDTH, FIELD_HEIGHT).build()
        )
        y += ROW_GAP + 4

        addRenderableWidget(
            EditBox(font, left, y, FIELD_WIDTH, FIELD_HEIGHT, lang("ui.scan_node.scan_id")).also {
                it.setMaxLength(64)
                it.value = scanIdValue
                it.setResponder { v -> scanIdValue = v }
            }
        )
        y += ROW_GAP + 4

        for (i in 0 until ScanNodeEntity.FRAME_COUNT) {
            addRenderableWidget(
                EditBox(font, left, y, FIELD_WIDTH, FIELD_HEIGHT, lang("ui.scan_node.frame", i + 1)).also {
                    it.setMaxLength(64)
                    it.value = frameValues[i]
                    it.setResponder { v -> frameValues[i] = v }
                }
            )
            y += ROW_GAP
        }
        y += 8

        addRenderableWidget(
            Button.builder(lang("ui.generic.save")) { save() }
                .bounds(left, y, FIELD_WIDTH, FIELD_HEIGHT).build()
        )
    }

    private fun categoryLabel(): Component =
        lang("ui.scan_node.category", lang("ui.pokedex.cobblepedia.category.${category.id}"))

    private fun save() {
        SaveScanNodeConfigPacket(entityId, category.id, scanIdValue.trim(), frameValues.map { it.trim() }).sendToServer()
        minecraft?.setScreen(null)
    }

    override fun render(context: GuiGraphics, mouseX: Int, mouseY: Int, delta: Float) {
        renderBackground(context, mouseX, mouseY, delta)
        super.render(context, mouseX, mouseY, delta)
        context.drawCenteredString(font, title, width / 2, height / 2 - 100, 0xFFFFFF.toInt())
    }

    override fun isPauseScreen(): Boolean = false
}
