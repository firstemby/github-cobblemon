/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.mixin.camera;

import java.util.HashSet;
import java.util.Set;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.cobblemon.mod.common.api.camera.CameraManager;
import com.cobblemon.mod.common.api.camera.CameraManager.ChunkViewUpdate;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.logging.LogUtils;

import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ChunkTrackingView;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import org.slf4j.Logger;

@Mixin(value = ChunkMap.class, priority = 1100)
public abstract class ChunkMapMixin {
    private static final Logger COBBLEMON$CAMERA_LOGGER = LogUtils.getLogger();

    @Shadow
    protected abstract void markChunkPendingToSend(ServerPlayer player, ChunkPos pos);

    @Shadow
    private static void markChunkPendingToSend(ServerPlayer player, LevelChunk chunk) {}

    @Shadow
    private static void dropChunk(ServerPlayer player, ChunkPos chunkPos) {}

    @Inject(method = "updateChunkTracking", at = @At("HEAD"))
    private void cobblemon$handleCameraChunkUpdates(ServerPlayer player, CallbackInfo ci) {
        ChunkViewUpdate update = CameraManager.consumeChunkViewUpdate(player);

        if (update != null) {
            ChunkTrackingView playerView = player.getChunkTrackingView();
            ChunkTrackingView current = update.getCurrent();
            ChunkTrackingView previous = update.getPrevious();

            if (current != null) {
                ChunkTrackingView.difference(playerView, current, chunkPos -> cobblemon$queueChunkForPlayer(player, chunkPos), chunkPos -> {});
            }

            if (previous != null) {
                Set<ChunkPos> droppingChunks = new HashSet<>();

                ChunkTrackingView targetComparison = current != null ? current : playerView;
                ChunkTrackingView.difference(previous, targetComparison, chunkPos -> {}, droppingChunks::add);

                for (ChunkPos chunkPos : droppingChunks) {
                    if (playerView == null || !playerView.contains(chunkPos)) {
                        dropChunk(player, chunkPos);
                    }
                }
            }
        }
    }

    @Inject(method = "onChunkReadyToSend", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerPlayer;getChunkTrackingView()Lnet/minecraft/server/level/ChunkTrackingView;"))
    private void cobblemon$queueCameraChunk(LevelChunk chunk, CallbackInfo ci, @Local ServerPlayer player) {
        ChunkTrackingView view = CameraManager.getPlayerChunkView(player);

        if (view != null && view.contains(chunk.getPos())) {
            markChunkPendingToSend(player, chunk);
        }
    }

    @Inject(method = "isChunkTracked", at = @At("HEAD"), cancellable = true)
    private void cobblemon$isCameraChunkTracked(ServerPlayer player, int x, int z, CallbackInfoReturnable<Boolean> cir) {
        ChunkTrackingView view = CameraManager.getPlayerChunkView(player);

        if (view != null && view.contains(x, z) && !player.connection.chunkSender.isPending(ChunkPos.asLong(x, z))) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "dropChunk", at = @At("HEAD"), cancellable = true)
    private static void cobblemon$preventCameraChunkDrop(ServerPlayer player, ChunkPos pos, CallbackInfo ci) {
        ChunkTrackingView view = CameraManager.getPlayerChunkView(player);

        if (view != null && view.contains(pos)) {
            ci.cancel();
        }
    }

    private void cobblemon$queueChunkForPlayer(ServerPlayer player, ChunkPos chunkPos) {
        markChunkPendingToSend(player, chunkPos);
    }
}

