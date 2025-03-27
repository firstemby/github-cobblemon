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

            //Use custom jump bar logic if the current ride does not jump using it.
            if( !ride.getRiding().canJump(ride, player))
            {
                cir.setReturnValue(ride.setRideBar());
            }
        }
    }
}