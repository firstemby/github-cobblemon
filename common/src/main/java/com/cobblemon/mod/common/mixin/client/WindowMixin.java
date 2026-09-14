/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.mixin.client;

import com.cobblemon.mod.common.client.editor.cinematic.viewport.EditorViewportMath;
import com.cobblemon.mod.common.client.editor.cinematic.viewport.EditorViewportState;
import com.cobblemon.mod.common.client.editor.cinematic.viewport.WindowResizeHook;
import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Window.class)
public abstract class WindowMixin implements WindowResizeHook {
    @Shadow private int width;
    @Shadow private int height;
    @Shadow private double guiScale;
    @Shadow private int guiScaledWidth;
    @Shadow private int guiScaledHeight;

    @Unique private boolean cobblemon$wasActive = false;
    @Unique private int cobblemon$lastWidth = -1;
    @Unique private int cobblemon$lastHeight = -1;

    @Inject(method = "getWidth", at = @At("HEAD"), cancellable = true)
    private void cobblemon$overrideWidth(CallbackInfoReturnable<Integer> cir) {
        if (EditorViewportState.INSTANCE.getActive()) {
            cir.setReturnValue(EditorViewportMath.INSTANCE.overrideSize(EditorViewportState.INSTANCE.getFrameWidth(), this.width));
        }
    }

    @Inject(method = "getHeight", at = @At("HEAD"), cancellable = true)
    private void cobblemon$overrideHeight(CallbackInfoReturnable<Integer> cir) {
        if (EditorViewportState.INSTANCE.getActive()) {
            cir.setReturnValue(EditorViewportMath.INSTANCE.overrideSize(EditorViewportState.INSTANCE.getFrameHeight(), this.height));
        }
    }

    @Inject(method = "getScreenWidth", at = @At("HEAD"), cancellable = true)
    private void cobblemon$overrideScreenWidth(CallbackInfoReturnable<Integer> cir) {
        if (EditorViewportState.INSTANCE.getActive()) {
            cir.setReturnValue(EditorViewportMath.INSTANCE.overrideSize(EditorViewportState.INSTANCE.getFrameWidth(), this.width));
        }
    }

    @Inject(method = "getScreenHeight", at = @At("HEAD"), cancellable = true)
    private void cobblemon$overrideScreenHeight(CallbackInfoReturnable<Integer> cir) {
        if (EditorViewportState.INSTANCE.getActive()) {
            cir.setReturnValue(EditorViewportMath.INSTANCE.overrideSize(EditorViewportState.INSTANCE.getFrameHeight(), this.height));
        }
    }

    // GUI scaling must use viewport dimensions so rendering and hitboxes agree.
    @Inject(method = "calculateScale", at = @At("HEAD"), cancellable = true)
    private void cobblemon$overrideCalculateScale(int guiScaleIn, boolean forceUnicode, CallbackInfoReturnable<Integer> cir) {
        if (!EditorViewportState.INSTANCE.getActive()) return;
        Window self = (Window) (Object) this;
        int fbw = self.getWidth();
        int fbh = self.getHeight();
        int j = 1;
        while (j != guiScaleIn && j < fbw && j < fbh && fbw / (j + 1) >= 320 && fbh / (j + 1) >= 240) j++;
        if (forceUnicode && j % 2 != 0) j++;
        cir.setReturnValue(j);
    }

    @Inject(method = "setGuiScale", at = @At("HEAD"), cancellable = true)
    private void cobblemon$overrideGuiScale(double scaleFactor, CallbackInfo ci) {
        if (!EditorViewportState.INSTANCE.getActive()) return;
        Window self = (Window) (Object) this;
        int fbw = self.getWidth();
        int fbh = self.getHeight();
        this.guiScale = scaleFactor;
        int i = (int) ((double) fbw / scaleFactor);
        this.guiScaledWidth = (double) fbw / scaleFactor > (double) i ? i + 1 : i;
        int j = (int) ((double) fbh / scaleFactor);
        this.guiScaledHeight = (double) fbh / scaleFactor > (double) j ? j + 1 : j;
        ci.cancel();
    }

    // Viewport changes do not trigger GLFW's window resize callback.
    @Override
    public void cobblemon$checkForOverrideResize() {
        boolean active = EditorViewportState.INSTANCE.getActive();
        if (!active && !this.cobblemon$wasActive) return;
        Window self = (Window) (Object) this;
        int w = self.getWidth();
        int h = self.getHeight();
        if (active != this.cobblemon$wasActive || w != this.cobblemon$lastWidth || h != this.cobblemon$lastHeight) {
            this.cobblemon$wasActive = active;
            this.cobblemon$lastWidth = w;
            this.cobblemon$lastHeight = h;
            Minecraft.getInstance().resizeDisplay();
        }
    }
}
