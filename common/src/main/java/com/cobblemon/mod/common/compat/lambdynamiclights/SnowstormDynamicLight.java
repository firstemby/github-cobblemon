/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.compat.lambdynamiclights;

import com.cobblemon.mod.common.client.particle.ParticleStorm;
import dev.lambdaurora.lambdynlights.api.behavior.DynamicLightBehavior;
import net.minecraft.core.BlockPos;

/**
 * A monochrome dynamic light attached to a glowing Cobblemon particle effect, so the area around a
 * move lights up like any other dynamic light source. The peak level is author-controlled per
 * particle type ({@link ParticleStorm#lightEmission()}), ramped by how many particles are currently
 * alive ({@link ParticleStorm#glowIntensity()}) so it fades in/out with the effect; the light follows
 * the emitter and removes itself when the effect despawns.
 *
 * <p>Vanilla block light is monochrome, so this changes the light <em>level</em> only — never color.</p>
 *
 * <p>Only instantiated by {@link LambDynamicLightsInitializer} (the LambDynamicLights entrypoint), so
 * it is never loaded unless LambDynamicLights is installed.</p>
 */
public final class SnowstormDynamicLight implements DynamicLightBehavior {
    private static final int MAX_LUMINANCE = 15;

    private final ParticleStorm storm;
    private int prevX = Integer.MIN_VALUE;
    private int prevY = Integer.MIN_VALUE;
    private int prevZ = Integer.MIN_VALUE;
    private double prevLuminance = -1.0;

    public SnowstormDynamicLight(ParticleStorm storm) {
        this.storm = storm;
    }

    /** Peak luminance (0..15) for this effect right now: authored level scaled by live presence. */
    private double luminance() {
        int level = Math.min(MAX_LUMINANCE, storm.lightEmission());
        return level * storm.glowIntensity();
    }

    @Override
    public double lightAtPos(BlockPos pos, double falloffRatio) {
        double lum = luminance();
        if (lum <= 0.0) return 0.0;
        double dx = pos.getX() + 0.5 - storm.getX();
        double dy = pos.getY() + 0.5 - storm.getY();
        double dz = pos.getZ() + 0.5 - storm.getZ();
        double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
        // Standard block-light falloff: drops ~1 level per block from the emitter.
        return Math.max(0.0, lum - dist);
    }

    @Override
    public DynamicLightBehavior.BoundingBox getBoundingBox() {
        int r = Math.max(1, Math.min(MAX_LUMINANCE, (int) Math.ceil(luminance())));
        int cx = (int) Math.floor(storm.getX());
        int cy = (int) Math.floor(storm.getY());
        int cz = (int) Math.floor(storm.getZ());
        return new DynamicLightBehavior.BoundingBox(cx - r, cy - r, cz - r, cx + r, cy + r, cz + r);
    }

    @Override
    public boolean hasChanged() {
        int cx = (int) Math.floor(storm.getX());
        int cy = (int) Math.floor(storm.getY());
        int cz = (int) Math.floor(storm.getZ());
        double lum = luminance();
        boolean changed = cx != prevX || cy != prevY || cz != prevZ || Math.abs(lum - prevLuminance) >= 1.0;
        if (changed) {
            prevX = cx;
            prevY = cy;
            prevZ = cz;
            prevLuminance = lum;
        }
        return changed;
    }

    @Override
    public boolean isRemoved() {
        return storm.getDespawned();
    }
}
