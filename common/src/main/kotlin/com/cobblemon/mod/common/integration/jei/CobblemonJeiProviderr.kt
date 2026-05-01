package com.cobblemon.mod.common.integration.jei

import com.cobblemon.mod.common.integration.recipeviewer.RecipeViewerCategoryProvider
import com.cobblemon.mod.common.integration.recipeviewer.RecipeViewerRecipe
import mezz.jei.api.recipe.RecipeType
import mezz.jei.api.registration.IRecipeCatalystRegistration
import mezz.jei.api.registration.IRecipeCategoryRegistration
import mezz.jei.api.registration.IRecipeRegistration

class CobblemonJeiProviderr<T: RecipeViewerRecipe>(val originalProvider: RecipeViewerCategoryProvider<T>) {
    val recipeType: RecipeType<T> =
        RecipeType<T>.create(originalProvider.id.namespace, originalProvider.id.path, originalProvider.recipeClass)

    fun registerCategory(registration: IRecipeCategoryRegistration) {
        registration.addRecipeCategories(CobblemonJeiCategory(registration, this))
    }

    fun registerRecipes(registration: IRecipeRegistration) {
        registration.addRecipes(recipeType, originalProvider.getRecipes())
    }

    fun registerRecipeCatalsysts(registration: IRecipeCatalystRegistration) {
        originalProvider.getWorkstations().forEach {
            registration.addRecipeCatalyst(it, recipeType)
        }
    }
}