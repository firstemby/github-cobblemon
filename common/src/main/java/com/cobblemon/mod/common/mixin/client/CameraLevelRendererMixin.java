/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.mixin.client;

import com.cobblemon.mod.common.client.camera.CameraClientChunkCacheExtension;
import com.cobblemon.mod.common.client.render.camera.CameraFeedHandler;
import com.cobblemon.mod.common.client.render.camera.CameraViewAreaExtension;
import com.cobblemon.mod.common.client.util.compat.SodiumCompat;
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.ViewArea;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = LevelRenderer.class, priority = 1100)
public abstract class CameraLevelRendererMixin {
    private static final int CINEMATIC_RENDER_DISTANCE_CHUNKS = 32;
    private static final float CINEMATIC_FOG_DISTANCE = CINEMATIC_RENDER_DISTANCE_CHUNKS * 16;

    @Shadow
    private SectionRenderDispatcher sectionRenderDispatcher;
    @Shadow
    private ClientLevel level;

    @WrapWithCondition(method = "setupRender", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/ViewArea;repositionCamera(DD)V"))
    private boolean cobblemon$shouldRepositionCamera(ViewArea viewArea, double x, double z) {
        return !CameraFeedHandler.isCapturing();
    }

    // Sodium owns its setup pipeline even while vanilla feed sections are substituted.
    @Inject(method = "setupRender", at = @At("HEAD"), cancellable = true)
    private void cobblemon$onSetupRender(Camera camera, Frustum frustum, boolean hasCapturedFrustum, boolean isSpectator, CallbackInfo ci) {
        if (CameraFeedHandler.isCapturing() && !SodiumCompat.getInstance().isActive()) {
            CameraFeedHandler.prepareVisibleSections(frustum);
            ci.cancel();
        }
    }

    @Inject(method = "setLevel", at = @At("HEAD"))
    private void cobblemon$onSetLevel(ClientLevel level, CallbackInfo ci) {
        try {
            if (this.level != level) {
                CameraClientChunkCacheExtension.clear();
            }
        } finally {
            CameraViewAreaExtension.clear();
        }
    }

    @Inject(method = "allChanged", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/SectionOcclusionGraph;waitAndReset(Lnet/minecraft/client/renderer/ViewArea;)V"))
    private void cobblemon$onAllChanged(CallbackInfo ci) {
        CameraViewAreaExtension.allChanged(sectionRenderDispatcher, level);
    }

    @Inject(method = "addRecentlyCompiledSection", at = @At("HEAD"), cancellable = true)
    private void cobblemon$onSectionCompiled(SectionRenderDispatcher.RenderSection section, CallbackInfo ci) {
        if (CameraViewAreaExtension.onSectionCompiled(section)) {
            ci.cancel();
        }
    }

    @Inject(method = "isSectionCompiled", at = @At("HEAD"), cancellable = true)
    private void cobblemon$onIsSectionCompiled(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        if (CameraFeedHandler.isCapturing()) {
            SectionPos sectionPos = SectionPos.of(pos);
            SectionRenderDispatcher.RenderSection section = CameraViewAreaExtension.rawFetch(sectionPos.x(), sectionPos.y(), sectionPos.z(), false);

            if (section != null && section.getCompiled() != SectionRenderDispatcher.CompiledSection.UNCOMPILED) {
                cir.setReturnValue(true);
            }
        }
    }

    @ModifyVariable(method = "renderLevel", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/FogRenderer;setupColor(Lnet/minecraft/client/Camera;FLnet/minecraft/client/multiplayer/ClientLevel;IF)V"), ordinal = 1)
    private float cobblemon$modifyFogRenderDistance(float original) {
        if (CameraFeedHandler.isCapturing())
            return CINEMATIC_FOG_DISTANCE;

        return original;
    }

    // A Marker camera otherwise excludes the local player from vanilla entity rendering.
    @WrapOperation(
            method = "renderLevel",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;getEntity()Lnet/minecraft/world/entity/Entity;")
    )
    private Entity cobblemon$getCameraEntityDuringCapture(Camera camera, Operation<Entity> original) {
        if (CameraFeedHandler.isCapturing()) {
            Entity player = Minecraft.getInstance().player;
            if (player != null) return player;
        }
        return original.call(camera);
    }

    @WrapOperation(
            method = "renderLevel",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;isDetached()Z")
    )
    private boolean cobblemon$isCameraDetachedDuringCapture(Camera camera, Operation<Boolean> original) {
        if (CameraFeedHandler.isCapturing()) {
            return true;
        }
        return original.call(camera);
    }

}
