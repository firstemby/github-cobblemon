/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.mixin.client;

import com.cobblemon.mod.common.client.render.RenderAlphaScope;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(RenderStateShard.class)
public abstract class RenderStateShardMixin {
    @Inject(method = "setupRenderState", at = @At("RETURN"))
    private void cobblemon$applyScopedAlpha(CallbackInfo ci) {
        if ((Object) this instanceof RenderType) RenderAlphaScope.afterMaterialSetup();
    }

    @Inject(method = "clearRenderState", at = @At("RETURN"))
    private void cobblemon$restoreScopedAlpha(CallbackInfo ci) {
        if ((Object) this instanceof RenderType) RenderAlphaScope.afterMaterialClear();
    }
}
