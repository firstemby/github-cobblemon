/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.mixin.client;

import com.cobblemon.mod.common.client.render.camera.CameraViewAreaExtension;
import net.minecraft.client.renderer.ViewArea;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ViewArea.class)
public class ViewAreaMixin {
    @Inject(method = "setDirty", at = @At("HEAD"))
    private void cobblemon$markCinematicDirty(int cx, int cy, int cz, boolean reRenderOnMainThread, CallbackInfo ci) {
        CameraViewAreaExtension.setDirty(cx, cy, cz, reRenderOnMainThread);
    }
}
