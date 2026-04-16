/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.mixin;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.CobblemonItems;
import com.cobblemon.mod.common.api.events.CobblemonEvents;
import com.cobblemon.mod.common.api.events.item.LeftoversCreatedEvent;
import com.cobblemon.mod.common.api.tags.CobblemonItemTags;
import kotlin.Unit;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.Consumable;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FoodProperties.class)
public class FoodPropertiesMixin {
    @Inject(method = "onConsume", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/food/FoodData;eat(Lnet/minecraft/world/food/FoodProperties;)V"))
    public void dropLeftOvers(Level level, LivingEntity user, ItemStack stack, Consumable consumable, CallbackInfo ci) {
        if (!level.isClientSide()) {
            if (stack.is(CobblemonItemTags.LEAVES_LEFTOVERS) && level.getRandom().nextDouble() < Cobblemon.config.getAppleLeftoversChance()) {
                ItemStack leftovers = new ItemStack(CobblemonItems.LEFTOVERS);
                CobblemonEvents.LEFTOVERS_CREATED.postThen(
                        new LeftoversCreatedEvent(user, leftovers),
                        _ -> Unit.INSTANCE,
                        leftoversCreatedEvent -> {
                            user.handleExtraItemsCreatedOnUse(leftoversCreatedEvent.getLeftovers());
                            return Unit.INSTANCE;
                        }
                );
            }
        }
    }
}
