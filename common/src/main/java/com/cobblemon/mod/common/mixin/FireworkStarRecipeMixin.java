/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.mixin;

import com.cobblemon.mod.common.CobblemonItems;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.FireworkExplosion;
import net.minecraft.world.item.crafting.FireworkStarRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.ItemLike;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Arrays;
import java.util.Map;

@Mixin(FireworkStarRecipe.class)
public class FireworkStarRecipeMixin {

    @Mutable
    @Shadow
    @Final
    private static Map<Item, FireworkExplosion.Shape> SHAPE_BY_ITEM;

    @Mutable
    @Shadow
    @Final
    private static Ingredient SHAPE_INGREDIENT;

    @Inject(method = "<clinit>", at = @At("TAIL"))
    private static void cobblemon$addCustomShape(CallbackInfo ci) {
        ItemStack[] existing = SHAPE_INGREDIENT.getItems();
        ItemStack[] extended = new ItemStack[existing.length + 1];
        System.arraycopy(existing, 0, extended, 0, existing.length);

        extended[existing.length] = new ItemStack(CobblemonItems.POKE_BALL);

        SHAPE_INGREDIENT = Ingredient.of(Arrays.stream(extended)
            .map(ItemStack::getItem)
            .toArray(ItemLike[]::new));
        SHAPE_BY_ITEM.put(CobblemonItems.POKE_BALL, FireworkExplosion.Shape.byId(5));
    }
}
