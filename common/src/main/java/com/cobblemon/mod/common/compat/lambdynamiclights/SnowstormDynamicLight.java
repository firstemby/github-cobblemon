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
import net.minecraft.world.phys.Vec3;

/**
 * A (monochrome) dynamic light attached to a glowing Cobblemon particle effect, so the area around a
 * move lights up like any other dynamic light source. Brightness scales with how many particles the
 * effect currently has alive ({@link ParticleStorm#glowIntensity()}), and the light follows the live
 * particles' center (so a beam/AoE lights where the particles actually are, not the emitter origin on
 * the Pokémon). It removes itself when the effect despawns.
 *
 * <p>Vanilla block light is monochrome, so this changes the light <em>level</em> only — the move's
 * color is added separately by the screen-space colored pass ({@code MoveGlowRenderer}) when Iris is
 * also present.</p>
 *
 * <p>Only instantiated by {@link LambDynamicLightsInitializer} (the LambDynamicLights entrypoint), so
 * it is never loaded unless LambDynamicLights is installed.</p>
 */
public final class SnowstormDynamicLight implements DynamicLightBehavior {
    /** Peak block-light level (0..15) at full intensity. Keep modest — this is real light. */
    private static final int MAX_LUMINANCE = 8;

    private final ParticleStorm storm;
    // Cached each hasChanged() so lightAtPos()/getBoundingBox() don't recompute the centroid per block.
    private double centerX;
    private double centerY;
    private double centerZ;
    private double luminance;
    private int prevX = Integer.MIN_VALUE;
    private int prevY = Integer.MIN_VALUE;
    private int prevZ = Integer.MIN_VALUE;
    private double prevLuminance = -1.0;

    public SnowstormDynamicLight(ParticleStorm storm) {
        this.storm = storm;
        this.centerX = storm.getX();
        this.centerY = storm.getY();
        this.centerZ = storm.getZ();
        this.luminance = storm.glowIntensity() * MAX_LUMINANCE;
    }

    /** Refreshes the cached center (live-particle centroid, fallback to emitter origin) and luminance. */
    private void updateState() {
        Vec3 centroid = storm.liveParticleCentroid();
        if (centroid != null) {
            centerX = centroid.x;
            centerY = centroid.y;
            centerZ = centroid.z;
        } else {
            centerX = storm.getX();
            centerY = storm.getY();
            centerZ = storm.getZ();
        }
        luminance = storm.glowIntensity() * MAX_LUMINANCE;
    }

    @Override
    public double lightAtPos(BlockPos pos, double falloffRatio) {
        if (luminance <= 0.0) return 0.0;
        double dx = pos.getX() + 0.5 - centerX;
        double dy = pos.getY() + 0.5 - centerY;
        double dz = pos.getZ() + 0.5 - centerZ;
        double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
        // Standard block-light falloff: drops ~1 level per block from the center.
        return Math.max(0.0, luminance - dist);
    }

    @Override
    public DynamicLightBehavior.BoundingBox getBoundingBox() {
        int r = Math.max(1, Math.min(MAX_LUMINANCE, (int) Math.ceil(luminance)));
        int cx = (int) Math.floor(centerX);
        int cy = (int) Math.floor(centerY);
        int cz = (int) Math.floor(centerZ);
        return new DynamicLightBehavior.BoundingBox(cx - r, cy - r, cz - r, cx + r, cy + r, cz + r);
    }

    @Override
    public boolean hasChanged() {
        updateState();
        int cx = (int) Math.floor(centerX);
        int cy = (int) Math.floor(centerY);
        int cz = (int) Math.floor(centerZ);
        boolean changed = cx != prevX || cy != prevY || cz != prevZ || Math.abs(luminance - prevLuminance) >= 1.0;
        if (changed) {
            prevX = cx;
            prevY = cy;
            prevZ = cz;
            prevLuminance = luminance;
        }
        return changed;
    }

    @Override
    public boolean isRemoved() {
        return storm.getDespawned();
    }
}
