/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.mixin;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.world.gamerules.CobblemonGameRules;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(TargetingConditions.class)
public class TargetingConditionsMixin {

    @Inject(method = "test", at = @At("HEAD"), cancellable = true)
    public void test(ServerLevel serverLevel, LivingEntity baseEntity, LivingEntity targetEntity, CallbackInfoReturnable<Boolean> cir) {
        if (targetEntity instanceof ServerPlayer player) {
            if (serverLevel.getGameRules().get(CobblemonGameRules.MOB_TARGET_IN_BATTLE)) return;
            boolean targetInBattle = Cobblemon.INSTANCE.getBattleRegistry().getBattleByParticipatingPlayer(player) != null;
            if (targetInBattle) cir.setReturnValue(false);
        }
    }
}
