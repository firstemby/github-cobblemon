/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.mixin.entity;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.level.gamerules.GameRule;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TamableAnimal.class)
public class TameableEntityMixin {

    @Inject(method = "die", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/TamableAnimal;getOwner()Lnet/minecraft/world/entity/LivingEntity;"), cancellable = true)
    public void cobblemon$checkIfPokemonBeforeSendingMessage(DamageSource damageSource, CallbackInfo ci, @Local(name = "var4") LivingEntity owner) {
        if(((TamableAnimal)(Object)this).level().isClientSide() || (this.getClass().isAssignableFrom(PokemonEntity.class))) {
            ci.cancel();
        }
    }

}
