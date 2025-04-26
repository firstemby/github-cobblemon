/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.mixin;

import net.minecraft.world.inventory.RecipeBookType;
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

@Mixin(value = RecipeBookType.class)
public class RecipeBookTypeMixin {

    @Final
    @Mutable
    @Shadow
    private static RecipeBookType[] $VALUES; // moj

    @Invoker("<init>")
    private static RecipeBookType cobblemon$createBookType(String name, int ordinal) {
        throw new AssertionError();
    }

    @Inject(
            method = "<clinit>",
            at = @At("TAIL")
    )
    private static void cobblemon$addRecipeBookType(CallbackInfo ci) {
        ArrayList<RecipeBookType> types = new ArrayList<>(List.of($VALUES));
        types.add(cobblemon$createBookType("COOKING_POT", $VALUES.length));
        $VALUES = types.toArray(RecipeBookType[]::new);
    }
}
