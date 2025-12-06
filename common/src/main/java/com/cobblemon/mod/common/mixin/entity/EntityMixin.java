/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.mixin.entity;

import com.cobblemon.mod.common.OrientationControllable;
import com.cobblemon.mod.common.duck.RidePassenger;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public abstract class EntityMixin {

    @Shadow @Nullable public abstract Entity getVehicle();

    @Inject(method = "updateInWaterStateAndDoWaterCurrentPushing", at = @At(value = "FIELD", target = "Lnet/minecraft/world/entity/Entity;wasTouchingWater:Z", ordinal = 1), cancellable = true)
    public void cobblemon$verifyActuallyTouchingWater(CallbackInfo ci) {
        if(this.getVehicle() instanceof PokemonEntity) {
            ci.cancel();
        }
    }

    @WrapOperation(
            method = "collide(Lnet/minecraft/world/phys/Vec3;)Lnet/minecraft/world/phys/Vec3;",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/Entity;onGround()Z"
            )
    )
    public boolean cobblemon$forceOnGroundForStepUp(Entity entity, Operation<Boolean> original) {
        if (entity instanceof PokemonEntity && entity.hasControllingPassenger()) {
            BlockPos below = entity.blockPosition().below();
            Level level = entity.level();
            var blockStateBelow = level.getBlockState(below);
            boolean isAirOrLiquid = blockStateBelow.isAir() || !blockStateBelow.getFluidState().isEmpty();
            boolean canSupportEntity = blockStateBelow.isFaceSturdy(level, below, Direction.UP);
            boolean standingOnSolid = canSupportEntity && !isAirOrLiquid;
            if (standingOnSolid) {
                return true;
            }
        }
        return original.call(entity);
    }

    @Inject(
            method = "getEyePosition(F)Lnet/minecraft/world/phys/Vec3;",
            at = @At("HEAD"),
            cancellable = true
    )
    private void cobblemon$modifyEyePosition_partial(float partialTicks, CallbackInfoReturnable<Vec3> cir) {
        var entity = (Entity)(Object)this;
        if (entity.level().isClientSide) return;
        if (!(entity instanceof Player player)) return;
        if (!(player instanceof RidePassenger ridePassenger)) return;
        if (!(player.getVehicle() instanceof OrientationControllable vehicle)) return;
        var vehicleController = vehicle.getOrientationController();
        if (vehicleController == null) return;


        // TODO: Determine if it needs partialtick involved.
        Vec3 customEyePos = ridePassenger.cobblemon$getRideEyePos();
        cir.setReturnValue(customEyePos);
    }

    @Inject(
            method = "getEyePosition()Lnet/minecraft/world/phys/Vec3;",
            at = @At("HEAD"),
            cancellable = true
    )
    private void cobblemon$modifyEyePosition_noPartial(CallbackInfoReturnable<Vec3> cir) {
        var entity = (Entity)(Object)this;
        if (entity.level().isClientSide) return;
        if (!(entity instanceof Player player)) return;
        if (!(player instanceof RidePassenger ridePassenger)) return;
        if (!(player.getVehicle() instanceof OrientationControllable vehicle)) return;
        var vehicleController = vehicle.getOrientationController();
        if (vehicleController == null) return;

        // TODO: Determine if it needs partialtick involved.
        Vec3 customEyePos = ridePassenger.cobblemon$getRideEyePos();
        cir.setReturnValue(customEyePos);
    }


    @Inject(
            method = "push(Lnet/minecraft/world/entity/Entity;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    public void cobblemon$customPushLogic(Entity pushee, CallbackInfo ci) {
        var pusher = (Entity)(Object)this;
        var pusherIsPlayerOrPokemon = (pusher instanceof PokemonEntity || pusher instanceof Player);
        var pusheeIsPlayerOrPokemon = (pushee instanceof PokemonEntity || pushee instanceof Player);

        if (pusherIsPlayerOrPokemon && pusheeIsPlayerOrPokemon){
            if (!pusher.isPassengerOfSameVehicle(pushee)) {
                if (!pushee.noPhysics && !pusher.noPhysics) {
                    double d = pushee.getX() - pusher.getX();
                    double e = pushee.getZ() - pusher.getZ();
                    double f = Mth.absMax(d, e);
                    if (f >= (double) 0.01F) {
                        f = Math.sqrt(f);
                        d /= f;
                        e /= f;
                        double g = (double) 1.0F / f;
                        if (g > (double) 1.0F) {
                            g = (double) 1.0F;
                        }

                        d *= g;
                        e *= g;
                        d *= (double) 0.05F;
                        e *= (double) 0.05F;

                        /**********************************************
                         * Custom portion of the code:
                         * Handle collisions with weight in mind. Use
                         * the weight value of pokemon to determine
                         * which side gets what portion of the total
                         * force.
`                        *********************************************/
                        // Weights in hectograms
                        // Check to see if the pusher or pushee is a player. 200ish pounds. Steve's like a 6'6 miner
                        var pusherWeight = pusher instanceof PokemonEntity pusherPokemon ? pusherPokemon.getPokemon().getSpecies().getWeight() : 900F;
                        var pusheeWeight = pushee instanceof PokemonEntity pusheePokemon ? pusheePokemon.getPokemon().getSpecies().getWeight() : 900F;

                        // Calculate the portion of force received for both entities. Bigger receives less from the
                        // collision than the smaller one.
                        var totalWeight = pusherWeight + pusheeWeight;
                        var pusherReceivedForce = (pusheeWeight / totalWeight) * 2F;
                        var pusheeReceivedForce = (pusherWeight / totalWeight) * 2F;

                        // Do the pushing for both entities
                        if (!pusher.isVehicle() && pusher.isPushable()) {
                            pusher.push(-d * pusherReceivedForce, (double) 0.0F, -e * pusherReceivedForce);
                        }
                        if (!pushee.isVehicle() && pushee.isPushable()) {
                            pushee.push(d * pusheeReceivedForce, (double) 0.0F, e * pusheeReceivedForce);
                        }
                    }

                }
            }
            // Do not continue on to the base logic
            ci.cancel();
        }
    }
}
