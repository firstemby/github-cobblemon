/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.tooltips

import com.cobblemon.mod.common.CobblemonItemComponents
import com.cobblemon.mod.common.api.text.blue
import com.cobblemon.mod.common.api.text.green
import com.cobblemon.mod.common.api.text.yellow
import com.cobblemon.mod.common.util.lang
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.item.ItemStack
import net.minecraft.world.effect.MobEffectUtil

object MobEffectTooltipGenerator : TooltipGenerator() {
    private val effectHeader by lazy { lang("item_class.mob_effect").blue() }

    override fun generateCategoryTooltip(stack: ItemStack, lines: MutableList<Component>): MutableList<Component>? {
        val effects = stack.get(CobblemonItemComponents.MOB_EFFECTS)?.mobEffects ?: return null
        return if (effects.isNotEmpty()) mutableListOf(effectHeader) else null
    }

    override fun generateAdditionalTooltip(stack: ItemStack, lines: MutableList<Component>): MutableList<Component>? {
        val effects = stack.get(CobblemonItemComponents.MOB_EFFECTS)?.mobEffects ?: return null
        if (effects.isEmpty()) return null

        val tickRate = Minecraft.getInstance().level?.tickRateManager()?.tickrate() ?: 20.0f
        val result = mutableListOf<Component>()

        // Always add the header FIRST, even for additional tooltip
        result.add(effectHeader)

        for (instance in effects) {
            val name = instance.effect.value().descriptionId
            val duration = MobEffectUtil.formatDuration(instance, 1.0f, tickRate) // Already a Component
            val amplifierRoman = getRomanNumeral(instance.amplifier + 1)

            result.add(
                lang(
                    "tooltip.mob_effect_entry",
                    Component.translatable(name).yellow(),
                    Component.literal(amplifierRoman).green(),
                    duration.string.green()
                )
            )
        }

        return result
    }

    private fun getRomanNumeral(number: Int): String {
        if (number <= 0) return number.toString() // no Roman numeral for zero or negatives

        val numerals = listOf(
                1000 to "M",
                900 to "CM",
                500 to "D",
                400 to "CD",
                100 to "C",
                90 to "XC",
                50 to "L",
                40 to "XL",
                10 to "X",
                9 to "IX",
                5 to "V",
                4 to "IV",
                1 to "I"
        )

        var remaining = number
        val result = StringBuilder()

        for ((value, numeral) in numerals) {
            while (remaining >= value) {
                result.append(numeral)
                remaining -= value
            }
        }

        return result.toString()
    }
}