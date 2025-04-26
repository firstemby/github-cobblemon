/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.mixin.client;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.OrientationControllable;
import com.cobblemon.mod.common.api.riding.Rideable;
import com.cobblemon.mod.common.client.MountedPokemonAnimationRenderController;
import com.cobblemon.mod.common.client.render.camera.MountedCameraRenderer;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
public abstract class CameraMixin {
    @Shadow private Entity entity;
    @Shadow @Final private Quaternionf rotation;
    @Shadow @Final private Vector3f forwards;
    @Shadow @Final private Vector3f up;
    @Shadow @Final private Vector3f left;
    @Shadow private float xRot;
    @Shadow private float yRot;

    @Shadow private float eyeHeight;
    @Shadow private float eyeHeightOld;

    @Shadow protected abstract void setPosition(Vec3 pos);

    @Unique private float returnTimer = 0;
    @Unique private float rollAngleStart = 0;
    @Unique Minecraft minecraft = Minecraft.getInstance();

    @Unique boolean disableRollableCameraDebug = false;

    @Inject(method = "setRotation", at = @At("HEAD"), cancellable = true)
    public void cobblemon$setRotation(float f, float g, CallbackInfo ci) {
        if (!(this.entity instanceof OrientationControllable controllable) || disableRollableCameraDebug) return;
        var controller = controllable.getOrientationController();
        if (!controller.isActive() && controller.getOrientation() != null) {
            if(this.returnTimer < 1) {
                //Rotation is taken from entity since we no longer handle mouse ourselves
                //Stops a period of time when you can't input anything.
                controller.getOrientation().set(
                        new Matrix3f()
                                .rotateY((float) Math.toRadians(180 - this.entity.getYRot()))
                                .rotateX((float) Math.toRadians(-this.entity.getXRot()))
                );
                if(rollAngleStart == 0){
                    this.returnTimer = 1;
                    controller.reset();
                    return;
                }
                controller.getOrientation().rotateZ((float) Math.toRadians(-rollAngleStart*(1-returnTimer)));
                cobblemon$applyRotation();
                this.returnTimer += .05F;
                ci.cancel();
            } else {
                this.returnTimer = 1;
                controller.reset();
            }
            return;
        }
        if (controller.getOrientation() == null) return;
        cobblemon$applyRotation();

        this.returnTimer = 0;
        this.rollAngleStart = controller.getRoll();
        ci.cancel();
    }

    @WrapOperation(method = "setup", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;setPosition(DDD)V"))
    public void cobblemon$positionCamera(Camera instance, double x, double y, double z, Operation<Void> original, @Local(ordinal = 1, argsOnly = true) boolean thirdPersonReverse) {
        Entity entity = instance.getEntity();
        Entity vehicle = entity.getVehicle();
        MountedPokemonAnimationRenderController.INSTANCE.reset();

        if(vehicle instanceof Rideable){
            Vec3 position = MountedCameraRenderer.INSTANCE.getCameraPosition(
                    instance,
                    entity,
                    vehicle,
                    x,
                    y,
                    z,
                    thirdPersonReverse,
                    eyeHeightOld,
                    eyeHeight
            );

            if(position != null){
                setPosition(position);
                return;
            }
        }

        original.call(instance, x, y, z);
    }

    @Unique
    private void cobblemon$applyRotation(){
        if (!(this.entity instanceof OrientationControllable controllable) || controllable.getOrientationController().getOrientation() == null) return;
        var controller = controllable.getOrientationController();
        var newRotation = controller.getOrientation().normal(new Matrix3f()).getNormalizedRotation(new Quaternionf());
        if (this.minecraft.options.getCameraType().isMirrored()) {
            newRotation.rotateY((float)Math.toRadians(180));
        }
        this.rotation.set(newRotation);
        this.xRot = controller.getPitch();
        this.yRot = controller.getYaw();
        this.forwards.set(controller.getForwardVector());
        this.up.set(controller.getUpVector());
        this.left.set(controller.getLeftVector());
    }

    @WrapOperation(method = "setup", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;move(FFF)V", ordinal = 0))
    public void stopMoveIfRolling(Camera instance, float zoom, float dy, float dx, Operation<Void> original) {
        Entity entity = instance.getEntity();
        Entity vehicle = entity.getVehicle();

        if (!(vehicle instanceof PokemonEntity) || Cobblemon.config.getThirdPersonViewBobbing()) {
            original.call(instance, zoom, dy, dx);
        }
    }
}
