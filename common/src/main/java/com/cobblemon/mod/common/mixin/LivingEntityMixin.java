/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.mixin;

import com.cobblemon.mod.common.api.pokemon.effect.ShoulderEffectRegistry;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {

    @Inject(method = "onEffectRemoved", at = @At(value = "TAIL"))
    private void cobblemon$onEffectRemoved(MobEffectInstance effect, CallbackInfo ci) {
        final LivingEntity entity = (LivingEntity) (Object) this;
        if (entity instanceof ServerPlayer) {
            ShoulderEffectRegistry.INSTANCE.onEffectEnd((ServerPlayer) entity);
        }
    }

    /*
    @Inject(method = "travelRidden", at = @At(value = "HEAD"))
    private void cobblemon$altTravelRidden(Player player, Vec3 travelVector)
    {
        this.setSpeed(this.getRiddenSpeed(player));
        this.travel(vec3);
    }

    /*
    // Shadow the original method so we can call it.
    @Shadow
    protected abstract boolean isControlledByLocalInstance();

    /**
     * Redirect the call to isControlledByLocalInstance() within travelRidden.
     * When travelRidden calls isControlledByLocalInstance(), it will get our modified result.
     */

    /*
    @Redirect(
            method = "travelRidden(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/phys/Vec3;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/LivingEntity;isControlledByLocalInstance()Z"
            )
    )
    private boolean redirectIsControlledByLocalInstance(LivingEntity instance) {
        // Return true if either the original method returns true or our custom condition is true.
        return this.isControlledByLocalInstance() || this.b;
    }



    @Overwrite
    private void travelRidden(Player player, Vec3 travelVector) {
        Vec3 vec3 = this.getRiddenInput(player, travelVector);
        this.tickRidden(player, vec3);
        if (this.isControlledByLocalInstance()) {
            this.setSpeed(this.getRiddenSpeed(player));
            this.travel(vec3);
        } else {
            this.calculateEntityAnimation(false);
            this.setDeltaMovement(Vec3.ZERO);
            this.tryCheckInsideBlocks();
        }
    }


    @ModifyArg(
            method = "travelRidden",
            at = @At(
                    value = "INVOKE",
                    target = "setDeltaMovement(Lnet/minecraft/world/phys/Vec3;)V"
            ),
            index = 0 // the first (and only) argument
    )
    private Vec3 modifyDeltaMovementArgument(Vec3 original) {
        // Optionally, check if original is Vec3.ZERO, to only replace that call:
        if (original.equals(Vec3.ZERO)) {
            // Return your custom vector instead of Vec3.ZERO
            return original;
        }
        // Otherwise, return the original value
        return original;
    }
     */
}
