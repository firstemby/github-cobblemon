/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.mixin.client;

import com.cobblemon.mod.common.client.camera.CameraClientChunkCacheExtension;
import com.cobblemon.mod.common.client.camera.CameraChunkVisibilityManager;
import com.cobblemon.mod.common.client.render.camera.CameraFeedHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientChunkCache;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundLevelChunkPacketData;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.function.Consumer;

@Mixin(value = ClientChunkCache.class, priority = 1100)
public abstract class ClientChunkCacheMixin {
    @Shadow
    volatile ClientChunkCache.Storage storage;
    @Shadow
    @Final
    ClientLevel level;
    /**
     * Intercept updateViewCenter to use camera position when active.
     */
    @Inject(method = "drop", at = @At("HEAD"))
    private void cobblemon$onDrop(ChunkPos chunkPos, CallbackInfo ci) {
        int renderDistance = Minecraft.getInstance().options.renderDistance().get();

        if (CameraChunkVisibilityManager.shouldKeepChunk(chunkPos, renderDistance)
            || CameraFeedHandler.shouldAddChunk(chunkPos, renderDistance)) {
            return;
        }

        CameraClientChunkCacheExtension.drop(level, chunkPos);
    }

    /**
     * Override replaceWithPacketData to accept chunks based on camera position.
     */
    @Inject(method = "replaceWithPacketData", at = @At("HEAD"), cancellable = true)
    public void cobblemon$onReplaceWithPacketData(int x, int z, FriendlyByteBuf buffer, CompoundTag chunkTag, Consumer<ClientboundLevelChunkPacketData.BlockEntityTagOutput> tagOutputConsumer, CallbackInfoReturnable<LevelChunk> cir) {
        int renderDistance = Minecraft.getInstance().options.renderDistance().get();
        ChunkPos chunkPos = new ChunkPos(x, z);
        boolean isInPlayerRange = storage.inRange(x, z);
        boolean shouldAddChunk = CameraChunkVisibilityManager.shouldKeepChunk(chunkPos, renderDistance)
            || CameraFeedHandler.shouldAddChunk(chunkPos, renderDistance);

        if (shouldAddChunk) {
            LevelChunk newChunk = CameraClientChunkCacheExtension.replaceWithPacketData(level, x, z, new FriendlyByteBuf(buffer.copy()), chunkTag, tagOutputConsumer);
            if (!isInPlayerRange)
                cir.setReturnValue(newChunk);
        }
    }

    @Inject(method = "getChunk(IILnet/minecraft/world/level/chunk/status/ChunkStatus;Z)Lnet/minecraft/world/level/chunk/LevelChunk;", at = @At("TAIL"), cancellable = true)
    private void cobblemon$onGetChunk(int x, int z, ChunkStatus chunkStatus, boolean requireChunk, CallbackInfoReturnable<LevelChunk> cir) {
        if (!storage.inRange(x, z)) {
            LevelChunk chunk = CameraClientChunkCacheExtension.getChunk(x, z);

            if (chunk != null)
                cir.setReturnValue(chunk);
        }
    }
}

