package com.cobblemon.mod.common.integration.emi

import com.cobblemon.mod.common.integration.recipeviewer.RecipeViewerRecipe
import dev.emi.emi.api.recipe.EmiRecipeCategory
import dev.emi.emi.api.render.EmiRenderable
import dev.emi.emi.api.render.EmiTexture
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation

class CobblemonEmiCategory<T: RecipeViewerRecipe>(val provider: CobblemonEmiProvider<T>): EmiRecipeCategory(provider.originalProvider.id,
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