/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.mixin.client;

import com.cobblemon.mod.common.OrientationControllable;
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
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

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
        PokedexUsageContext usageContext = CobblemonClient.INSTANCE.getPokedexUsageContext();
        if (usageContext.getScanningGuiOpen()) {
            this.smoothTurnY.reset();
            this.smoothTurnX.reset();
            var defaultSensitivity = this.minecraft.options.sensitivity().get() * 0.6000000238418579 + 0.20000000298023224;
            var spyglassSensitivity = Math.pow(defaultSensitivity, 3);
            var lookSensitivity = spyglassSensitivity * 8.0;
            var sensitivity = Mth.lerp(usageContext.getFovMultiplier(), spyglassSensitivity, lookSensitivity);
            player.turn(this.accumulatedDX * sensitivity, (this.accumulatedDY * sensitivity));
            return false;
        }

        if (!(player instanceof OrientationControllable controllable)) return true;

        if (!controllable.getOrientationController().isActive()) {
            xMouseSmoother.reset();
            yMouseSmoother.reset();
            pitchSmoother.reset();
            rollSmoother.reset();
            yawSmoother.reset();
            return true;
        }

        //Send mouse input to be interpreted into rotation
        //deltas by the ride controller
        Vec3 angVecMouse = cobblemon$getRideMouseRotation(cursorDeltaX, cursorDeltaY, movementTime);

        //Perform Rotation using mouse influenced rotation deltas.
        controllable.getOrientationController().rotate(
            (float) angVecMouse.x,
            (float) angVecMouse.y,
            (float) angVecMouse.z
        );

        //Gather and apply the current rotation deltas
        var angRot = cobblemon$getAngularVelocity(movementTime);

        //Apply smoothing if requested by the controller.
        //This Might be best if done by the controller itself?
        if(cobblemon$shouldUseAngVelSmoothing())
        {
            var yaw = yawSmoother.getNewDeltaValue(angRot.x * 0.5f, d);
            var pitch = pitchSmoother.getNewDeltaValue(angRot.y * 0.5f, d);
            var roll = rollSmoother.getNewDeltaValue(angRot.z * 0.5f, d);
            controllable.getOrientationController().rotate((float) yaw, (float) pitch, (float) roll);
        }
        //Otherwise simply apply the smoothing
        else
        {
            controllable.getOrientationController().rotate((float) (angRot.x * 10 * d), (float) (angRot.y * 10 * d), (float) (angRot.z * 10 * d));
        }
        return false;
    }

    @Inject(method = "handleAccumulatedMovement", at = @At("HEAD"))
    private void cobblemon$maintainMovementWhenInScreens2(CallbackInfo ci) {
        double time = Blaze3D.getTime();
        cobblemon$timeDelta = time - this.lastHandleMovementTime;
    }

    @Inject(method = "handleAccumulatedMovement", at = @At("TAIL"))
    private void cobblemon$maintainMovementWhenInScreens(CallbackInfo ci) {
        if (minecraft.player == null) return;
        if (!(minecraft.player instanceof OrientationControllable controllable)) return;
        if (!controllable.getOrientationController().isActive()) return;
        if (minecraft.isPaused()) return;
        if (isMouseGrabbed()) return;

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

        var sensitivity = cobblemon$getRidingSensitivity();
        return pokemonEntity.ifRidingAvailableSupply(Vec3.ZERO, (behaviour, settings, state) -> {
            return behaviour.rotationOnMouseXY(
                    settings,
                    state,
                    pokemonEntity,
                    player,
                    mouseY,
                    mouseX,
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
