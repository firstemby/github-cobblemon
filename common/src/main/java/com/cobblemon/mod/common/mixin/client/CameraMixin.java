/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.mixin.client;

import com.cobblemon.mod.common.api.riding.Rideable;
import com.cobblemon.mod.common.client.MountedPokemonAnimationRenderController;
import com.cobblemon.mod.common.client.render.camera.MountedCameraRenderer;
import com.cobblemon.mod.common.duck.CameraDuck;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
public abstract class CameraMixin implements CameraDuck {
    @Shadow private float eyeHeight;
    @Shadow private float eyeHeightOld;
    @Shadow private Level level;
    @Shadow private Entity entity;
    @Final @Shadow private Vector3f forwards;
    @Final @Shadow private Vector3f up;
    @Final @Shadow private Vector3f left;
    @Shadow private float yRot;
    @Shadow private float xRot;
    @Shadow private Vec3 position;
    @Shadow @Final private BlockPos.MutableBlockPos blockPosition;
    @Shadow private int matrixPropertiesDirty;

    @Override public BlockGetter cobblemon$getLevel() {
       return level;
    }

    @Override public Entity cobblemon$getEntity() {
        return entity;
    }

    @Override public Vector3f cobblemon$getForwards() { return forwards; }

    @Override public Vector3f cobblemon$getUp() { return up; }

    @Override public Vector3f cobblemon$getLeft() { return left; }

    @Override public float cobblemon$getXRot() { return xRot; }
    @Override public void cobblemon$setXRot(float xRot) { this.xRot = xRot; }

    @Override public float cobblemon$getYRot() { return yRot; }
    @Override public void cobblemon$setYRot(float yRot) { this.yRot = yRot; }

    @Override public void cobblemon$markDirty() { this.matrixPropertiesDirty |= 3; }

    @Inject(method = "setPosition(DDD)V", at = @At("HEAD"), cancellable = true)
    private void cobblemon$positionCamera(double x, double y, double z, CallbackInfo ci) {
        if (this.entity == null) return;
        Entity entity = this.entity;
        Entity vehicle = entity.getVehicle();
        boolean thirdPersonReverse = Minecraft.getInstance().options.getCameraType().isMirrored();
        MountedPokemonAnimationRenderController.INSTANCE.reset();

        if (vehicle instanceof Rideable) {
            Vec3 ridePos = MountedCameraRenderer.INSTANCE.getCameraPosition(
                (Camera) (Object) this,
                entity,
                vehicle,
                thirdPersonReverse,
                eyeHeight,
                eyeHeightOld
            );

            if (ridePos != null) {
                // Directly set the position field instead of calling setPosition(Vec3)
                this.position = ridePos;
                this.blockPosition.set(ridePos.x, ridePos.y, ridePos.z);

                ci.cancel();
                return;
            }
        }

        // reset the rotation values if it is now not being ridden.
        MountedCameraRenderer.INSTANCE.setSmoothRotation(null);
    }
}
