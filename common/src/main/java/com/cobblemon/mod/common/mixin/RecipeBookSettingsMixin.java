/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.mixin;

import com.cobblemon.mod.common.CobblemonRecipeBookTypes;
import net.minecraft.stats.RecipeBookSettings;
import net.minecraft.world.inventory.RecipeBookType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.function.UnaryOperator;

@Mixin(RecipeBookSettings.class)
public class RecipeBookSettingsMixin {
    @Unique
    private RecipeBookSettings.TypeSettings cobblemon$cookingPot = new RecipeBookSettings.TypeSettings(false, false);

    @Inject(method = "getSettings", at = @At("HEAD"), cancellable = true)
    public void getSettings(RecipeBookType type, CallbackInfoReturnable<RecipeBookSettings.TypeSettings> cir) {
        if (type == CobblemonRecipeBookTypes.INSTANCE.getCOOKING_POT()) {
            cir.setReturnValue(cobblemon$cookingPot);
        }
    }

    @Inject(method = "updateSettings", at = @At("HEAD"), cancellable = true)
    private void updateSetting(RecipeBookType recipeBookType, UnaryOperator<RecipeBookSettings.TypeSettings> operator, CallbackInfo ci) {
        if (recipeBookType == CobblemonRecipeBookTypes.INSTANCE.getCOOKING_POT()) {
            cobblemon$cookingPot = operator.apply(cobblemon$cookingPot);
            ci.cancel();
        }
    }
}
