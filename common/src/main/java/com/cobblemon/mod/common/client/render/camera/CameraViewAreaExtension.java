/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.render.camera;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.Level;

public final class CameraViewAreaExtension {
    private static final Long2ObjectOpenHashMap<SectionRenderDispatcher.RenderSection> SECTIONS = new Long2ObjectOpenHashMap<>();
    private static SectionRenderDispatcher sectionRenderDispatcher;
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
        if (cy < minSectionY || cy >= maxSectionY) {
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
        for (int sectionY = minSectionY; sectionY < maxSectionY; sectionY++) {
            long sectionPos = SectionPos.asLong(sectionX, sectionY, sectionZ);
            SectionRenderDispatcher.RenderSection section = SECTIONS.remove(sectionPos);

            if (section != null) {
                section.releaseBuffers();
            }
        }
    }

    public static void clear() {
        for (SectionRenderDispatcher.RenderSection section : SECTIONS.values()) {
            section.releaseBuffers();
        }
        SECTIONS.clear();
    }

    public static int minSectionY() {
        return minSectionY;
    }

    public static int maxSectionY() {
        return maxSectionY;
    }

    private static SectionRenderDispatcher.RenderSection provideSection(long sectionPos) {
        return SECTIONS.computeIfAbsent(sectionPos, CameraViewAreaExtension::createSection);
    }

    private static SectionRenderDispatcher.RenderSection createSection(long sectionPos) {
        if (sectionRenderDispatcher == null) {
            throw new IllegalStateException("SectionRenderDispatcher not initialised");
        }
        BlockPos origin = SectionPos.of(sectionPos).origin();
        return sectionRenderDispatcher.new RenderSection(0, origin.getX(), origin.getY(), origin.getZ());
    }
}
