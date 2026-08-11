/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.integration.recipeviewer

import net.minecraft.world.item.ItemStack

data class RecipeSlot(
    val x: Int,
    val y: Int,
    val type: SlotType,
    val ingredient: RecipeIngredient,
    val watchIngredient: Boolean = false,
    val generatedStack: ((List<ItemStack>) -> ItemStack)? = null
)

enum class SlotType {
    INPUT,
    OUTPUT,
    CATALYST
}
