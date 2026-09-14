/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.camera;

import com.cobblemon.mod.common.client.render.camera.CameraFeedHandler;
import com.cobblemon.mod.common.client.util.compat.SodiumCompat;
import com.cobblemon.mod.common.platform.events.ClientChunkEvent;
import com.cobblemon.mod.common.platform.events.PlatformEvents;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.SectionPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundLevelChunkPacketData;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.ArrayList;
import java.util.function.Consumer;

public final class CameraClientChunkCacheExtension {
    private static final Long2ObjectOpenHashMap<LevelChunk> CHUNKS = new Long2ObjectOpenHashMap<>();

    private CameraClientChunkCacheExtension() {}

    public static boolean shouldKeepChunk(ChunkPos chunkPos) {
        int renderDistance = Minecraft.getInstance().options.renderDistance().get();
        return CameraChunkVisibilityManager.shouldKeepChunk(chunkPos, renderDistance)
            || CameraFeedHandler.shouldAddChunk(chunkPos, renderDistance);
    }

    public static void retain(LevelChunk chunk) {
        synchronized (CHUNKS) {
            CHUNKS.put(chunk.getPos().toLong(), chunk);
        }
    }

    public static LevelChunk take(ClientLevel level, int x, int z) {
        synchronized (CHUNKS) {
            long position = ChunkPos.asLong(x, z);
            LevelChunk chunk = CHUNKS.get(position);
            return chunk != null && chunk.getLevel() == level ? CHUNKS.remove(position) : null;
        }
    }

    public static void drop(ClientLevel level, ChunkPos chunkPos) {
        LevelChunk chunk = take(level, chunkPos.x, chunkPos.z);
        if (chunk != null) {
            unload(level, chunk);
        }
    }

    public static LevelChunk getChunk(int x, int z) {
        synchronized (CHUNKS) {
            return CHUNKS.get(ChunkPos.asLong(x, z));
        }
    }

    public static LevelChunk replaceWithPacketData(ClientLevel level, int x, int z, FriendlyByteBuf packetData, CompoundTag chunkTag, Consumer<ClientboundLevelChunkPacketData.BlockEntityTagOutput> tagOutput) {
        if (!Minecraft.getInstance().isSameThread()) {
            throw new IllegalStateException("Camera chunk packet applied off the client thread");
        }
        ChunkPos chunkPos = new ChunkPos(x, z);
        LevelChunk chunk = getChunk(x, z);
        if (chunk == null || chunk.getLevel() != level) {
            chunk = new LevelChunk(level, chunkPos);
        }
        chunk.replaceWithPacketData(packetData, chunkTag, tagOutput);
        retain(chunk);
        level.onChunkLoaded(chunkPos);
        SodiumCompat.getInstance().notifyChunkAdded(level, x, z);
        PlatformEvents.CLIENT_CHUNK_LOAD.postEvents(new ClientChunkEvent.Load(chunk, level));
        return chunk;
    }

    public static void prune(ClientLevel level) {
        for (LevelChunk chunk : snapshot()) {
            if (chunk.getLevel() == level && !shouldKeepChunk(chunk.getPos())) {
                try {
                    drop(level, chunk.getPos());
                } finally {
                    queueLightRemoval(level, chunk.getPos());
                }
            }
        }
    }

    public static void clear() {
        ArrayList<LevelChunk> removed;
        synchronized (CHUNKS) {
            removed = new ArrayList<>(CHUNKS.values());
            CHUNKS.clear();
        }
        RuntimeException failure = null;
        for (LevelChunk chunk : removed) {
            ClientLevel level = (ClientLevel) chunk.getLevel();
            try {
                try {
                    unload(level, chunk);
                } finally {
                    queueLightRemoval(level, chunk.getPos());
                }
            } catch (RuntimeException exception) {
                if (failure == null) failure = exception;
                else if (failure != exception) failure.addSuppressed(exception);
            }
        }
        if (failure != null) throw failure;
    }

    public static ArrayList<LevelChunk> snapshot() {
        synchronized (CHUNKS) {
            return new ArrayList<>(CHUNKS.values());
        }
    }

    private static void queueLightRemoval(ClientLevel level, ChunkPos position) {
        level.queueLightUpdate(() -> {
            LevelLightEngine lightEngine = level.getLightEngine();
            lightEngine.setLightEnabled(position, false);
            for (int y = lightEngine.getMinLightSection(); y < lightEngine.getMaxLightSection(); y++) {
                SectionPos section = SectionPos.of(position, y);
                lightEngine.queueSectionData(LightLayer.BLOCK, section, null);
                lightEngine.queueSectionData(LightLayer.SKY, section, null);
            }
            for (int y = level.getMinSection(); y < level.getMaxSection(); y++) {
                lightEngine.updateSectionStatus(SectionPos.of(position, y), true);
            }
        });
    }

    public static void unload(ClientLevel level, LevelChunk chunk) {
        ChunkPos position = chunk.getPos();
        try {
            SodiumCompat.getInstance().notifyChunkRemoved(level, position.x, position.z);
        } finally {
            try {
                PlatformEvents.CLIENT_CHUNK_UNLOAD.postEvents(new ClientChunkEvent.Unload(chunk, level));
            } finally {
                level.unload(chunk);
            }
        }
    }
}
