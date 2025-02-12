/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.mixin;

import com.cobblemon.mod.common.Rollable;
import com.cobblemon.mod.common.client.CobblemonClient;
import com.cobblemon.mod.common.client.keybind.keybinds.PartySendBinding;
import com.cobblemon.mod.common.pokedex.scanner.PokedexUsageContext;
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.SmoothDouble;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public class MouseHandlerMixin {
    @Shadow private double accumulatedScrollY;
    @Shadow @Final private Minecraft minecraft;

    @Unique SmoothDouble pitchSmoother = new SmoothDouble();
    @Unique SmoothDouble rollSmoother = new SmoothDouble();
    @Unique SmoothDouble yawSmoother = new SmoothDouble();

    @Shadow @Final private SmoothDouble smoothTurnY;

    @Shadow @Final private SmoothDouble smoothTurnX;

    @Shadow private double accumulatedDX;

    @Shadow private double accumulatedDY;

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
    public boolean cobblemon$modifyRotation(LocalPlayer player, double cursorDeltaX, double cursorDeltaY, @Local(argsOnly = true) double d) {
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

        if (!(player instanceof Rollable rollable)) return true;

        if (!rollable.shouldRoll()) {
            pitchSmoother.reset();
            rollSmoother.reset();
            yawSmoother.reset();
            return true;
        }

        if ( rollable.useMouseInputRoll())
        {
            var pitchMouse = pitchSmoother.getNewDeltaValue(cursorDeltaY * 0.15f, d);
            var rollMouse = rollSmoother.getNewDeltaValue(cursorDeltaX * 0.15f, d);
            rollable.rotate(0.0F, (float)pitchMouse, (float)rollMouse);

            //get the current velocity requested to be applied
            //by the ride controller
            var angRot = rollable.angRollVel();
            //Multiply against deltatime to get a consistent turn
            //independent of framerate
            if( angRot != null )
            {
                //var yaw = angRot.x * d;
                var yaw = yawSmoother.getNewDeltaValue(angRot.x * 0.5f, d);
                //var pitch = angRot.y * d;
                var pitch = pitchSmoother.getNewDeltaValue(angRot.y * 0.5f, d);
                //var roll = angRot.z * d;
                var roll = rollSmoother.getNewDeltaValue(angRot.z * 0.5f, d);
                rollable.rotate((float)yaw , (float)pitch, (float) roll);
            }

            return false;
        }
        //code used in two spots. Need to refactor or rethink how this is done
        //If riding maybe only ever use our way of doing mouse input?
        //It creates a better feeling ride anyhow?
        else
        {
            //get the current velocity requested to be applied
            //by the ride controller
            var angRot = rollable.angRollVel();
            //Multiply against deltatime to get a consistent turn
            //independent of framerate
            if( angRot != null )
            {
                //var yaw = angRot.x * d;
                //Add the yaw to the mouse here... this all needs changed. It should
                //just map which 0 1 or 2 of the three it is going to modulate with
                //what
                var yaw = yawSmoother.getNewDeltaValue(angRot.x *  0.5f, d);
                yaw += yawSmoother.getNewDeltaValue(cursorDeltaX * 0.15f, d);
                //var pitch = angRot.y * d;
                var pitch = pitchSmoother.getNewDeltaValue(angRot.y *  0.5f, d);
                //var roll = angRot.z * d;
                var roll = rollSmoother.getNewDeltaValue(angRot.z *  0.5f, d);
                rollable.rotate((float)yaw , (float)pitch, (float) roll);
            }

            return true;
        }

    }

    @Inject(method = "handleAccumulatedMovement", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/MouseHandler;isMouseGrabbed()Z", ordinal = 0))
    private void cobblemon$maintainMovementWhenInScreens(CallbackInfo ci, @Local(ordinal = 1) double e) {
        if (minecraft.player == null) return;
        if (!(minecraft.player instanceof Rollable rollable)) return;
        if (!rollable.shouldRoll()) return;
        if (minecraft.isPaused()) return;

        var pitch = pitchSmoother.getNewDeltaValue(0, e);
        var roll = rollSmoother.getNewDeltaValue(0, e);
        rollable.rotate(0.0F, (float)pitch, (float)roll);
    }

}
