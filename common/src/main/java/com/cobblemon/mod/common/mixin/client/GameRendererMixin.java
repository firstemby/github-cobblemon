/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.mixin.client;

import com.cobblemon.mod.common.api.scheduling.ClientTaskTracker;
import com.cobblemon.mod.common.client.camera.GameCamera;
import com.cobblemon.mod.common.client.camera.ClientCameraController;
import com.cobblemon.mod.common.client.render.camera.CameraFeedHandler;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {

    @Shadow @Final @Mutable
    private Camera mainCamera;

    @Unique
    private Camera cobblemon$playerCamera;

    @Unique
    private final GameCamera cobblemon$gameCamera = new GameCamera();

    @Unique
    private boolean cobblemon$usingCinematic;

    @Unique
    private long cobblemon$lastTime = -1;

    @Inject(method = "render", at = @At("HEAD"))
    private void cobblemon$swapCamera(DeltaTracker counter, boolean tick, CallbackInfo ci) {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null) {
            cobblemon$restoreCamera();
            return;
        }

        if (ClientCameraController.shouldUseCinematicCamera()) {
            if (!cobblemon$usingCinematic) {
                cobblemon$playerCamera = mainCamera;
                mainCamera = cobblemon$gameCamera;
                cobblemon$usingCinematic = true;
            }
            ClientCameraController.prepareCinematicCamera(client, counter.getGameTimeDeltaPartialTick(tick));
        } else if (cobblemon$usingCinematic) {
            cobblemon$restoreCamera();
        }
    }

    @Inject(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/GameRenderer;tryTakeScreenshotIfNeeded()V"))
    private void cobblemon$afterLevelRendering(DeltaTracker deltaTracker, boolean renderLevel, CallbackInfo ci) {
        //CameraFeedHandler.captureFeeds(deltaTracker);
    }

    @Inject(method = "pick", at = @At("HEAD"), cancellable = true)
    private void securitycraft$preventFramePick(float partialTicks, CallbackInfo ci) {
        if (CameraFeedHandler.isCapturing())
            ci.cancel();
    }

    @Inject(method = "render", at = @At(value = "TAIL"))
    private void cobblemon$afterRender(DeltaTracker counter, boolean tick, CallbackInfo ci) {
        Minecraft client = Minecraft.getInstance();

        long newTime = System.currentTimeMillis();

        if (client.isPaused()) {
            cobblemon$lastTime = newTime;
            return;
        }

        if (cobblemon$lastTime != -1) {
            ClientTaskTracker.INSTANCE.update((newTime - cobblemon$lastTime) / 1000F);
        }
        cobblemon$lastTime = newTime;
    }

    @Unique
    private void cobblemon$restoreCamera() {
        if (cobblemon$usingCinematic) {
            mainCamera = cobblemon$playerCamera;
            cobblemon$playerCamera = null;
            cobblemon$usingCinematic = false;
        }
    }
}
