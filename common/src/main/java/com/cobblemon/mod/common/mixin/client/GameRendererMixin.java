/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.mixin.client;

import com.cobblemon.mod.common.api.orientation.OrientationController;
import com.cobblemon.mod.common.api.scheduling.ClientTaskTracker;
import com.cobblemon.mod.common.stadium.StadiumClientManager;
import com.cobblemon.mod.common.util.CameraExtensionsKt;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public class GameRendererMixin {
    @Shadow @Final Minecraft minecraft;
    @Shadow float renderDistance;
    @Shadow @Final private LightTexture lightTexture;
    @Shadow private boolean renderHand;

    @Shadow private double getFov(Camera activeRenderInfo, float partialTicks, boolean useFOVSetting) { return 0.0; }
    @Shadow public void resetProjectionMatrix(Matrix4f matrix) {}
    @Shadow public Matrix4f getProjectionMatrix(double fov) { return new Matrix4f(); }
    @Shadow private void renderItemInHand(Camera camera, float partialTick, Matrix4f projectionMatrix) {}
    @Shadow public void pick(float partialTicks) {}

    @Unique
    long lastTime = -1;

    @Inject(
            method = "render",
            at = @At(value = "TAIL")
    )
    public void render(DeltaTracker counter, boolean tick, CallbackInfo ci) {
        Minecraft client = Minecraft.getInstance();
        long newTime = System.currentTimeMillis();
        // Don't play scheduled animations when the game is paused
        if (client.isPaused()) {
            lastTime = newTime;
            return;
        }

        if (lastTime != -1) {
            ClientTaskTracker.INSTANCE.update((newTime - lastTime) / 1000F);
        }
        lastTime = newTime;
    }

    @Inject(method = "renderLevel", at = @At(value = "HEAD"), cancellable = true)
    public void renderLevel(DeltaTracker deltaTracker, CallbackInfo ci) {
        if (StadiumClientManager.INSTANCE.shouldUseStadiumCamera()) {
            float f = deltaTracker.getGameTimeDeltaPartialTick(true);
            this.lightTexture.updateLightTexture(f);
            if (this.minecraft.getCameraEntity() == null) {
                this.minecraft.setCameraEntity(this.minecraft.player);
            }

            this.minecraft.getProfiler().push("camera");
            Camera stadiumCamera = new Camera();
            Entity entity = (Entity) this.minecraft.player;
            stadiumCamera.setup(this.minecraft.level, entity, false, false, 1.0F);

            //adjust camera
            StadiumClientManager.INSTANCE.updateCameraData(f);
            var position = StadiumClientManager.INSTANCE.getCameraPosition();
            stadiumCamera.setPosition(position);

            Quaternionf rotation = StadiumClientManager.INSTANCE.getCameraRotation();
            var forwardVector = rotation.transform(Camera.FORWARDS, new Vector3f());
            var upVector = rotation.transform(Camera.UP, new Vector3f());
            var leftVector = rotation.transform(Camera.LEFT, new Vector3f());
            stadiumCamera.rotation.set(rotation);
            stadiumCamera.xRot = (float)Mth.wrapDegrees(-Math.toDegrees(Math.asin(forwardVector.y)));
            stadiumCamera.yRot = Mth.wrapDegrees((float)-Math.toDegrees(Camera.FORWARDS.angleSigned(forwardVector, Camera.UP)) + 180);
            stadiumCamera.forwards.set(forwardVector);
            stadiumCamera.up.set(upVector);
            stadiumCamera.left.set(leftVector);

            this.renderDistance = (float)(this.minecraft.options.getEffectiveRenderDistance() * 16);
            double d = this.getFov(stadiumCamera, f, true);
            Matrix4f matrix4f = this.getProjectionMatrix(d);

            this.resetProjectionMatrix(matrix4f);
            Quaternionf quaternionf = stadiumCamera.rotation().conjugate(new Quaternionf());
            Matrix4f matrix4f2 = (new Matrix4f()).rotation(quaternionf);
            this.minecraft.levelRenderer.prepareCullFrustum(stadiumCamera.getPosition(), matrix4f2, this.getProjectionMatrix(Math.max(d, (double)(Integer)this.minecraft.options.fov().get())));
            this.minecraft.levelRenderer.renderLevel(deltaTracker, false, stadiumCamera, (GameRenderer) (Object) this, this.lightTexture, matrix4f2, matrix4f);

            this.minecraft.getProfiler().pop();

            ci.cancel();
        }
    }

    @Inject(method = "tick", at = @At(value = "TAIL"))
    public void tick(CallbackInfo ci) {
//        StadiumClientManager.INSTANCE.tickCameraManager();
    }
}
