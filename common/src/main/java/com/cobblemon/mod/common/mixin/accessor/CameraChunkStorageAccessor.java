/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.mixin.accessor;

import net.minecraft.client.multiplayer.ClientChunkCache;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.concurrent.atomic.AtomicReferenceArray;

@Mixin(ClientChunkCache.Storage.class)
public interface CameraChunkStorageAccessor {
    @Accessor("chunks")
    AtomicReferenceArray<LevelChunk> cobblemon$getChunks();

    @Accessor("chunkRadius")
    int cobblemon$getChunkRadius();

    @Accessor("chunkCount")
    int cobblemon$getChunkCount();

    @Accessor("chunkCount")
    void cobblemon$setChunkCount(int count);

    @Accessor("viewCenterX")
    int cobblemon$getViewCenterX();

    @Accessor("viewCenterZ")
    int cobblemon$getViewCenterZ();

    @Invoker("getIndex")
    int cobblemon$getIndex(int x, int z);
}
