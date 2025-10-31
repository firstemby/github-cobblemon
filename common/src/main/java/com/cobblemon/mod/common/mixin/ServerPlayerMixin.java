/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.mixin;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.OrientationControllable;
import com.cobblemon.mod.common.PlayerSpawnerGetter;
import com.cobblemon.mod.common.api.spawning.spawner.PlayerSpawner;
import com.cobblemon.mod.common.api.spawning.spawner.PlayerSpawnerFactory;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.world.gamerules.CobblemonGameRules;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayer.class)
public abstract class ServerPlayerMixin implements PlayerSpawnerGetter {
    @Shadow
    public abstract ServerLevel serverLevel();

    @Unique
    public PlayerSpawner cobblemon$spawner;

    @Override
    public PlayerSpawner getPlayerSpawner() {
        ServerPlayer player = (ServerPlayer)(Object)this;
        if (cobblemon$spawner == null) {
            cobblemon$spawner = PlayerSpawnerFactory.INSTANCE.create(player);
        }
        return cobblemon$spawner;
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void cobblemon$tickSpawner(CallbackInfo ci) {
        if (!Cobblemon.config.getEnableSpawning()) {
            return;
        } else if (!serverLevel().getGameRules().getBoolean(CobblemonGameRules.DO_POKEMON_SPAWNING)) {
            return;
        }
        getPlayerSpawner().tick();
    }

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

    //TODO: Switch to sending out on load instead of preventing saving
    @ModifyExpressionValue(method = "addAdditionalSaveData", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;hasExactlyOnePlayerPassenger()Z"))
    private boolean cobblemon$cancelSavingPokemonMounts(boolean original, @Local(ordinal=0) Entity entity) {
        return original && !(entity instanceof PokemonEntity);
    }
}
