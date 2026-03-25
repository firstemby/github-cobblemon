/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.tooltips

import com.cobblemon.mod.common.api.text.*
import com.cobblemon.mod.common.item.interactive.TechnicalMachineItem
import com.cobblemon.mod.common.item.components.TMMoveComponent
import com.cobblemon.mod.common.util.lang
import net.minecraft.core.component.DataComponents
import net.minecraft.network.chat.Component
import net.minecraft.world.item.ItemStack

object TechnicalMachineTooltipGenerator : TooltipGenerator() {
    private val technicalMachineHeader by lazy { lang("technical_machine_effect_header").blue() }
    private val technicalMachineItemClass by lazy { lang("item_class.technical_machine").blue() }

    // Max characters per description line
    private const val MAX_LINE_LENGTH = 35

    override fun generateCategoryTooltip(stack: ItemStack, lines: MutableList<Component>): MutableList<Component>? {
        if (stack.get(DataComponents.HIDE_ADDITIONAL_TOOLTIP) != null) return null
        if (stack.item !is TechnicalMachineItem) return null
        return mutableListOf(technicalMachineItemClass)
    }

    override fun generateAdditionalTooltip(stack: ItemStack, lines: MutableList<Component>): MutableList<Component>? {
        if (stack.get(DataComponents.HIDE_ADDITIONAL_TOOLTIP) != null) return null
        if (stack.item !is TechnicalMachineItem) return null

        val move = TMMoveComponent.getTMMove(stack) ?: return null

        val result = mutableListOf<Component>()

        result.add(technicalMachineHeader)

        // Type
        val typeLabel = lang("ui.info.type").white()
        val typeValue = move.elementalType.displayName.copy().withStyle { it.withColor(move.elementalType.primaryColor) }

        // Category
        val categoryLabel = lang("move.category").white()
        val categoryKey = "move.category.${move.damageCategory.name.lowercase()}"
        val categoryValue = lang(categoryKey).gold()

        result.add(
            Component.empty()
                .append(typeLabel).append(": ").append(typeValue)
                .append(Component.literal("  "))
                .append(categoryLabel).append(": ").append(categoryValue)
        )

        // Power / Accuracy / PP
        val powerLabel = lang("ui.power").white()
        val accuracyLabel = lang("ui.accuracy").white()
        val ppLabel = lang("ui.pp").white()

        val accuracy = move.accuracy.toInt()

        val powerValue = Component.literal(move.power.toInt().toString()).yellow()
        val accuracyValue = Component.literal("${if (accuracy != -1) accuracy else 100}%").yellow()
        val ppValue = Component.literal(move.pp.toString()).yellow()

        result.add(
            Component.empty()
                .append(powerLabel).append(": ").append(powerValue)
                .append(Component.literal("  "))
                .append(accuracyLabel).append(": ").append(accuracyValue)
                .append(Component.literal("  "))
                .append(ppLabel).append(": ").append(ppValue)
        )

        // Description (wrapped)
        val description = move.description.string
        val wrappedLines = wrapText(description, MAX_LINE_LENGTH)
        for (line in wrappedLines) {
            result.add(Component.literal(line).gray())
        }

        return result
    }

    private fun wrapText(text: String, maxLength: Int): List<String> {
        val words = text.split(" ")
        val lines = mutableListOf<String>()
        var currentLine = StringBuilder()

        for (word in words) {
            if (currentLine.length + word.length + 1 > maxLength) {
                lines.add(currentLine.toString())
                currentLine = StringBuilder()
            }
            if (currentLine.isNotEmpty()) currentLine.append(" ")
            currentLine.append(word)
        }

        if (currentLine.isNotEmpty()) {
            lines.add(currentLine.toString())
        }

        return lines
    }
}