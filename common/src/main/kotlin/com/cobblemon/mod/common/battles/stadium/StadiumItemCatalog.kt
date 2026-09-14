/*
 * Copyright (C) 2026 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.battles.stadium

import com.cobblemon.mod.common.pokemon.helditem.CobblemonHeldItemManager
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.ItemStack

object StadiumItemCatalog {
    fun collect(): Map<ResourceLocation, String> = BuiltInRegistries.ITEM.mapNotNull { item ->
        val id = CobblemonHeldItemManager.showdownId(ItemStack(item))?.takeIf { it.isNotBlank() && it.length <= 80 }
        id?.let { BuiltInRegistries.ITEM.getKey(item) to it }
    }.toMap()
}
