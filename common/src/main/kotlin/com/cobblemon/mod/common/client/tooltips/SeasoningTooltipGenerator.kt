/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.tooltips

import com.cobblemon.mod.common.api.cooking.Seasonings
import net.minecraft.network.chat.Component
import net.minecraft.world.item.ItemStack

object SeasoningTooltipGenerator : TooltipGenerator() {
    override fun generateCategoryTooltip(stack: ItemStack, lines: MutableList<Component>): MutableList<Component>? {
        if (!Seasonings.isSeasoning(stack)) return null
        return mutableListOf(seasoningHeader)
    }

    override fun generateAdditionalTooltip(stack: ItemStack, lines: MutableList<Component>): MutableList<Component>? {
        val flavors = Seasonings.getFlavoursFromItemStack(stack)
        if (flavors.isEmpty()) {
            return null
        }
        return generateAdditionalFlavorTooltip(flavors)
    }
}
