/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.camera;

import com.cobblemon.mod.common.platform.events.ClientChunkEvent;
import com.cobblemon.mod.common.platform.events.PlatformEvents;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import kotlin.Unit;
import kotlin.jvm.functions.Function1;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundLevelChunkPacketData;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.function.Consumer;

public class CameraClientChunkCacheExtension {
    private static final int MIN_RENDER_DISTANCE = 2;
    private static final int MAX_RENDER_DISTANCE = 64;
    private static final int RENDER_DISTANCE_MARGIN = 2;

    private static final Long2ObjectOpenHashMap<LevelChunk> CHUNK_MAP = new Long2ObjectOpenHashMap<>();
    private static final Long2ObjectOpenHashMap<LevelChunk> CHUNK_MAP_OTHER_THREADS = new Long2ObjectOpenHashMap<>();
    private static volatile ChunkPos CAMERA_CENTER;

    private CameraClientChunkCacheExtension() {}

    public static void drop(ClientLevel level, ChunkPos chunkPos) {
        if (Minecraft.getInstance().isSameThread()) {
            long chunkPosLong = chunkPos.toLong();
            LevelChunk chunk = CHUNK_MAP.get(chunkPosLong);

            if (chunk != null) {
                modifyChunkMaps(map -> map.remove(chunkPosLong));
                PlatformEvents.CLIENT_CHUNK_UNLOAD.postEvents(new ClientChunkEvent.Unload(chunk, level));
                level.unload(chunk);
            }
        }
    }

    public static LevelChunk getChunk(int x, int z) {
        long chunkPos = ChunkPos.asLong(x, z);

        if (Minecraft.getInstance().isSameThread())
            return CHUNK_MAP.get(chunkPos);
        else {
            synchronized (CHUNK_MAP_OTHER_THREADS) {
                return CHUNK_MAP_OTHER_THREADS.get(chunkPos);
            }
        }
    }

    public static LevelChunk replaceWithPacketData(ClientLevel level, int x, int z, FriendlyByteBuf packetData, CompoundTag chunkTag, Consumer<ClientboundLevelChunkPacketData.BlockEntityTagOutput> tagOutput) {
        if (!Minecraft.getInstance().isSameThread())
            throw new UnsupportedOperationException("replaceWithPacketData called off-thread, this shouldn't happen!");

        ChunkPos chunkPos = new ChunkPos(x, z);
        long longChunkPos = chunkPos.toLong();
        LevelChunk chunk = CHUNK_MAP.get(longChunkPos);

        if (chunk == null) {
            LevelChunk newChunk = new LevelChunk(level, chunkPos);

            chunk = newChunk;
            chunk.replaceWithPacketData(packetData, chunkTag, tagOutput);
            modifyChunkMaps(map -> map.put(longChunkPos, newChunk));
        }

        level.onChunkLoaded(chunkPos);
        PlatformEvents.CLIENT_CHUNK_LOAD.postEvents(new ClientChunkEvent.Load(chunk, level));
        return chunk;
    }

    private static void modifyChunkMaps(Consumer<Long2ObjectOpenHashMap<LevelChunk>> operation) {
        operation.accept(CHUNK_MAP);

        synchronized (CHUNK_MAP_OTHER_THREADS) {
            operation.accept(CHUNK_MAP_OTHER_THREADS);
        }
    }

    public static void clear() {
        CHUNK_MAP.clear();
        CHUNK_MAP_OTHER_THREADS.clear();
        CAMERA_CENTER = null;
    }

    public static boolean hasCameraChunks() {
        if (Minecraft.getInstance().isSameThread()) {
            return !CHUNK_MAP.isEmpty();
        } else {
            synchronized (CHUNK_MAP_OTHER_THREADS) {
                return !CHUNK_MAP_OTHER_THREADS.isEmpty();
            }
        }
    }

    /**
     * Computes render distance using Chebyshev distance with a safety margin.
     */
    public static int getSuggestedRenderDistance(ChunkPos center) {
        int maxDistance = 0;

        if (Minecraft.getInstance().isSameThread()) {
            for (long key : CHUNK_MAP.keySet()) {
                int x = ChunkPos.getX(key);
                int z = ChunkPos.getZ(key);
                int dx = Math.abs(x - center.x);
                int dz = Math.abs(z - center.z);
                maxDistance = Math.max(maxDistance, Math.max(dx, dz));
            }
        } else {
            synchronized (CHUNK_MAP_OTHER_THREADS) {
                for (long key : CHUNK_MAP_OTHER_THREADS.keySet()) {
                    int x = ChunkPos.getX(key);
                    int z = ChunkPos.getZ(key);
                    int dx = Math.abs(x - center.x);
                    int dz = Math.abs(z - center.z);
                    maxDistance = Math.max(maxDistance, Math.max(dx, dz));
                }
            }
        }

        return Math.max(MIN_RENDER_DISTANCE, Math.min(MAX_RENDER_DISTANCE, maxDistance + RENDER_DISTANCE_MARGIN));
    }

    public static void setCameraCenter(ChunkPos center) {
        CAMERA_CENTER = center;
    }

    public static ChunkPos getCameraCenter() {
        return CAMERA_CENTER;
    }
}
