/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.mixin.client;

import com.cobblemon.mod.common.client.editor.cinematic.viewport.WindowResizeHook;
import com.cobblemon.mod.common.api.scheduling.ClientTaskTracker;
import com.cobblemon.mod.common.client.camera.GameCamera;
import com.cobblemon.mod.common.client.camera.ClientCameraController;
import com.cobblemon.mod.common.client.cinematic.ClientSceneSessionManager;
import com.cobblemon.mod.common.client.render.camera.accessor.CameraFeedRenderState;
import com.cobblemon.mod.common.client.render.camera.CameraFeedHandler;
import com.cobblemon.mod.common.client.render.camera.compositor.CompositorManager;
import net.minecraft.client.Camera;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.function.Predicate;
import java.util.UUID;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin implements CameraFeedRenderState {

    @Shadow private boolean renderHand;
    @Shadow private boolean renderBlockOutline;

    @Override public boolean cobblemonRenderHand() { return renderHand; }
    @Override public boolean cobblemonRenderBlockOutline() { return renderBlockOutline; }

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

        ClientCameraController.advanceFrame(client.isPaused() ? 0f : counter.getRealtimeDeltaTicks() / 20f);

        ClientSceneSessionManager.INSTANCE.getAllSessions().forEach(session -> session.refreshCompositor());

        if (ClientCameraController.shouldUseCinematicCamera()) {
            if (!cobblemon$usingCinematic) {
                cobblemon$playerCamera = mainCamera;
                mainCamera = cobblemon$gameCamera;
                cobblemon$usingCinematic = true;
            }
            cobblemon$gameCamera.prepare(client, counter.getGameTimeDeltaPartialTick(tick));
        } else if (cobblemon$usingCinematic) {
            cobblemon$restoreCamera();
        }
    }

    @Inject(method = "render", at = @At("HEAD"))
    private void cobblemon$editorViewportResizeCheck(DeltaTracker counter, boolean tick, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        ((WindowResizeHook) (Object) mc.getWindow()).cobblemon$checkForOverrideResize();
    }

    // WrapOperation allows Iris/Sodium to share this call site; Redirect claims it exclusively.
    @WrapOperation(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/GameRenderer;renderLevel(Lnet/minecraft/client/DeltaTracker;)V"))
    private void cobblemon$skipVanillaRenderLevel(GameRenderer instance, DeltaTracker deltaTracker, Operation<Void> original) {
        if (!CompositorManager.isFullscreenActive()) {
            original.call(instance, deltaTracker);
        }
    }

    @Inject(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/GameRenderer;tryTakeScreenshotIfNeeded()V"))
    private void cobblemon$afterLevelRendering(DeltaTracker deltaTracker, boolean renderLevel, CallbackInfo ci) {
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

    @Inject(method = "getFov", at = @At("HEAD"), cancellable = true)
    private void cobblemon$overrideCinematicFov(Camera camera, float partialTicks, boolean useFovSetting, CallbackInfoReturnable<Double> cir) {
        UUID capturedId = CameraFeedHandler.getCurrentlyCapturedCamera();
        Float fov;
        if (capturedId != null) {
            CameraFeedHandler.FeedContext context = CameraFeedHandler.getFeedContext(capturedId);
            fov = context != null ? context.getFov() : null;
        } else {
            if (camera != cobblemon$gameCamera || !ClientCameraController.shouldUseCinematicCamera()) return;
            fov = ClientCameraController.getPrimaryFovOverride();
        }
        if (fov != null) {
            cir.setReturnValue(fov.doubleValue());
        }
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
