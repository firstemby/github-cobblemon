/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.mixin;

import com.cobblemon.mod.common.CobblemonRecipePropertySets;
import com.cobblemon.mod.common.item.crafting.CookingPotRecipeBase;
import com.cobblemon.mod.common.item.crafting.brewingstand.BrewingStandRecipe;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipePropertySet;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Mixin(RecipeManager.class)
public abstract class RecipeManagerMixin {

    @Shadow
    @Final
    private static Map<ResourceKey<RecipePropertySet>, RecipeManager.IngredientExtractor> RECIPE_PROPERTY_SETS;
    
    @WrapOperation(method = "<clinit>", at = @At(value = "INVOKE", target = "Ljava/util/Map;of(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)Ljava/util/Map;"))
    private static Map poggers(Object k1, Object v1,
                               Object k2, Object v2,
                               Object k3, Object v3,
                               Object k4, Object v4,
                               Object k5, Object v5,
                               Object k6, Object v6,
                               Object k7, Object v7,
                               Operation<Map> original) {
        return new HashMap(original.call(k1,v1,k2,v2,k3,v3,k4,v4,k5,v5,k6,v6,k7,v7));
    }

    @Inject(method = "<clinit>", at = @At("TAIL"))
    private static void cobblemon$addPropertySets(CallbackInfo ci) {
        RECIPE_PROPERTY_SETS.put(CobblemonRecipePropertySets.INSTANCE.getBREWING_BOTTLE(), recipe -> {
            if (recipe instanceof BrewingStandRecipe brew) {
                return Optional.of(brew.getBottle());
            }
            return Optional.empty();
        });

        RECIPE_PROPERTY_SETS.put(CobblemonRecipePropertySets.INSTANCE.getBREWING_INPUT(), recipe -> {
            if (recipe instanceof BrewingStandRecipe brew) {
                return Optional.of(brew.getInput());
            }
            return Optional.empty();
        });

        RECIPE_PROPERTY_SETS.put(CobblemonRecipePropertySets.INSTANCE.getCOOKING_POT_FLAVOUR_PROCESSOR(), recipe -> {
            if (recipe instanceof CookingPotRecipeBase cooking && cooking.getSeasoningProcessors().stream().anyMatch(x -> x.getType().equals("flavour"))) {
                return Optional.of(Ingredient.of(cooking.getResult().item().value()));
            }
            return Optional.empty();
        });

        RECIPE_PROPERTY_SETS.put(CobblemonRecipePropertySets.INSTANCE.getCOOKING_POT_FOOD_PROCESSOR(), recipe -> {
            if (recipe instanceof CookingPotRecipeBase cooking && cooking.getSeasoningProcessors().stream().anyMatch(x -> x.getType().equals("food"))) {
                return Optional.of(Ingredient.of(cooking.getResult().item().value()));
            }
            return Optional.empty();
        });

        RECIPE_PROPERTY_SETS.put(CobblemonRecipePropertySets.INSTANCE.getCOOKING_POT_MOB_EFFECTS_PROCESSOR(), recipe -> {
            if (recipe instanceof CookingPotRecipeBase cooking && cooking.getSeasoningProcessors().stream().anyMatch(x -> x.getType().equals("mob_effects"))) {
                return Optional.of(Ingredient.of(cooking.getResult().item().value()));
            }
            return Optional.empty();
        });

        RECIPE_PROPERTY_SETS.put(CobblemonRecipePropertySets.INSTANCE.getCOOKING_POT_SPAWN_BAIT_PROCESSOR(), recipe -> {
            if (recipe instanceof CookingPotRecipeBase cooking && cooking.getSeasoningProcessors().stream().anyMatch(x -> x.getType().equals("spawn_bait"))) {
                return Optional.of(Ingredient.of(cooking.getResult().item().value()));
            }
            return Optional.empty();
        });
    }
}
