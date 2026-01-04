/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.mixin.client;

import com.cobblemon.mod.common.client.render.camera.CameraFeedHandler;
import com.cobblemon.mod.common.client.render.camera.CameraViewAreaExtension;
import com.cobblemon.mod.common.client.render.camera.accessor.CameraLevelRendererAccessor;
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.ViewArea;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = LevelRenderer.class, priority = 1100)
public abstract class CameraLevelRendererMixin implements CameraLevelRendererAccessor {
    private static final int CINEMATIC_RENDER_DISTANCE_CHUNKS = 32;
    private static final float CINEMATIC_FOG_DISTANCE = CINEMATIC_RENDER_DISTANCE_CHUNKS * 16;

    @Shadow
    @Final
    private Minecraft minecraft;
    @Shadow
    private SectionRenderDispatcher sectionRenderDispatcher;
    @Shadow
    private ClientLevel level;

    @Shadow private Frustum capturedFrustum;

    @Shadow public Frustum cullingFrustum;

    @WrapWithCondition(method = "setupRender", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/ViewArea;repositionCamera(DD)V"))
    private boolean cobblemon$shouldRepositionCamera(ViewArea viewArea, double x, double z) {
        return !CameraFeedHandler.isCapturing();
    }

    @Inject(method = "setupRender", at = @At("HEAD"), cancellable = true)
    private void cobblemon$onSetupRender(Camera camera, Frustum frustum, boolean hasCapturedFrustum, boolean isSpectator, CallbackInfo ci) {
        if (CameraFeedHandler.isCapturing())
            ci.cancel();
    }

    @Inject(method = "allChanged", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/SectionOcclusionGraph;waitAndReset(Lnet/minecraft/client/renderer/ViewArea;)V"))
    private void cobblemon$onAllChanged(CallbackInfo ci) {
        CameraViewAreaExtension.allChanged(sectionRenderDispatcher, level);
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

    @Override
    public Frustum cobblemonGetFrustum() {
            return this.capturedFrustum != null ? this.capturedFrustum : this.cullingFrustum;
    }
}
