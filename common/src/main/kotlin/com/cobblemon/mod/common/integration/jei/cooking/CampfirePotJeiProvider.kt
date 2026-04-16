/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.integration.jei.cooking

import com.cobblemon.mod.common.CobblemonItems
import com.cobblemon.mod.common.integration.jei.CobblemonJeiProvider
import mezz.jei.api.registration.IRecipeCatalystRegistration
import mezz.jei.api.registration.IRecipeCategoryRegistration
import mezz.jei.api.registration.IRecipeRegistration

class CampfirePotJeiProvider : CobblemonJeiProvider {
    override fun registerCategory(registration: IRecipeCategoryRegistration) {
        registration.addRecipeCategories(CampfirePotRecipeCategory(registration))
    }

    override fun registerRecipes(registration: IRecipeRegistration) {
        // The client-side recipe sync no longer exposes custom recipe collections directly in 1.21.11.
        registration.addRecipes(CampfirePotRecipeCategory.RECIPE_TYPE, emptyList())
    }

    override fun registerRecipeCatalsysts(registration: IRecipeCatalystRegistration) {
        CobblemonItems.campfire_pots.forEach {
            registration.addCraftingStation(CampfirePotRecipeCategory.RECIPE_TYPE, it)
        }
    }
}
