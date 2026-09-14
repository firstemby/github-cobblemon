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
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import java.util.function.BooleanSupplier;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.client.gui.components.BossHealthOverlay;
import net.minecraft.client.gui.components.spectator.SpectatorGui;
import net.minecraft.world.entity.player.Player;
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

    @Inject(method = "renderItemHotbar", at = @At("HEAD"), cancellable = true)
    private void cobblemon$hideItemHotbar(GuiGraphics guiGraphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        if (ClientCameraController.shouldHideElement(HudElement.HOTBAR)) ci.cancel();
    }

    @WrapWithCondition(method = "renderHotbarAndDecorations", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/components/spectator/SpectatorGui;renderHotbar(Lnet/minecraft/client/gui/GuiGraphics;)V"))
    private boolean cobblemon$hideSpectatorHotbar(SpectatorGui instance, GuiGraphics guiGraphics) {
        return !ClientCameraController.shouldHideElement(HudElement.HOTBAR);
    }

    @Inject(method = "renderExperienceBar", at = @At("HEAD"), cancellable = true)
    private void cobblemon$hideExperienceBar(GuiGraphics guiGraphics, int x, CallbackInfo ci) {
        if (ClientCameraController.shouldHideElement(HudElement.EXPERIENCE_BAR)) ci.cancel();
    }

    @Inject(method = "renderExperienceLevel", at = @At("HEAD"), cancellable = true)
    private void cobblemon$hideExperienceLevel(GuiGraphics guiGraphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        if (ClientCameraController.shouldHideElement(HudElement.EXPERIENCE_BAR)) ci.cancel();
    }

    @Inject(method = "renderHearts", at = @At("HEAD"), cancellable = true)
    private void cobblemon$hidePlayerHealth(GuiGraphics guiGraphics, Player player, int x, int y, int height, int regen,
                                            float maxHealth, int lastHealth, int health, int absorption, boolean blink,
                                            CallbackInfo ci) {
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

    // Fabric has no separate air-bubble render call to cancel; other HUD elements mirror NeoForge's layer suppression.

    @Inject(method = "renderChat", at = @At("HEAD"), cancellable = true)
    private void cobblemon$hideChat(GuiGraphics guiGraphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        if (ClientCameraController.shouldHideElement(HudElement.CHAT)) ci.cancel();
    }

    @Inject(method = "renderScoreboardSidebar(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/client/DeltaTracker;)V", at = @At("HEAD"), cancellable = true)
    private void cobblemon$hideScoreboard(GuiGraphics guiGraphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        if (ClientCameraController.shouldHideElement(HudElement.SCOREBOARD)) ci.cancel();
    }

    @Inject(method = "renderTabList", at = @At("HEAD"), cancellable = true)
    private void cobblemon$hidePlayerList(GuiGraphics guiGraphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        if (ClientCameraController.shouldHideElement(HudElement.PLAYER_LIST)) ci.cancel();
    }

    @Inject(method = "renderArmor", at = @At("HEAD"), cancellable = true)
    private static void cobblemon$hideArmor(GuiGraphics guiGraphics, Player player, int y, int heartRows, int height, int x, CallbackInfo ci) {
        if (ClientCameraController.shouldHideElement(HudElement.ARMOR)) ci.cancel();
    }

    @Inject(method = "renderFood", at = @At("HEAD"), cancellable = true)
    private void cobblemon$hideFood(GuiGraphics guiGraphics, Player player, int y, int x, CallbackInfo ci) {
        if (ClientCameraController.shouldHideElement(HudElement.FOOD)) ci.cancel();
    }

    @WrapWithCondition(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/components/BossHealthOverlay;render(Lnet/minecraft/client/gui/GuiGraphics;)V"))
    private boolean cobblemon$hideBossBar(BossHealthOverlay instance, GuiGraphics guiGraphics) {
        return !ClientCameraController.shouldHideElement(HudElement.BOSS_BAR);
    }
}
