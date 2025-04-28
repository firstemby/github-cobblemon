/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.mixin;

import com.cobblemon.mod.common.OrientationControllable;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayer.class)
public abstract class ServerPlayerMixin {

    @Inject(method = "rideTick", at = @At("HEAD"))
    private void cobblemon$updateOrientationControllerRideTick(CallbackInfo ci) {
        if (!(this instanceof OrientationControllable controllable)) return;
        var shouldUseCustomOrientation = cobblemon$shouldUseCustomOrientation((ServerPlayer)(Object)this);
        controllable.getOrientationController().setActive(shouldUseCustomOrientation);
    }

    @Unique
    private boolean cobblemon$shouldUseCustomOrientation(ServerPlayer player) {
        var playerVehicle = player.getVehicle();
        if (playerVehicle == null) return false;
        if (!(playerVehicle instanceof PokemonEntity pokemonEntity)) return false;
        return pokemonEntity.ifRidingAvailableSupply(false, (behaviour, settings, state) -> {
            return behaviour.shouldRoll(settings, state, pokemonEntity);
        });
    }

    @Inject(method = "stopRiding", at = @At("HEAD"))
    public void cobblemon$resetOrientationOnDismount(CallbackInfo ci) {
        if (!(this instanceof OrientationControllable controllable)) return;
        controllable.getOrientationController().setActive(false);
    }

}
