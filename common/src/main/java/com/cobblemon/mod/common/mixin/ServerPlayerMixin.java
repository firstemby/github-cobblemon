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
import com.cobblemon.mod.common.PlayerSpawnerAccessor;
import com.cobblemon.mod.common.api.riding.RidingStyle;
import com.cobblemon.mod.common.api.spawning.spawner.PlayerSpawner;
import com.cobblemon.mod.common.api.spawning.spawner.PlayerSpawnerFactory;
import com.cobblemon.mod.common.api.stats.CobblemonStats;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.util.CompoundTagUtilities;
import com.cobblemon.mod.common.util.ShoulderEntitySynchronizer;
import com.cobblemon.mod.common.world.gamerules.CobblemonGameRules;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayer.class)
public abstract class ServerPlayerMixin implements PlayerSpawnerAccessor {

    @Shadow public abstract CompoundTag getShoulderEntityLeft();

    @Shadow public abstract CompoundTag getShoulderEntityRight();

    @Shadow public abstract void respawnEntityOnShoulder(CompoundTag entityNbt);

    @Shadow public abstract void setShoulderEntityRight(CompoundTag entityNbt);

    @Shadow public abstract void setShoulderEntityLeft(CompoundTag entityNbt);

    @Unique
    public PlayerSpawner cobblemon$spawner;

    @Shadow
    private long timeEntitySatOnShoulder;

    @Override
    public PlayerSpawner getPlayerSpawner() {
        ServerPlayer player = (ServerPlayer)(Object)this;
        if (cobblemon$spawner == null) {
            cobblemon$spawner = PlayerSpawnerFactory.INSTANCE.create(player);
        }
        return cobblemon$spawner;
    }

    @Override
    public void setPlayerSpawner(PlayerSpawner spawner) {
        this.cobblemon$spawner = spawner;
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void cobblemon$tickSpawner(CallbackInfo ci) {
        ServerPlayer player = (ServerPlayer)(Object)this;
        if (!Cobblemon.config.getEnableSpawning()) {
            return;
        } else if (!player.level().getGameRules().get(CobblemonGameRules.DO_POKEMON_SPAWNING)) {
            return;
        }
        getPlayerSpawner().tick();
    }

    @Inject(method = "removeEntitiesOnShoulder", at = @At("HEAD"), cancellable = true)
    private void cobblemon$preventPokemonDropping(CallbackInfo ci) {
        ServerPlayer player = (ServerPlayer)(Object)this;
        if (player.isSpectator() || player.isDeadOrDying()) {
            return;
        }

        long gameTime = player.level().getGameTime();
        if (this.timeEntitySatOnShoulder + 20L >= gameTime) {
            return;
        }

        boolean changed = false;
        CompoundTag leftShoulder = player.getShoulderEntityLeft();
        if (!leftShoulder.isEmpty() && !CompoundTagUtilities.isShoulderPokemon(leftShoulder)) {
            respawnEntityOnShoulder(leftShoulder);
            setShoulderEntityLeft(new CompoundTag());
            changed = true;
        }

        CompoundTag rightShoulder = player.getShoulderEntityRight();
        if (!rightShoulder.isEmpty() && !CompoundTagUtilities.isShoulderPokemon(rightShoulder)) {
            respawnEntityOnShoulder(rightShoulder);
            setShoulderEntityRight(new CompoundTag());
            changed = true;
        }

        this.timeEntitySatOnShoulder = gameTime;
        if (changed) {
            ShoulderEntitySynchronizer.sync(player);
        }
        ci.cancel();
    }

    @Inject(method = "rideTick", at = @At("HEAD"))
    private void cobblemon$updateOrientationControllerRideTick(CallbackInfo ci) {
        ServerPlayer player = (ServerPlayer)(Object)this;
        Entity vehicle = player.getVehicle();
        if (!(vehicle instanceof OrientationControllable controllableVehicle)) return;
        var shouldUseCustomOrientation = cobblemon$shouldUseCustomOrientation(vehicle);
        var controller = controllableVehicle.getOrientationController();
        controller.setActive(shouldUseCustomOrientation);
        if (shouldUseCustomOrientation && controller.getOrientation() == null) {
            controller.updateOrientation(matrix -> matrix);
        }
    }

    @Unique
    private boolean cobblemon$shouldUseCustomOrientation(Entity entity) {
        if (entity == null) return false;
        if (!(entity instanceof PokemonEntity pokemonEntity)) return false;
        return pokemonEntity.ifRidingAvailableSupply(false, (behaviour, settings, state) -> {
            return behaviour.shouldRoll(settings, state, pokemonEntity);
        });
    }

    //TODO: Switch to sending out on load instead of preventing saving
    @ModifyExpressionValue(method = "saveParentVehicle", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;hasExactlyOnePlayerPassenger()Z"))
    private boolean cobblemon$cancelSavingPokemonMounts(boolean original, @Local(name = "rootVehicle") Entity entity) {
        return original && !(entity instanceof PokemonEntity);
    }

    @WrapOperation(method = "checkRidingStatistics", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerPlayer;getVehicle()Lnet/minecraft/world/entity/Entity;"))
    public Entity cobblemon$checkRidingStatistics(ServerPlayer player, Operation<Entity> original, @Local int distance) {
        var entity = original.call(player);
        if (!(entity instanceof PokemonEntity pokemonEntity)) {
            return entity;
        }

        var ridingController = pokemonEntity.getRidingController();
        if (ridingController == null) return entity;

        var activeContext = ridingController.getContext();
        if (activeContext == null) return entity;

        var ridingStyle = activeContext.getStyle();
        CobblemonStats.CobblemonStat stat = switch (ridingStyle) {
            case RidingStyle.LAND -> CobblemonStats.RIDING_LAND;
            case RidingStyle.AIR -> CobblemonStats.RIDING_AIR;
            case RidingStyle.LIQUID -> CobblemonStats.RIDING_LIQUID;
            default -> null;
        };

        if (stat != null) {
            player.awardStat(CobblemonStats.getStat(stat), distance);
        }

        return entity;
    }
}
