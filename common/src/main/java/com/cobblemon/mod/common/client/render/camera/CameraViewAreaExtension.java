/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.render.camera;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.UUID;

public final class CameraViewAreaExtension {
    private static final Long2ObjectOpenHashMap<SectionRenderDispatcher.RenderSection> SECTIONS = new Long2ObjectOpenHashMap<>();
    private static final Map<UUID, LongSet> OWNERS = new HashMap<>();
    private static SectionRenderDispatcher sectionRenderDispatcher;
    private static final AtomicLong REVISION = new AtomicLong();
    private static final AtomicLong GRAPH_REVISION = new AtomicLong();
    private static final Set<SectionRenderDispatcher.RenderSection> CAMERA_SECTIONS = Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));
    private static int minSectionY;
    private static int maxSectionY;

    private CameraViewAreaExtension() {
    }

    public static void allChanged(SectionRenderDispatcher dispatcher, Level level) {
        clear();
        sectionRenderDispatcher = dispatcher;
        minSectionY = level.getMinSection();
        maxSectionY = level.getMaxSection();
    }

    public static SectionRenderDispatcher.RenderSection rawFetch(int cx, int cy, int cz, boolean generateNew) {
        if (sectionRenderDispatcher == null || cy < minSectionY || cy >= maxSectionY) {
            return null;
        }

        long sectionPos = SectionPos.asLong(cx, cy, cz);
        return generateNew ? provideSection(sectionPos) : SECTIONS.get(sectionPos);
    }

    public static void setDirty(int cx, int cy, int cz, boolean playerChanged) {
        SectionRenderDispatcher.RenderSection section = rawFetch(cx, cy, cz, false);
        if (section != null) {
            section.setDirty(playerChanged);
        }
    }

    public static void onChunkUnload(int sectionX, int sectionZ) {
        REVISION.incrementAndGet();
        Collection<SectionRenderDispatcher.RenderSection> removed = new ArrayList<>();
        for (int sectionY = minSectionY; sectionY < maxSectionY; sectionY++) {
            long sectionPos = SectionPos.asLong(sectionX, sectionY, sectionZ);
            SectionRenderDispatcher.RenderSection section = SECTIONS.remove(sectionPos);

            if (section != null) {
                removed.add(section);
            }
        }
        releaseBuffers(removed);
    }

    public static void clear() {
        REVISION.incrementAndGet();
        sectionRenderDispatcher = null;
        minSectionY = 0;
        maxSectionY = 0;
        OWNERS.clear();
        Collection<SectionRenderDispatcher.RenderSection> removed = new ArrayList<>(SECTIONS.values());
        SECTIONS.clear();
        releaseBuffers(removed);
    }

    public static long revision() {
        return REVISION.get();
    }

    public static long graphRevision() {
        return GRAPH_REVISION.get();
    }

    public static boolean onSectionCompiled(SectionRenderDispatcher.RenderSection section) {
        // Late camera uploads must not enter the player's graph after their buffers have been released.
        if (!CAMERA_SECTIONS.contains(section)) return false;
        GRAPH_REVISION.incrementAndGet();
        return true;
    }

    public static void retainSections(UUID cameraId, Collection<SectionRenderDispatcher.RenderSection> sections) {
        LongSet positions = new LongOpenHashSet();
        for (SectionRenderDispatcher.RenderSection section : sections) {
            positions.add(SectionPos.asLong(section.getOrigin()));
        }
        LongSet previous = OWNERS.put(cameraId, positions);
        releaseUnownedSections(previous);
    }

    public static void releaseSections(UUID cameraId) {
        releaseUnownedSections(OWNERS.remove(cameraId));
    }

    private static void releaseUnownedSections(LongSet positions) {
        if (positions == null) return;
        Collection<SectionRenderDispatcher.RenderSection> removed = new ArrayList<>();
        for (long position : positions) {
            if (OWNERS.values().stream().anyMatch(owner -> owner.contains(position))) continue;
            SectionRenderDispatcher.RenderSection section = SECTIONS.remove(position);
            if (section != null) removed.add(section);
        }
        releaseBuffers(removed);
    }

    private static void releaseBuffers(Collection<SectionRenderDispatcher.RenderSection> sections) {
        RuntimeException failure = null;
        for (SectionRenderDispatcher.RenderSection section : sections) {
            try {
                section.releaseBuffers();
            } catch (RuntimeException exception) {
                if (failure == null) failure = exception;
                else if (failure != exception) failure.addSuppressed(exception);
            }
        }
        if (failure != null) throw failure;
    }

    private static SectionRenderDispatcher.RenderSection provideSection(long sectionPos) {
        return SECTIONS.computeIfAbsent(sectionPos, CameraViewAreaExtension::createSection);
    }

    private static SectionRenderDispatcher.RenderSection createSection(long sectionPos) {
        if (sectionRenderDispatcher == null) {
            throw new IllegalStateException("SectionRenderDispatcher not initialised");
        }
        BlockPos origin = SectionPos.of(sectionPos).origin();
        SectionRenderDispatcher.RenderSection section = sectionRenderDispatcher.new RenderSection(0, origin.getX(), origin.getY(), origin.getZ());
        CAMERA_SECTIONS.add(section);
        return section;
    }
}
