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
import com.cobblemon.mod.common.api.text.gray
import com.cobblemon.mod.common.item.AprijuiceItem
import com.cobblemon.mod.common.util.lang
import net.minecraft.network.chat.Component
import net.minecraft.world.item.ItemStack

object AprijuiceTooltipGenerator : TooltipGenerator() {
    override fun generateAdditionalTooltip(stack: ItemStack, lines: MutableList<Component>): MutableList<Component>? {
        if (stack.item !is AprijuiceItem) return null

        val flavourComponent = stack.get(CobblemonItemComponents.FLAVOUR) ?: return null

        val resultLines = mutableListOf<Component>()

        val quality = flavourComponent.getQuality()
        resultLines.add(lang("cooking.cooking_quality", quality.getLang()))

        SeasoningTooltipGenerator.generateAdditionalTooltip(stack, lines)

        return resultLines
    }
}