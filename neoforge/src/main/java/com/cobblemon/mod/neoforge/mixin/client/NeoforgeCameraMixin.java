/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.neoforge.mixin.client;

import com.cobblemon.mod.common.client.render.camera.MountedCameraRenderer;
import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
public class NeoforgeCameraMixin {
    @Inject(method = "Lnet/minecraft/client/Camera;setRotation(FF)V", at = @At("HEAD"), cancellable = true)
    public void cobblemon$setRotation(float f, float g, CallbackInfo ci) {
        if (MountedCameraRenderer.INSTANCE.setRotation((Camera) (Object) this)) ci.cancel();
    }
}
