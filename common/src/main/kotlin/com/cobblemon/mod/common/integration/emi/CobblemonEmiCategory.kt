/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.integration.emi

import com.cobblemon.mod.common.integration.recipeviewer.RecipeViewerRecipe
import dev.emi.emi.api.recipe.EmiRecipeCategory
import dev.emi.emi.api.render.EmiTexture
import net.minecraft.network.chat.Component

class CobblemonEmiCategory<T: RecipeViewerRecipe>(val provider: CobblemonEmiProvider<T, *>): EmiRecipeCategory(provider.originalProvider.id,
    EmiTexture(provider.originalProvider.getIconResource(), 0, 0,
        provider.originalProvider.getIconDimensions().x,
        provider.originalProvider.getIconDimensions().y,
        provider.originalProvider.getIconDimensions().x,
        provider.originalProvider.getIconDimensions().y,
        provider.originalProvider.getIconDimensions().x,
        provider.originalProvider.getIconDimensions().y
    )
) {
    override fun getName(): Component = provider.originalProvider.getName()
}