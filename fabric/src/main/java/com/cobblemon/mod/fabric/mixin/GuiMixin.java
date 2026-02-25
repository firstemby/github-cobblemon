/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.fabric.mixin;

import com.cobblemon.mod.common.api.camera.model.HudElement;
import com.cobblemon.mod.common.client.CobblemonClient;
import com.cobblemon.mod.common.client.camera.ClientCameraController;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.function.BooleanSupplier;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin to give us a hook to render the PartyOverlay below the Chat
 *
 * @author Qu
 * @since 2022-02-22
 */
@Mixin(Gui.class)
public class GuiMixin {
    private Long lastTimeMillis = null;

    @Inject(method = "renderCameraOverlays", at = @At("HEAD"))
    private void beforeChatHook(GuiGraphics context, DeltaTracker tickCounter, CallbackInfo ci) {
        if (lastTimeMillis != null) {
            CobblemonClient.INSTANCE.beforeChatRender(context, tickCounter);
        }
        lastTimeMillis = System.currentTimeMillis();
    }

    //Modifies the render condition passed in the LayeredDraw so the layer doesnt render when pokedex is open
    @WrapOperation(
        method = "<init>(Lnet/minecraft/client/Minecraft;)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/LayeredDraw;add(Lnet/minecraft/client/gui/LayeredDraw;Ljava/util/function/BooleanSupplier;)Lnet/minecraft/client/gui/LayeredDraw;")
    )
    private LayeredDraw cobblemon$dontRenderUiInPokedex(
        LayeredDraw instance,
        LayeredDraw layeredDraw,
        BooleanSupplier renderInner,
        Operation<LayeredDraw> original
    ) {
        BooleanSupplier newBool = () -> {
            if (ClientCameraController.shouldHideHud()) {
                return false;
            }
            if (CobblemonClient.INSTANCE.getPokedexUsageContext().getScanningGuiOpen() && Minecraft.getInstance().options.getCameraType().isFirstPerson()) {
                return false;
            }
            return renderInner.getAsBoolean();
        };
        return original.call(instance, layeredDraw, newBool);
    }

    @Inject(method = "renderCrosshair", at = @At("HEAD"), cancellable = true)
    private void cobblemon$hideCrosshair(GuiGraphics guiGraphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        if (ClientCameraController.shouldHideElement(HudElement.CROSSHAIR)) ci.cancel();
    }

    @Inject(method = "renderHotbarAndDecorations", at = @At("HEAD"), cancellable = true)
    private void cobblemon$hideHotbar(GuiGraphics guiGraphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        if (ClientCameraController.shouldHideElement(HudElement.HOTBAR)) ci.cancel();
    }

    @Inject(method = "renderExperienceBar", at = @At("HEAD"), cancellable = true)
    private void cobblemon$hideExperienceBar(GuiGraphics guiGraphics, int x, CallbackInfo ci) {
        if (ClientCameraController.shouldHideElement(HudElement.EXPERIENCE_BAR)) ci.cancel();
    }

    @Inject(method = "renderExperienceLevel", at = @At("HEAD"), cancellable = true)
    private void cobblemon$hideExperienceLevel(GuiGraphics guiGraphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        if (ClientCameraController.shouldHideElement(HudElement.EXPERIENCE_BAR)) ci.cancel();
    }

    @Inject(method = "renderPlayerHealth", at = @At("HEAD"), cancellable = true)
    private void cobblemon$hidePlayerHealth(GuiGraphics guiGraphics, CallbackInfo ci) {
        if (ClientCameraController.shouldHideElement(HudElement.PLAYER_HEALTH)) ci.cancel();
    }

    @Inject(method = "renderEffects", at = @At("HEAD"), cancellable = true)
    private void cobblemon$hideEffects(GuiGraphics guiGraphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        if (ClientCameraController.shouldHideElement(HudElement.EFFECTS)) ci.cancel();
    }

    @Inject(method = "renderSelectedItemName", at = @At("HEAD"), cancellable = true)
    private void cobblemon$hideSelectedItemName(GuiGraphics guiGraphics, CallbackInfo ci) {
        if (ClientCameraController.shouldHideElement(HudElement.SELECTED_ITEM_NAME)) ci.cancel();
    }
}
