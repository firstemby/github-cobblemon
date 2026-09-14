/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.mixin.client;

import com.cobblemon.mod.common.client.editor.cinematic.viewport.EditorViewportBlit;
import com.cobblemon.mod.common.client.editor.cinematic.viewport.EditorViewportState;
import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(RenderTarget.class)
public abstract class RenderTargetMixin {
    @Inject(method = "blitToScreen(II)V", at = @At("HEAD"), cancellable = true)
    private void cobblemon$partialBlitForEditor(int width, int height, CallbackInfo ci) {
        if (!EditorViewportState.INSTANCE.getActive()) return;
        if ((Object) this != Minecraft.getInstance().getMainRenderTarget()) return;
        EditorViewportBlit.INSTANCE.blit((RenderTarget) (Object) this);
        ci.cancel();
    }
}
