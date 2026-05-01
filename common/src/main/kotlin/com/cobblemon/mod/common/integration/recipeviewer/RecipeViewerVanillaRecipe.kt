package com.cobblemon.mod.common.integration.recipeviewer

import mezz.jei.api.recipe.RecipeType
import net.minecraft.world.item.crafting.Recipe

data class RecipeViewerVanillaRecipe<T: Recipe<*>>(val recipe: T)