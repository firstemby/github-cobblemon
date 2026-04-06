/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.fabric.mixin;


import com.cobblemon.mod.common.CobblemonRecipeBookCategories;
import net.minecraft.client.gui.screens.recipebook.SearchRecipeBookCategory;
import net.minecraft.world.item.crafting.RecipeBookCategory;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;


@Mixin(SearchRecipeBookCategory.class)
public class SearchRecipeBookCategoryMixin {

    @Final
    @Mutable
    @Shadow
    private static SearchRecipeBookCategory[] $VALUES;

    @Invoker("<init>")
    private static SearchRecipeBookCategory cobblemon$createCategory(String name, int ordinal, RecipeBookCategory... recipeBookCategorys) {
        throw new AssertionError();
    }

    @Inject(
            method = "<clinit>",
            at = @At("TAIL")
    )
    private static void cobblemon$addRecipeBookType(CallbackInfo ci) {
        ArrayList<SearchRecipeBookCategory> types = new ArrayList<>(List.of($VALUES));
        // if you change or add any of these, neoforge uses a separate mechanism
        // check enumextensions.json and `CobblemonEnumExtensions`
        types.add(cobblemon$createCategory("COBBLEMON_COOKING_POT_SEARCH", $VALUES.length,
                CobblemonRecipeBookCategories.INSTANCE.getCOOKING_POT_COMPLEX_DISHES(),
                CobblemonRecipeBookCategories.INSTANCE.getCOOKING_POT_FOODS(),
                CobblemonRecipeBookCategories.INSTANCE.getCOOKING_POT_MEDICINES(),
                CobblemonRecipeBookCategories.INSTANCE.getCOOKING_POT_MISC()));
        $VALUES = types.toArray(SearchRecipeBookCategory[]::new);
    }
}
