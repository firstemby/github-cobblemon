/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.mixin;

import net.minecraft.client.player.LocalPlayer;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LocalPlayer.class)
public abstract class LocalPlayerMixin {
    @Shadow
    private float jumpRidingScale;

    @Inject(method = "getJumpRidingScale", at = @At("HEAD"), cancellable = true)
    public void modifyJumpRidingScale(CallbackInfoReturnable<Float> cir) {

        LocalPlayer player = (LocalPlayer) (Object) this;
        if (player.isPassenger() && player.getVehicle() instanceof PokemonEntity) {
            PokemonEntity ride = (PokemonEntity) player.getVehicle();
            //ride.getRiding().canJump()
            if( !ride.getRiding().canJump(ride, player))
            {
                cir.setReturnValue(0.5f);
            }
        }


        // Check if the player is riding your custom entity (for example, a Pokemon)
        /*
        if (player.isRiding() && player.getVehicle() instanceof PokemonEntity) {
            PokemonEntity rideable = (PokemonEntity) player.getVehicle();
            // Get your custom fuel ratio (a value from 0.0 to 1.0)
            float fuelRatio = rideable.getRidingFuelRatio();
            cir.setReturnValue(fuelRatio);
        }
        */
    }
}