/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.mixin.client;

import com.cobblemon.mod.common.client.camera.CameraClientChunkCacheExtension;
import com.cobblemon.mod.common.mixin.accessor.CameraChunkStorageAccessor;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.multiplayer.ClientChunkCache;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundLevelChunkPacketData;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

@Mixin(value = ClientChunkCache.class, priority = 1100)
public abstract class ClientChunkCacheMixin {
    @Shadow
    volatile ClientChunkCache.Storage storage;
    @Shadow
    @Final
    ClientLevel level;
    @Shadow
    @Final
    private LevelChunk emptyChunk;

    @Inject(method = "drop", at = @At("HEAD"), cancellable = true)
    private void cobblemon$onDrop(ChunkPos chunkPos, CallbackInfo ci) {
        if (CameraClientChunkCacheExtension.shouldKeepChunk(chunkPos)) {
            CameraChunkStorageAccessor access = (CameraChunkStorageAccessor) (Object) storage;
            int index = access.cobblemon$getIndex(chunkPos.x, chunkPos.z);
            LevelChunk chunk = access.cobblemon$getChunks().get(index);
            if (chunk != null && chunk.getPos().equals(chunkPos)) {
                cobblemon$retain(access, index, chunk);
            }
            ci.cancel();
        } else {
            CameraClientChunkCacheExtension.drop(level, chunkPos);
        }
    }

    @Inject(method = "updateViewCenter", at = @At("HEAD"))
    private void cobblemon$onViewCenter(int x, int z, CallbackInfo ci) {
        CameraChunkStorageAccessor access = (CameraChunkStorageAccessor) (Object) storage;
        cobblemon$retainOutsideRange(access, x, z, access.cobblemon$getChunkRadius());
    }

    @Inject(method = "updateViewRadius", at = @At("HEAD"))
    private void cobblemon$onViewRadius(int radius, CallbackInfo ci) {
        CameraChunkStorageAccessor access = (CameraChunkStorageAccessor) (Object) storage;
        cobblemon$retainOutsideRange(access, access.cobblemon$getViewCenterX(), access.cobblemon$getViewCenterZ(), Math.max(2, radius) + 3);
    }

    @Inject(method = "updateViewCenter", at = @At("TAIL"))
    private void cobblemon$afterViewCenter(int x, int z, CallbackInfo ci) {
        cobblemon$promoteCameraChunks((CameraChunkStorageAccessor) (Object) storage);
    }

