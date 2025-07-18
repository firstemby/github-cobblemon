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
import com.mojang.datafixers.util.Pair;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.behavior.*;
import net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.piglin.*;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
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
        System.out.println("Redirect");
        // Copy and modify the list
        List<Activity> newList = new ArrayList<>(originalList);
        newList.addFirst(Activity.INVESTIGATE);

        // Call original method with modified list
        brain.setActiveActivityToFirstValid(newList);
    }


//    /**
//     * Overwrites the `someMethod` in `TargetClass` to change its behavior.
//     * This is necessary to fix a bug in block placement logic.
//     */
//    @Overwrite
//    public static void updateActivity(Piglin piglin) {
//        Brain<Piglin> brain = piglin.getBrain();
//        Activity activity = (Activity)brain.getActiveNonCoreActivity().orElse((Activity) null);
//        brain.setActiveActivityToFirstValid(ImmutableList.of(Activity.INVESTIGATE, Activity.ADMIRE_ITEM, Activity.FIGHT, Activity.AVOID, Activity.CELEBRATE, Activity.RIDE, Activity.IDLE));
//        Activity activity2 = (Activity)brain.getActiveNonCoreActivity().orElse((Activity) null);
//        if (activity != activity2) {
//            Optional<SoundEvent> var10000 = PiglinAi.getSoundForCurrentActivity(piglin);
//            Objects.requireNonNull(piglin);
//            var10000.ifPresent(piglin::makeSound);
//        }
//
//        piglin.setAggressive(brain.hasMemoryValue(MemoryModuleType.ATTACK_TARGET));
////        if (!brain.hasMemoryValue(MemoryModuleType.RIDE_TARGET) && PiglinAi.isBabyRidingBaby(piglin)) {
////            piglin.stopRiding();
////        }
//
//        if (!brain.hasMemoryValue(MemoryModuleType.CELEBRATE_LOCATION)) {
//            brain.eraseMemory(MemoryModuleType.DANCING);
//        }
//
//        piglin.setDancing(brain.hasMemoryValue(MemoryModuleType.DANCING));
//    }
}
