/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.integration.jei.brewing

import com.cobblemon.mod.common.integration.jei.CobblemonJeiProvider
import mezz.jei.api.constants.RecipeTypes
import mezz.jei.api.registration.IRecipeCatalystRegistration
import mezz.jei.api.registration.IRecipeCategoryRegistration
import mezz.jei.api.registration.IRecipeRegistration

class BrewingStandJeiProvider : CobblemonJeiProvider {
    override fun registerCategory(registration: IRecipeCategoryRegistration) {
    }

    override fun registerRecipes(registration: IRecipeRegistration) {
        // The synchronized client recipe view no longer exposes arbitrary custom recipe lists in 1.21.11.
        registration.addRecipes(RecipeTypes.BREWING, emptyList())
    }

    override fun registerRecipeCatalsysts(registration: IRecipeCatalystRegistration) { }
}