    @Inject(method = "updateViewRadius", at = @At("TAIL"))
    private void cobblemon$afterViewRadius(int radius, CallbackInfo ci) {
        cobblemon$promoteCameraChunks((CameraChunkStorageAccessor) (Object) storage);
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void cobblemon$onTick(BooleanSupplier hasTimeLeft, boolean tickChunks, CallbackInfo ci) {
        CameraClientChunkCacheExtension.prune(level);
    }

    @Inject(method = "replaceWithPacketData", at = @At("HEAD"), cancellable = true)
    private void cobblemon$onReplaceWithPacketData(int x, int z, FriendlyByteBuf buffer, CompoundTag chunkTag, Consumer<ClientboundLevelChunkPacketData.BlockEntityTagOutput> tagOutputConsumer, CallbackInfoReturnable<LevelChunk> cir) {
        if (!storage.inRange(x, z)) {
            if (CameraClientChunkCacheExtension.shouldKeepChunk(new ChunkPos(x, z))) {
                cir.setReturnValue(CameraClientChunkCacheExtension.replaceWithPacketData(level, x, z, buffer, chunkTag, tagOutputConsumer));
            }
            return;
        }
        CameraChunkStorageAccessor access = (CameraChunkStorageAccessor) (Object) storage;
        int index = access.cobblemon$getIndex(x, z);
        LevelChunk previous = access.cobblemon$getChunks().get(index);
        if (previous != null && (previous.getPos().x != x || previous.getPos().z != z)
            && CameraClientChunkCacheExtension.shouldKeepChunk(previous.getPos())) {
            cobblemon$retain(access, index, previous);
        }
    }

    @WrapOperation(method = "replaceWithPacketData", at = @At(value = "NEW", target = "(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/level/ChunkPos;)Lnet/minecraft/world/level/chunk/LevelChunk;"))
    private LevelChunk cobblemon$reuseCameraChunk(Level level, ChunkPos position, Operation<LevelChunk> original) {
        LevelChunk retained = CameraClientChunkCacheExtension.take(this.level, position.x, position.z);
        return retained != null ? retained : original.call(level, position);
    }

    @Inject(method = "replaceBiomes", at = @At("HEAD"), cancellable = true)
    private void cobblemon$onReplaceBiomes(int x, int z, FriendlyByteBuf buffer, CallbackInfo ci) {
        LevelChunk chunk = CameraClientChunkCacheExtension.getChunk(x, z);
        if (chunk != null && chunk.getLevel() == level) {
            chunk.replaceBiomes(buffer);
            ci.cancel();
        }
    }

    @Inject(method = "getChunk(IILnet/minecraft/world/level/chunk/status/ChunkStatus;Z)Lnet/minecraft/world/level/chunk/LevelChunk;", at = @At("RETURN"), cancellable = true)
    private void cobblemon$onGetChunk(int x, int z, ChunkStatus chunkStatus, boolean requireChunk, CallbackInfoReturnable<LevelChunk> cir) {
        if (cir.getReturnValue() == null || cir.getReturnValue() == emptyChunk) {
            LevelChunk chunk = CameraClientChunkCacheExtension.getChunk(x, z);
            if (chunk != null && chunk.getLevel() == level) {
                cir.setReturnValue(chunk);
            }
        }
    }

    @Unique
    private void cobblemon$promoteCameraChunks(CameraChunkStorageAccessor access) {
        AtomicReferenceArray<LevelChunk> chunks = access.cobblemon$getChunks();
        for (LevelChunk chunk : CameraClientChunkCacheExtension.snapshot()) {
            ChunkPos position = chunk.getPos();
            if (chunk.getLevel() != level
                || Math.abs(position.x - access.cobblemon$getViewCenterX()) > access.cobblemon$getChunkRadius()
                || Math.abs(position.z - access.cobblemon$getViewCenterZ()) > access.cobblemon$getChunkRadius()) continue;
            int index = access.cobblemon$getIndex(position.x, position.z);
            LevelChunk previous = chunks.get(index);
            if (previous != null && CameraClientChunkCacheExtension.shouldKeepChunk(previous.getPos())) {
                cobblemon$retain(access, index, previous);
                previous = null;
            }
            CameraClientChunkCacheExtension.take(level, position.x, position.z);
            chunks.set(index, chunk);
            if (previous == null) {
                access.cobblemon$setChunkCount(access.cobblemon$getChunkCount() + 1);
            } else {
                CameraClientChunkCacheExtension.unload(level, previous);
            }
        }
    }

    @Unique
    private void cobblemon$retainOutsideRange(CameraChunkStorageAccessor access, int centerX, int centerZ, int radius) {
        AtomicReferenceArray<LevelChunk> chunks = access.cobblemon$getChunks();
        for (int index = 0; index < chunks.length(); index++) {
            LevelChunk chunk = chunks.get(index);
            if (chunk == null) continue;
            ChunkPos position = chunk.getPos();
            if ((Math.abs(position.x - centerX) > radius || Math.abs(position.z - centerZ) > radius)
                && CameraClientChunkCacheExtension.shouldKeepChunk(position)) {
                cobblemon$retain(access, index, chunk);
            }
        }
    }

    @Unique
    private void cobblemon$retain(CameraChunkStorageAccessor access, int index, LevelChunk chunk) {
        // Detach before loader unload callbacks: lighting and block entities belong to this same LevelChunk.
        if (access.cobblemon$getChunks().compareAndSet(index, chunk, null)) {
            access.cobblemon$setChunkCount(access.cobblemon$getChunkCount() - 1);
            CameraClientChunkCacheExtension.retain(chunk);
        }
    }
}
