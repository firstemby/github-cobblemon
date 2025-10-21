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
import com.cobblemon.mod.common.client.CobblemonClient;
import com.cobblemon.mod.common.client.keybind.keybinds.PartySendBinding;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokedex.scanner.PokedexUsageContext;
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.Blaze3D;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.SmoothDouble;
import net.minecraft.world.phys.Vec3;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static com.google.common.primitives.Floats.min;
import static net.minecraft.util.Mth.lerp;

@Mixin(MouseHandler.class)
public abstract class MouseHandlerMixin {
    @Shadow private double accumulatedScrollY;
    @Shadow @Final private Minecraft minecraft;

    @Unique SmoothDouble xMouseSmoother = new SmoothDouble();
    @Unique SmoothDouble yMouseSmoother = new SmoothDouble();
    @Unique SmoothDouble pitchSmoother = new SmoothDouble();
    @Unique SmoothDouble rollSmoother = new SmoothDouble();
    @Unique SmoothDouble yawSmoother = new SmoothDouble();

    @Shadow @Final private SmoothDouble smoothTurnY;

    @Shadow @Final private SmoothDouble smoothTurnX;

    @Shadow private double accumulatedDX;

    @Shadow private double accumulatedDY;

    @Shadow private double lastHandleMovementTime;

    @Shadow public abstract boolean isMouseGrabbed();

    @Shadow protected abstract void turnPlayer(double movementTime);

    @Unique private double cobblemon$timeDelta;

    @Inject(
            method = "onScroll",
            at = @At(
                    value = "FIELD",
                    target="Lnet/minecraft/client/MouseHandler;accumulatedScrollY:D",
                    opcode = Opcodes.PUTFIELD,
                    ordinal = 2,
                    shift = At.Shift.BEFORE
            ),
            cancellable = true
    )
    public void cobblemon$scrollParty(long window, double horizontal, double vertical, CallbackInfo ci) {
        if (PartySendBinding.INSTANCE.getWasDown()) {
            int i = (int) accumulatedScrollY;
            if (i > 0) {
                accumulatedScrollY -= i;
                CobblemonClient.INSTANCE.getStorage().shiftSelected(false);
                ci.cancel();
                PartySendBinding.INSTANCE.actioned();
            } else if (i < 0) {
                accumulatedScrollY -= i;
                CobblemonClient.INSTANCE.getStorage().shiftSelected(true);
                ci.cancel();
                PartySendBinding.INSTANCE.actioned();
            }
        }
    }

