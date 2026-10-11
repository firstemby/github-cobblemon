/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.integration.recipeviewer

import net.minecraft.world.item.crafting.Ingredient

// Whole existence due to the fact we can have recipes with multiple count requirement
data class RecipeIngredient(
    val ingredient: Ingredient,
    val count: Int = 1
)