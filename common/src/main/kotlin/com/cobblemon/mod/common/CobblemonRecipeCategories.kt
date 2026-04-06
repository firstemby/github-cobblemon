/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common

import com.cobblemon.mod.common.platform.PlatformRegistry
import net.minecraft.client.gui.screens.recipebook.SearchRecipeBookCategory
import net.minecraft.core.Registry
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceKey
import net.minecraft.world.inventory.RecipeBookType
import net.minecraft.world.item.crafting.ExtendedRecipeBookCategory
import net.minecraft.world.item.crafting.Recipe
import net.minecraft.world.item.crafting.RecipeBookCategories
import net.minecraft.world.item.crafting.RecipeBookCategory
import net.minecraft.world.item.crafting.RecipeType

object CobblemonRecipeBookCategories : PlatformRegistry<Registry<RecipeBookCategory>, ResourceKey<Registry<RecipeBookCategory>>, RecipeBookCategory>() {

    val COOKING_POT_SEARCH = object : ExtendedRecipeBookCategory {}
    val COOKING_POT_FOODS = register("cobblemon:cooking_pot_foods")
    val COOKING_POT_MEDICINES = register("cobblemon:cooking_pot_medicines")
    val COOKING_POT_COMPLEX_DISHES = register("cobblemon:cooking_pot_complex_dishes")
    val COOKING_POT_MISC = register("cobblemon:cooking_pot_misc")

    fun register(identifier: String): RecipeBookCategory {
        return Registry.register(
            BuiltInRegistries.RECIPE_BOOK_CATEGORY,
            identifier,
            RecipeBookCategory()
        )
    }

    override val registry: Registry<RecipeBookCategory>
        get() = BuiltInRegistries.RECIPE_BOOK_CATEGORY
    override val resourceKey: ResourceKey<Registry<RecipeBookCategory>>
        get() = Registries.RECIPE_BOOK_CATEGORY
}

object CobblemonRecipeBookTypes {
    const val COOKING_POT_NAME = "COBBLEMON_COOKING_POT"
    val COOKING_POT: RecipeBookType = RecipeBookType.valueOf(COOKING_POT_NAME)
}
