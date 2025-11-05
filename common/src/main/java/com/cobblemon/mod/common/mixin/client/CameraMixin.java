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
import com.cobblemon.mod.common.api.orientation.OrientationController;
import com.cobblemon.mod.common.api.riding.Rideable;
import com.cobblemon.mod.common.client.MountedPokemonAnimationRenderController;
import com.cobblemon.mod.common.client.render.camera.MountedCameraRenderer;
import com.cobblemon.mod.common.duck.RidePassenger;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.Blaze3D;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
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

import static net.minecraft.util.Mth.abs;
import static net.minecraft.util.Mth.atan2;

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

    @Shadow private float partialTickTime;

    // Used to help in camera rotation rendering when transitioning
    // from rolled to unrolled.
    @Unique private float cobblemon$returnTimer = 0;
    @Unique private float cobblemon$rollAngleStart = 0;

    @Unique private double cobblemon$lastHandledRotationTime = Double.MIN_VALUE;
    @Unique private double cobblemon$frameTime = 0.0;

    @Inject(method = "setRotation", at = @At("HEAD"), cancellable = true)

    public void cobblemon$setRotation(float f, float g, CallbackInfo ci) {
        // Calculate the current frametime
        double d = Blaze3D.getTime();
        this.cobblemon$frameTime = (this.cobblemon$lastHandledRotationTime != Double.MIN_VALUE) ? d - this.cobblemon$lastHandledRotationTime : 0.0;
        this.cobblemon$lastHandledRotationTime = d;

        var vehicle = this.entity.getVehicle();
        if (!(vehicle instanceof OrientationControllable controllableVehicle)) return;
        var instance = (Camera)(Object)this;

        // If the current vehicle has no orientation then return and perform
        // vanilla camera rotations
        var vehicleController = controllableVehicle.getOrientationController();
        if (vehicleController.getOrientation() == null) return;

        // If the controller has been deactivated but the orientation isn't null yet
        // then perform transition from rolled ride/camera to vanilla
        if (!vehicleController.isActive() && vehicleController.getOrientation() != null) {
            cobblemon$applyTransitionRotation(vehicleController, ci);
        } else { // Otherwise perform cobblemon specific camera rotation and set transition helper variables
            cobblemon$applyRotation();
            this.cobblemon$returnTimer = 0;
            this.cobblemon$rollAngleStart = instance.rotation().getEulerAnglesYXZ(new Vector3f()).z();
            ci.cancel();
        }
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
        var driver = this.entity;
        if (!(driver.getVehicle() instanceof OrientationControllable controllableVehicle) ||
                controllableVehicle.getOrientationController().getOrientation() == null ||
                !controllableVehicle.getOrientationController().getActive()
        ) return;

        // Grab the driver and vehicle orientation controllers.
        var vehicleController = controllableVehicle.getOrientationController();

        var isDriving = driver.getControlledVehicle() != null;

        // Grab the ride orientation
        var newRotation = new Quaternionf();

        // If the player is driving then use the current orientation since the driver sets it.
        // If not then use the smoothed render orientation for passengers otherwise the vehicle rotation will be jerky
        if (isDriving) {
            newRotation = vehicleController.getOrientation().normal(new Matrix3f()).getNormalizedRotation(new Quaternionf());
        } else {
            newRotation = vehicleController.getRenderOrientation(this.partialTickTime);
        }

        // Unroll the current rotation if no roll is requested
        if (Cobblemon.config.getDisableRoll()) {
            var eulerAngs = newRotation.getEulerAnglesYXZ(new Vector3f());
            var currAngs = this.rotation.getEulerAnglesYXZ(new Vector3f());
            var vehicle = (PokemonEntity)driver.getVehicle();
            var vel = vehicle.getRidingAnimationData().getVelocitySpring().getInterpolated(this.partialTickTime, 0);

            // lerp the new rotation towards this y rot at a rate proportional to the horizontal movement speed?
            var yaw = Mth.wrapDegrees(Math.toDegrees(Mth.atan2(vel.x(), vel.z())) + 180.0f);

            var degDiff = Mth.degreesDifference((float)Math.toDegrees(currAngs.y()), (float)yaw);
            var pitchDeadzoneDeg = 15.0; // x degrees of deadzone about +-90 degrees of pitch.
            var pitchDeg = vehicleController.getPitch();
            // ConsiderPitch to be already 90 if within the deadzone around it.
            var cutoffPitch =  Math.min(1.0,(Mth.abs(pitchDeg) / (90.0f - pitchDeadzoneDeg))) * 90.0f * Math.signum(pitchDeg);
            var lerpRateMod = Mth.cos((float)Math.toRadians(cutoffPitch));

            // Apply smoothing from the cameras current yaw to the yaw of the velocity vector of the ride.
            var k = 5.0f; // Some smoothing constant k
            var smoothingFactor = lerpRateMod * cobblemon$frameTime * k;
            var newYaw = currAngs.y() + (Math.toRadians(degDiff) * smoothingFactor);

            newRotation.set(new Quaternionf().rotateYXZ((float)newYaw, eulerAngs.x(), 0.0f));
        }

        var rotationOffset = 0;
        if (Minecraft.getInstance().options.getCameraType().isMirrored()) {
            newRotation.rotateY((float)Math.toRadians(180));
            rotationOffset = 180;
        }

        // Get the drivers local look angle in relation to the ride and apply it.
        if (vehicleController.getActive()) {
            var vehicleMatrix = new Matrix3f().set(newRotation);
            var driverMatrix = new Matrix3f();// Base the driver
            var playerRotater = (RidePassenger)driver;
            newRotation = vehicleMatrix.mul(
                    driverMatrix.rotateYXZ(
                            -Mth.DEG_TO_RAD * (playerRotater.cobblemon$getRideYRot()),
                            -Mth.DEG_TO_RAD * playerRotater.cobblemon$getRideXRot(),
                            0.0f)
                ).normal(new Matrix3f()).getNormalizedRotation(new Quaternionf());
        }
        this.rotation.set(newRotation);
        var eulerAngs = newRotation.getEulerAnglesXYZ(new Vector3f());
        this.xRot = eulerAngs.x() * Mth.RAD_TO_DEG;
        this.yRot = eulerAngs.y() * Mth.RAD_TO_DEG;
        // Set the drivers rotations to match
        var driverEulerAngs = new Quaternionf(this.rotation).getEulerAnglesYXZ(new Vector3f());
        driver.setXRot(-driverEulerAngs.x() * Mth.RAD_TO_DEG + rotationOffset);
        driver.setYRot(180.0f - (driverEulerAngs.y() * Mth.RAD_TO_DEG));

        OrientationController.Companion.getFORWARDS().rotate(this.rotation, this.forwards);
        OrientationController.Companion.getUP().rotate(this.rotation, this.up);
        OrientationController.Companion.getLEFT().rotate(this.rotation, this.left);
    }

    @Unique
    private void cobblemon$applyTransitionRotation(
            OrientationController vehicleController,
            CallbackInfo ci
    ) {
        // If the transition has just started then
        if (this.cobblemon$returnTimer == 0.0) {
            MountedCameraRenderer.INSTANCE.resetDriverRotations((Camera)(Object)this, this.entity);
        }

        if(this.cobblemon$returnTimer < 1) {
            //Rotation is taken from entity since we no longer handle mouse ourselves
            //Stops a period of time when you can't input anything.
            float interpolatedRoll = Mth.lerp(this.cobblemon$returnTimer, this.cobblemon$rollAngleStart, 0.0f);
            float pitch = (float) Math.toRadians(-this.entity.getXRot());
            float yaw = (float) Math.toRadians(180 - this.entity.getYRot());

            Quaternionf interRot = new Quaternionf();
            interRot.rotationYXZ(yaw, pitch, interpolatedRoll);

            this.rotation.set(interRot);
            var eulerAngs = interRot.getEulerAnglesXYZ(new Vector3f());
            this.xRot = eulerAngs.x() * Mth.RAD_TO_DEG;
            this.yRot = eulerAngs.y() * Mth.RAD_TO_DEG;
            OrientationController.Companion.getFORWARDS().rotate(this.rotation, this.forwards);
            OrientationController.Companion.getUP().rotate(this.rotation, this.up);
            OrientationController.Companion.getLEFT().rotate(this.rotation, this.left);

            if(cobblemon$rollAngleStart == 0){
                this.cobblemon$returnTimer = 1;
                vehicleController.reset();
                MountedCameraRenderer.INSTANCE.resetDriverRotations((Camera)(Object)this, this.entity);
                return;
            }
            this.cobblemon$returnTimer += (partialTickTime * (1.0f/20.0f));
            ci.cancel();
        } else {
            this.cobblemon$returnTimer = 1;
            vehicleController.reset();
            MountedCameraRenderer.INSTANCE.resetDriverRotations((Camera)(Object)this, this.entity);
        }
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
