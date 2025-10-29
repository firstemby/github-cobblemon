/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.mixin.entity;

import com.cobblemon.mod.common.OrientationControllable;
import com.cobblemon.mod.common.duck.RideRotation;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
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

//    // Targets: public final Vec3 getEyePosition(float partialTicks)
//    @Inject(
//            method = "getEyePosition(F)Lnet/minecraft/world/phys/Vec3;",
//            at = @At("HEAD"),
//            cancellable = true
//    )
//    private void cobblemon$modifyEyePosition_partial(float partialTicks, CallbackInfoReturnable<Vec3> cir) {
//        var entity = (Entity)(Object)this;
//        if (!(entity instanceof LocalPlayer player)) return;
//        if (!(player instanceof RideRotation playerRideRots)) return;
//        if (!(player.getVehicle() instanceof OrientationControllable vehicle)) return;
//        var vehicleController = vehicle.getOrientationController();
//        if (vehicleController == null) return;
//
//        // If Orientation is currently being controlled then use the stored eye position from camera positioning.
//        if (vehicleController.getActive() && vehicleController.getOrientation() != null) {
//            // TODO: Determine if it needs partialtick involved.
//            Vec3 customEyePos = playerRideRots.cobblemon$getRideEyePos();
//            cir.setReturnValue(customEyePos);
//        }
//    }
//
//    @Inject(
//            method = "getEyePosition()Lnet/minecraft/world/phys/Vec3;",
//            at = @At("HEAD"),
//            cancellable = true
//    )
//    private void cobblemon$modifyEyePosition_noPartial(CallbackInfoReturnable<Vec3> cir) {
//        var entity = (Entity)(Object)this;
//        if (!(entity instanceof LocalPlayer player)) return;
//        if (!(player instanceof RideRotation playerRideRots)) return;
//        if (!(player.getVehicle() instanceof OrientationControllable vehicle)) return;
//        var vehicleController = vehicle.getOrientationController();
//        if (vehicleController == null) return;
//
//        // If Orientation is currently being controlled then use the stored eye position from camera positioning.
//        if (vehicleController.getActive() && vehicleController.getOrientation() != null) {
//            Vec3 customEyePos = playerRideRots.cobblemon$getRideEyePos();
//            cir.setReturnValue(customEyePos);
//        }
//    }

}
