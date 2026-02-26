/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.mixin.client;

import com.cobblemon.mod.common.api.scheduling.ClientTaskTracker;
import com.cobblemon.mod.common.api.tween.TweenManager;
import com.cobblemon.mod.common.client.camera.GameCamera;
import com.cobblemon.mod.common.client.camera.ClientCameraController;
import com.cobblemon.mod.common.client.render.camera.CameraFeedHandler;
import com.cobblemon.mod.common.client.render.camera.compositor.CompositorManager;
import net.minecraft.client.Camera;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Predicate;

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

    /**
     * Skip the vanilla renderLevel() call when a fullscreen compositor is active since
     * the compositor covers the entire screen with camera feeds.
     * This avoids a full wasted scene render every frame.
     */
    @Redirect(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/GameRenderer;renderLevel(Lnet/minecraft/client/DeltaTracker;)V"))
    private void cobblemon$skipVanillaRenderLevel(GameRenderer instance, DeltaTracker deltaTracker) {
        if (!CompositorManager.isFullscreenActive()) {
            instance.renderLevel(deltaTracker);
        }
    }

    @Inject(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/GameRenderer;tryTakeScreenshotIfNeeded()V"))
    private void cobblemon$afterLevelRendering(DeltaTracker deltaTracker, boolean renderLevel, CallbackInfo ci) {
        // Only capture feeds when a compositor is actively consuming them.
        // This avoids expensive full-resolution renders (especially with Iris)
        // when feeds aren't being displayed.
        boolean compositorNeedsFeeds = CompositorManager.isFullscreenActive()
                || !CompositorManager.INSTANCE.getRequiredFeeds().isEmpty();
        if (compositorNeedsFeeds && CameraFeedHandler.hasFeeds()) {
            CameraFeedHandler.captureFeeds(deltaTracker);
        }
        if (CompositorManager.isFullscreenActive()) {
            CompositorManager.INSTANCE.compositeAll();
            CompositorManager.renderFullscreen();
        }
    }

    @Inject(method = "pick", at = @At("HEAD"), cancellable = true)
    private void cobblemon$preventFramePick(float partialTicks, CallbackInfo ci) {
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
            float delta = (newTime - cobblemon$lastTime) / 1000F;
            ClientTaskTracker.INSTANCE.update(delta);
            TweenManager.Companion.getClient().update(delta);
        }
        cobblemon$lastTime = newTime;
    }

    @ModifyArg(
        method = "pick(Lnet/minecraft/world/entity/Entity;DDF)Lnet/minecraft/world/phys/HitResult;",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/entity/projectile/ProjectileUtil;getEntityHitResult(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;D)Lnet/minecraft/world/phys/EntityHitResult;"
        ),
        index = 4
    )
    private Predicate<Entity> filterPokemonVehicle(Predicate<Entity> original, @Local(ordinal = 0, argsOnly = true) Entity entity) {
        return (entityX) -> !(entity.getVehicle() == entityX && entityX instanceof PokemonEntity) && original.test(entityX);
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
