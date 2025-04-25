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
import com.cobblemon.mod.common.api.riding.Seat;
import com.cobblemon.mod.common.client.MountedPokemonAnimationRenderController;
import com.cobblemon.mod.common.client.entity.PokemonClientDelegate;
import com.cobblemon.mod.common.client.render.MatrixWrapper;
import com.cobblemon.mod.common.client.render.models.blockbench.FloatingState;
import com.cobblemon.mod.common.client.render.models.blockbench.PosableModel;
import com.cobblemon.mod.common.client.render.models.blockbench.repository.VaryingModelRepository;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
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

import java.util.Map;

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

    @Shadow
    private BlockGetter level;

    @Shadow private float partialTickTime;
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
            if (!(vehicle instanceof PokemonEntity pokemon)) {
                original.call(instance, x, y, z);
                return;
            }
            Vec3 entityPos = new Vec3(
                    Mth.lerp(instance.getPartialTickTime(), pokemon.xOld, pokemon.getX()),
                    Mth.lerp(instance.getPartialTickTime(), pokemon.yOld, pokemon.getY()),
                    Mth.lerp(instance.getPartialTickTime(), pokemon.zOld, pokemon.getZ())
            );
            MountedPokemonAnimationRenderController.INSTANCE.setup(pokemon, partialTickTime);
            int seatIndex = pokemon.getPassengers().indexOf(entity);
            Seat seat = pokemon.getSeats().get(seatIndex);

            OrientationControllable rollable = (OrientationControllable) entity;
            OrientationController controller = rollable.getOrientationController();

            PosableModel model = VaryingModelRepository.INSTANCE.getPoser(pokemon.getPokemon().getSpecies().getResourceIdentifier(), new FloatingState());

            // First person and view bobbing are handled the same way.
            if (!instance.isDetached() || Cobblemon.config.getThirdPersonViewBobbing()) {
                PokemonClientDelegate delegate = (PokemonClientDelegate) pokemon.getDelegate();
                MatrixWrapper locator = delegate.getLocatorStates().get(seat.getLocator());

                if (locator == null) {
                    original.call(instance, x, y, z);
                    return;
                }

                Vec3 locatorOffset = new Vec3(locator.getMatrix().getTranslation(new Vector3f()));

                // Get Eye height
                float currEyeHeight = Mth.lerp(instance.getPartialTickTime(), eyeHeightOld, eyeHeight);
                Vector3f offset = new Vector3f(0f, currEyeHeight - (entity.getBbHeight() / 2), 0f);

                // Get additional offset from poser and add to the eyeHeight offset when first person
                if(!instance.isDetached()){
                    offset.add(cobblemon$getFirstPersonOffset(model, seat));
                }

                // Rotate Offset if needed
                if (controller.isActive()) {
                    offset = controller.getRenderOrientation(partialTickTime).transform(offset);
                }

                Vec3 position = locatorOffset.add(entityPos).add(new Vec3(offset));
                setPosition(position);
            } else {
                // Get pivot from poser
                Vector3f pivot = cobblemon$getThirdPersonPivot(model, seat, pokemon);
                Vector3f pos = entityPos.toVector3f().add(pivot);

                // Get offset from poser
                Vector3f offset = cobblemon$getThirdPersonOffset(thirdPersonReverse, model, seat);
                if (thirdPersonReverse) offset.z *= -1;
                float xRot = (float) (-1 * Math.toRadians(instance.getXRot()));

                // Rotate offset based on orientation or normal rotations
                Matrix3f orientation = controller.isActive() && controller.getOrientation() != null ? controller.getOrientation() : new Matrix3f().rotateY((float) Math.toRadians(180f - instance.getYRot())).rotateX(xRot);
                Vector3f rotatedOffset = orientation.transform(offset);
                float offsetDistance = rotatedOffset.length();
                Vector3f offsetDirection = rotatedOffset.mul(1 / offsetDistance);

                // Use getMaxZoom to calculate clipping
                float maxZoom = cobblemon$getMaxZoom(offsetDistance, offsetDirection, pos);

                setPosition(new Vec3(offsetDirection.mul(maxZoom).add(pos)));
            }
        } else {
            original.call(instance, x, y, z);
        }
    }

    @Unique
    private static @NotNull Vector3f cobblemon$getThirdPersonOffset(boolean thirdPersonReverse, PosableModel model, Seat seat) {
        Map<String, Vec3> cameraOffsets = model.getThirdPersonCameraOffset();

        if (thirdPersonReverse && cameraOffsets.containsKey(seat.getLocator() + "_reverse")) {
            return cameraOffsets.get(seat.getLocator() + "_reverse").toVector3f();
        } else if (cameraOffsets.containsKey(seat.getLocator())) {
            return cameraOffsets.get(seat.getLocator()).toVector3f();
        } else {
            return new Vector3f(0f, 2f, 4f);
        }
    }

    @Unique
    private static @NotNull Vector3f cobblemon$getThirdPersonPivot(PosableModel model, Seat seat, PokemonEntity entity) {
        Map<String, Vec3> pivotOffset = model.getThirdPersonPivotOffset();

        if (pivotOffset.containsKey(seat.getLocator())) {
            return pivotOffset.get(seat.getLocator()).toVector3f();
        } else {
            return new Vector3f(0f, entity.getBbHeight()/2, 0);
        }
    }

    @Unique
    private static @NotNull Vector3f cobblemon$getFirstPersonOffset(PosableModel model, Seat seat) {
        Map<String, Vec3> cameraOffsets = model.getFirstPersonCameraOffset();

        if (cameraOffsets.containsKey(seat.getLocator())) {
            return cameraOffsets.get(seat.getLocator()).toVector3f();
        } else {
            return new Vector3f(0f, 0f, 0f);
        }
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

    //Modified vanilla code
    @Unique
    private float cobblemon$getMaxZoom(float maxZoom, Vector3f directionVector, Vector3f positionVector) {
        Vec3 pos = new Vec3(positionVector);
        for (int i = 0; i < 8; i++) {
            float g = (float) ((i & 1) * 2 - 1);
            float h = (float) ((i >> 1 & 1) * 2 - 1);
            float j = (float) ((i >> 2 & 1) * 2 - 1);
            Vec3 vec3 = pos.add(g * 0.1F, h * 0.1F, j * 0.1F);
            Vec3 vec32 = vec3.add(new Vec3(directionVector).scale(maxZoom));
            HitResult hitResult = this.level.clip(new ClipContext(vec3, vec32, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, this.entity));
            if (hitResult.getType() != HitResult.Type.MISS) {
                float k = (float) hitResult.getLocation().distanceToSqr(pos);
                if (k < Mth.square(maxZoom)) {
                    maxZoom = Mth.sqrt(k);
                }
            }
        }

        return maxZoom;
    }
}
