/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.mixin.client;

import com.cobblemon.mod.common.client.render.shader.TeraHalo;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public class LevelRendererMixin {
    @Inject(method = "initOutline", at = @At("TAIL"))
    private void cobblemon$loadTeraHalo(CallbackInfo ci) {
        TeraHalo.INSTANCE.load();
    }

    @Inject(method = "resize", at = @At("TAIL"))
    private void cobblemon$resizeTeraHalo(int width, int height, CallbackInfo ci) {
        TeraHalo.INSTANCE.resize(width, height);
    }

    @Inject(method = "close", at = @At("TAIL"))
    private void cobblemon$closeTeraHalo(CallbackInfo ci) {
        TeraHalo.INSTANCE.close();
    }
}
