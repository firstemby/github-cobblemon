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

    //If you want to move this to a delagate you need an AW for position
    @WrapOperation(method = "setup", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;setPosition(DDD)V"))
    public void cobblemon$positionCamera(Camera instance, double x, double y, double z, Operation<Void> original, @Local(ordinal = 1, argsOnly = true) boolean thirdPersonReverse) {
        Entity entity = instance.getEntity();
        Entity vehicle = entity.getVehicle();
        MountedPokemonAnimationRenderController.INSTANCE.reset();

        if(vehicle instanceof Rideable){
            //RidingCameraDelagate.INSTANCE.positionCamera(instance, x, y, z, original);
            if (!(vehicle instanceof PokemonEntity pokemon)) {
                original.call(instance, x, y, z);
                return;
            }

            PokemonClientDelegate delegate = (PokemonClientDelegate) pokemon.getDelegate();
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

            if (!instance.isDetached() || Cobblemon.config.getThirdPartyViewBobbing()) {
                MatrixWrapper locator = delegate.getLocatorStates().get(seat.getLocator());

                if (locator == null) {
                    original.call(instance, x, y, z);
                    return;
                }

                Vec3 locatorOffset = new Vec3(locator.getMatrix().getTranslation(new Vector3f()));

                float currEyeHeight = Mth.lerp(instance.getPartialTickTime(), eyeHeightOld, eyeHeight);
                var rotatedEyeHeight = new Vector3f(0f, currEyeHeight - (entity.getBbHeight() / 2), 0f);
                if (controller.isActive()) {
                    rotatedEyeHeight = controller.getRenderOrientation(partialTickTime).transform(rotatedEyeHeight);
                }

                Vec3 position = locatorOffset.add(entityPos).add(new Vec3(rotatedEyeHeight));
                setPosition(position);
            } else {
                Vector3f pos = entityPos.add(new Vec3(0, pokemon.getBbHeight() / 2, 0)).toVector3f();

                PosableModel model = VaryingModelRepository.INSTANCE.getPoser(pokemon.getPokemon().getSpecies().getResourceIdentifier(), new FloatingState());
                Map<String, Vec3> cameraOffsets = model.getSeatToCameraOffset();

                Vec3 cameraOffset;
                if (thirdPersonReverse && cameraOffsets.containsKey(seat.getLocator() + "_reverse")) {
                    cameraOffset = cameraOffsets.get(seat.getLocator() + "_reverse");
                } else if (cameraOffsets.containsKey(seat.getLocator())) {
                    cameraOffset = cameraOffsets.get(seat.getLocator());
                } else {
                    cameraOffset = new Vec3(0f, 2f, 4f);
                }

                Vector3f offset = cameraOffset.toVector3f();

                if (thirdPersonReverse) offset.z *= -1;
                float xRot = (float) (-1 * Math.toRadians(instance.getXRot()));

                Matrix3f orientation = controller.isActive() && controller.getOrientation() != null ? controller.getOrientation() : new Matrix3f().rotateY((float) Math.toRadians(180f - instance.getYRot())).rotateX(xRot);
                Vector3f rotatedOffset = orientation.transform(offset);
                float offsetDistance = rotatedOffset.length();
                Vector3f offsetDirection = rotatedOffset.mul(1 / offsetDistance);

                float maxZoom = cobblemon$getMaxZoom(offsetDistance, offsetDirection, pos);

                setPosition(new Vec3(offsetDirection.mul(maxZoom).add(pos)));
            }
        } else {
            original.call(instance, x, y, z);
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

        if (!(vehicle instanceof PokemonEntity) || Cobblemon.config.getThirdPartyViewBobbing()) {
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
