/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.fabric.mixin;

import com.cobblemon.mod.common.client.CobblemonClient;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.objectweb.asm.Opcodes;

/**
 * Mixin to give us a hook to extractRenderState the PartyOverlay below the Chat
 *
 * @author Qu
 * @since 2022-02-22
 */
@Mixin(Gui.class)
public class GuiMixin {
    @Inject(method = "extractCameraOverlays", at = @At("HEAD"))
    private void beforeChatHook(GuiGraphicsExtractor context, DeltaTracker tickCounter, CallbackInfo ci) {
        CobblemonClient.INSTANCE.beforeChatRender(context, tickCounter);
    }

    @ModifyExpressionValue(
        method = "extractRenderState",
        at = @At(value = "FIELD", target = "Lnet/minecraft/client/Options;hideGui:Z", opcode = Opcodes.GETFIELD)
    )
    private boolean cobblemon$hideGuiWhileScanningPokedex(boolean original) {
        if (original) {
            return true;
        }

        return CobblemonClient.INSTANCE.getPokedexUsageContext().getScanningGuiOpen()
            && Minecraft.getInstance().options.getCameraType().isFirstPerson();
    }
}
