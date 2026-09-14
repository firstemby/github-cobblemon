/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.mixin.client;

import com.cobblemon.mod.common.client.imgui.CobblemonImGui;
import com.mojang.blaze3d.systems.RenderSystem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraft.client.Minecraft;

// Render after the backbuffer blit so ImGui stays above the composited frame.
@Mixin(Minecraft.class)
public abstract class MinecraftClientMixin {

    @Inject(
        method = "runTick",
        at = @At(
            value = "INVOKE",
            target = "Lcom/mojang/blaze3d/pipeline/RenderTarget;blitToScreen(II)V",
            shift = At.Shift.AFTER
        )
    )
    private void cobblemon$renderImGuiOverlay(boolean tick, CallbackInfo ci) {
        if (!RenderSystem.isOnRenderThread()) return;
        if (CobblemonImGui.INSTANCE.isActive()) {
            CobblemonImGui.INSTANCE.renderFrame();
        }
    }
}
