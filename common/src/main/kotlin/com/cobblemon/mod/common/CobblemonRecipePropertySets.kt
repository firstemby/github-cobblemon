/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common

import com.cobblemon.mod.common.util.cobblemonResource
import net.minecraft.resources.ResourceKey
import net.minecraft.world.item.crafting.RecipePropertySet

object CobblemonRecipePropertySets {
    val BREWING_INPUT = ResourceKey.create(RecipePropertySet.TYPE_KEY, cobblemonResource("brewing_input"))
    val BREWING_BOTTLE = ResourceKey.create(RecipePropertySet.TYPE_KEY, cobblemonResource("brewing_bottle"))

    val COOKING_POT_FOOD_PROCESSOR = ResourceKey.create(RecipePropertySet.TYPE_KEY, cobblemonResource("cooking_pot_food_processor"))
    val COOKING_POT_FLAVOUR_PROCESSOR = ResourceKey.create(RecipePropertySet.TYPE_KEY, cobblemonResource("cooking_pot_flavour_processor"))
    val COOKING_POT_MOB_EFFECTS_PROCESSOR = ResourceKey.create(RecipePropertySet.TYPE_KEY, cobblemonResource("cooking_pot_mob_effects_processor"))
    val COOKING_POT_SPAWN_BAIT_PROCESSOR = ResourceKey.create(RecipePropertySet.TYPE_KEY, cobblemonResource("cooking_pot_spawn_bait_processor"))

}