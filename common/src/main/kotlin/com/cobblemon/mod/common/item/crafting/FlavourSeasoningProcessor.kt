/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.item.crafting

import com.cobblemon.mod.common.CobblemonItemComponents
import com.cobblemon.mod.common.api.cooking.Flavour
import com.cobblemon.mod.common.api.cooking.Seasonings
import com.cobblemon.mod.common.item.components.FlavourComponent
import net.minecraft.world.item.ItemStack

object FlavourSeasoningProcessor : SeasoningProcessor {
    override val type = "flavour"
    override fun apply(result: ItemStack, seasoning: List<ItemStack>) {
        var flavours = mutableMapOf<Flavour, Int>()
        for (seasoningStack in seasoning) {
            val seasoning = Seasonings.getFromItemStack(seasoningStack)
            seasoning?.flavours?.forEach { (flavour, value) ->
                val currentAmount = flavours[flavour] ?: 0
                flavours[flavour] = currentAmount + value
            }
        }
        result.set(CobblemonItemComponents.FLAVOUR, FlavourComponent(flavours))
    }
}