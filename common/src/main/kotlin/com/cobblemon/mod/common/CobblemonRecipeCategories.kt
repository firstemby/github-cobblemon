/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common

import net.minecraft.client.RecipeBookCategories

enum class CobblemonRecipeCategories {

    COOKING_POT_SEARCH("COOKING_POT_SEARCH"),
    COOKING_POT_FOODS("COOKING_POT_FOODS"),
    COOKING_POT_MEDICINES("COOKING_POT_MEDICINES"),
    COOKING_POT_COMPLEX_DISHES("COOKING_POT_COMPLEX_DISHES"),
    COOKING_POT_MISC("COOKING_POT_MISC");

    companion object {
        val customAggregateCategories: Map<RecipeBookCategories, List<RecipeBookCategories>> = mapOf(
            COOKING_POT_SEARCH.toVanillaCategory() to listOf(
                COOKING_POT_FOODS.toVanillaCategory(), COOKING_POT_MISC.toVanillaCategory(), COOKING_POT_MEDICINES.toVanillaCategory(), COOKING_POT_COMPLEX_DISHES.toVanillaCategory()
            )
        )
    }

    var id: String

    constructor(id: String) {
        this.id = id
    }

    fun toVanillaCategory(): RecipeBookCategories {
        return RecipeBookCategories.valueOf(this.id)
    }


}