    @Inject(
        method = "onScroll",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/entity/player/Inventory;swapPaint(D)V"
        ),
        cancellable = true
    )
    public void cobblemon$doPokedexZoom(long window, double horizontal, double vertical, CallbackInfo ci) {
        PokedexUsageContext usageContext = CobblemonClient.INSTANCE.getPokedexUsageContext();
        if (usageContext.getScanningGuiOpen()) {
            usageContext.adjustZoom(vertical);
            ci.cancel();
        }
    }

    @WrapWithCondition(
            method = "turnPlayer",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/player/LocalPlayer;turn(DD)V"
            )
    )
    public boolean cobblemon$modifyRotation(
        LocalPlayer player,
        double cursorDeltaX,
        double cursorDeltaY,
        @Local(argsOnly = true) double d,
        @Local(argsOnly = true) double movementTime
        ) {
        boolean returnValue = true;

        PokedexUsageContext usageContext = CobblemonClient.INSTANCE.getPokedexUsageContext();
        if (usageContext.getScanningGuiOpen()) {
            this.smoothTurnY.reset();
            this.smoothTurnX.reset();
            var defaultSensitivity = this.minecraft.options.sensitivity().get() * 0.6 + 0.2;
            var spyglassSensitivity = Math.pow(defaultSensitivity, 3);
            var lookSensitivity = spyglassSensitivity * 8.0;
            var sensitivity = lerp(usageContext.getFovMultiplier(), spyglassSensitivity, lookSensitivity);
            var yRotationFlip = this.minecraft.options.invertYMouse().get() ? -1 : 1;
            player.turn(this.accumulatedDX * sensitivity, (this.accumulatedDY * sensitivity * yRotationFlip));
            returnValue = false;
        }

        var vehicle = player.getVehicle();

        // Clamp player rotation if riding and the vehicle demands it
        if (player.isPassenger() && vehicle instanceof PokemonEntity pokeVehicle) {
            pokeVehicle.clampPassengerRotation(player);
        }

        // If the player/passenger is not the driver then return and perform the normal turnPlayer logic
        if( player.getControlledVehicle() == null) return true;


        // If the ride is not orientation controllable then return and perform the normal turnPlayer logic
        if (!(vehicle instanceof OrientationControllable controllableVehicle) || !controllableVehicle.getOrientationController().isActive()) {
            xMouseSmoother.reset();
            yMouseSmoother.reset();
            pitchSmoother.reset();
            rollSmoother.reset();
            yawSmoother.reset();
            return returnValue;
        }

        var vehicleController = controllableVehicle.getOrientationController();

        // If looking around then modify the players orientation instead and zero the mouse deltas so the mouse doesn't
        // affect the vehicle orientation when looking around.
        // If the client is a passenger it should definitely just return above
        var lookaroundButtonPressed = Minecraft.getInstance().mouseHandler.isMiddlePressed();
        if (lookaroundButtonPressed) {
            cursorDeltaX = 0.0;
            cursorDeltaY = 0.0;
        } else {
            // If not looking around but riding then reset to zero rots
            player.setXRot(lerp(min(5.0f * (float)movementTime, 1.0f), Mth.wrapDegrees(player.getXRot()), 0.0f));
            player.setYRot(lerp(min(5.0f * (float)movementTime, 1.0f), Mth.wrapDegrees(player.getYRot()), 0.0f));
        }

        // Send mouse input to be interpreted into rotation
        // deltas by the ride controller
        Vec3 angVecMouse = cobblemon$getRideMouseRotation(cursorDeltaX, cursorDeltaY, movementTime);

        // Perform Rotation using mouse influenced rotation deltas.
        vehicleController.rotate(
            (float) angVecMouse.x,
            (float) angVecMouse.y,
            (float) angVecMouse.z
        );

        // Gather and apply the current rotation deltas
        var angRot = cobblemon$getAngularVelocity(movementTime);

        // Apply smoothing if requested by the controller.
        // This Might be best if done by the controller itself?
        if(cobblemon$shouldUseAngVelSmoothing()) {
            var yaw = yawSmoother.getNewDeltaValue(angRot.x * 0.5f, d);
            var pitch = pitchSmoother.getNewDeltaValue(angRot.y * 0.5f, d);
            var roll = rollSmoother.getNewDeltaValue(angRot.z * 0.5f, d);
            vehicleController.rotate((float) yaw, (float) pitch, (float) roll);
        }
        // Otherwise simply apply the smoothing
        else {
            // TODO: why 10?
            vehicleController.rotate((float) (angRot.x * 10 * d), (float) (angRot.y * 10 * d), (float) (angRot.z * 10 * d));
        }

        return lookaroundButtonPressed; //TODO: This was changed to return true to also allow base mc player angles to change. is there a better way to do this?
    }

    @Inject(method = "handleAccumulatedMovement", at = @At("HEAD"))
    private void cobblemon$maintainMovementWhenInScreens2(CallbackInfo ci) {
        double time = Blaze3D.getTime();
        cobblemon$timeDelta = time - this.lastHandleMovementTime;
    }

    @Inject(method = "handleAccumulatedMovement", at = @At("TAIL"))
    private void cobblemon$maintainMovementWhenInScreens(CallbackInfo ci) {
        if (minecraft.player == null) return;
        if (!(minecraft.player.getVehicle() instanceof OrientationControllable vehicleControllable)) return;
        if (!(vehicleControllable.getOrientationController() instanceof OrientationController vehicleController)) return;
        if (vehicleController.getOrientation() == null) return;
        if (minecraft.isPaused()) return;
        if (this.isMouseGrabbed()) return; // return if turnPlayer() has already run this pass

        this.turnPlayer(cobblemon$timeDelta);
    }

    @Unique
    private Vec3 cobblemon$getAngularVelocity(double deltaTime) {
        var player = minecraft.player;
        if (player == null) return Vec3.ZERO;
        if (!(player instanceof OrientationControllable)) return Vec3.ZERO;

        var playerVehicle = player.getVehicle();
        if (playerVehicle == null) return Vec3.ZERO;
        if (!(playerVehicle instanceof PokemonEntity pokemonEntity)) return Vec3.ZERO;
        return pokemonEntity.ifRidingAvailableSupply(Vec3.ZERO, (behaviour, settings, state) -> {
            return behaviour.angRollVel(settings, state, pokemonEntity, player, deltaTime);
        });
    }

    @Unique
    private Vec3 cobblemon$getRideMouseRotation(double mouseX, double mouseY, double deltaTime) {
        var player = minecraft.player;
        if (player == null) return Vec3.ZERO;
        var vehicle = player.getVehicle();
        if (vehicle == null) return Vec3.ZERO;
        if (!(vehicle instanceof PokemonEntity pokemonEntity)) return Vec3.ZERO;

        var invertYaw = Cobblemon.config.getInvertYaw();
        var invertPitch = Cobblemon.config.getInvertPitch();

        var xValue = mouseX * Cobblemon.config.getXAxisSensitivity();
        var yValue = mouseY * Cobblemon.config.getYAxisSensitivity();

        var swapXAndY = Cobblemon.config.getSwapXAndYAxes();

        var yaw = (swapXAndY ? yValue : xValue) * (invertYaw? -1.0f : 1.0f);

        var pitch = (swapXAndY ? xValue : yValue) * (invertPitch? -1.0f : 1.0f);

        var sensitivity = cobblemon$getRidingSensitivity();
        return pokemonEntity.ifRidingAvailableSupply(Vec3.ZERO, (behaviour, settings, state) -> {
            return behaviour.rotationOnMouseXY(
                    settings,
                    state,
                    pokemonEntity,
                    player,
                    pitch,
                    yaw,
                    yMouseSmoother,
                    xMouseSmoother,
                    sensitivity,
                    deltaTime
            );
        });
    }

    @Unique
    private double cobblemon$getRidingSensitivity() {
        var sensitivity = this.minecraft.options.sensitivity().get() * 0.6000000238418579 + 0.20000000298023224;
        return Math.pow(sensitivity, 3);
    }

    @Unique
    private boolean cobblemon$shouldUseAngVelSmoothing() {
        var player = minecraft.player;
        if (player == null) return true;

        var playerVehicle = player.getVehicle();
        if (playerVehicle == null) return true;
        if (!(playerVehicle instanceof PokemonEntity pokemonEntity)) return true;
        return pokemonEntity.ifRidingAvailableSupply(true, (behaviour, settings, state) -> {
            return behaviour.useAngVelSmoothing(settings, state, pokemonEntity);
        });
    }

}
