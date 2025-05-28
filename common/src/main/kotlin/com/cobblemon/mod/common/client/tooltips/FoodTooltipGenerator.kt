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
import net.minecraft.network.chat.Component
import net.minecraft.world.item.ItemStack

object FoodTooltipGenerator : TooltipGenerator() {
    private val foodHeader by lazy { lang("item_class.food").blue() }

    override fun generateCategoryTooltip(stack: ItemStack, lines: MutableList<Component>): MutableList<Component>? {
        val food = stack.get(CobblemonItemComponents.FOOD) ?: return null
        return mutableListOf(foodHeader)
    }

    override fun generateAdditionalTooltip(stack: ItemStack, lines: MutableList<Component>): MutableList<Component>? {
        val food = stack.get(CobblemonItemComponents.FOOD) ?: return null

        return mutableListOf(
                lang("tooltip.food.hunger", Component.literal("${food.hunger}").yellow()),
                lang("tooltip.food.saturation", Component.literal("%.2f".format(food.saturation)).green())
        )
    }
}