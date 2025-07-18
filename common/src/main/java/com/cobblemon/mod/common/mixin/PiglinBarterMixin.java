/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.mixin;

import com.cobblemon.mod.common.CobblemonItems;
import com.cobblemon.mod.common.CobblemonMemories;
import com.cobblemon.mod.common.entity.ai.PiglinDanceTarget;
import com.google.common.collect.ImmutableList;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.monster.piglin.*;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.*;


@Mixin(PiglinAi.class)
public class PiglinBarterMixin {

    @Inject(method = "isBarterCurrency", at = @At(value = "RETURN"), cancellable = true)
    private static void cobblemon$acceptsForBarter(ItemStack stack, CallbackInfoReturnable<Boolean> ci) {
        if (!ci.getReturnValue()) {
            //TODO: Make a tag?
            if (stack.is(CobblemonItems.RELIC_COIN_POUCH))
                ci.setReturnValue(true);
        }
    }

    @Inject(method = "makeBrain", at = @At("TAIL"), cancellable = true)
    private static void cobblemon$makeBrain(Piglin piglin, Brain<Piglin> brain, CallbackInfoReturnable<Brain<?>> cir) {
        // Get access to private task map
        brain.addActivityAndRemoveMemoryWhenStopped(Activity.INVESTIGATE, 10,
                ImmutableList.of(
                        new PiglinDanceTarget()
                ), CobblemonMemories.INSTANCE.getNEAREST_LOVED_ENTITY());
    }

    @Redirect(
            method = "updateActivity(Lnet/minecraft/world/entity/monster/piglin/Piglin;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/ai/Brain;setActiveActivityToFirstValid(Ljava/util/List;)V"
            )
    )
    private static void redirectSetActiveActivityToFirstValid(
            Brain<Piglin> brain,
            List<Activity> originalList
    ) {
        // Add new activity to the front
        List<Activity> newList = new ArrayList<>(List.of(Activity.INVESTIGATE));
        newList.addAll(originalList);

        // Call original method with modified list
        brain.setActiveActivityToFirstValid(newList);
    }
}
