/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.compat.lambdynamiclights;

import com.cobblemon.mod.common.client.render.SnowstormParticle;
import dev.lambdaurora.lambdynlights.api.behavior.DynamicLightBehavior;
import net.minecraft.core.BlockPos;

/**
 * A real, monochrome dynamic light attached to a single Cobblemon particle, so each glowing particle
 * lights the world around it like its own light source. The level is whatever the particle currently
 * emits ({@link SnowstormParticle#currentLightLevel()}); a particle emitting 0 contributes nothing.
 *
 * <p>How many of these exist per emitter is capped (by the maxLitParticlesPerEmitter config, enforced
 * at particle spawn, not here). This follows the particle as it moves and removes itself when it dies.</p>
 *
 * <p>Only instantiated by {@link LambDynamicLightsInitializer} (the LambDynamicLights entrypoint), so
 * it is never loaded unless LambDynamicLights is installed.</p>
 */
public final class SnowstormParticleLight implements DynamicLightBehavior {
    private static final int MAX_LUMINANCE = 15;

    private final SnowstormParticle particle;
    private int prevX = Integer.MIN_VALUE;
    private int prevY = Integer.MIN_VALUE;
    private int prevZ = Integer.MIN_VALUE;
    private int prevLuminance = -1;
    /** Level cached each tick in hasChanged() so the per block lightAtPos call stays cheap and stable. */
    private double cachedLuminance;

    public SnowstormParticleLight(SnowstormParticle particle) {
        this.particle = particle;
        this.cachedLuminance = Math.min(MAX_LUMINANCE, particle.currentLightLevel());
    }

    @Override
    public double lightAtPos(BlockPos pos, double falloffRatio) {
        double lum = cachedLuminance;
        if (lum <= 0.0) return 0.0;
        double dx = pos.getX() + 0.5 - particle.getX();
        double dy = pos.getY() + 0.5 - particle.getY();
        double dz = pos.getZ() + 0.5 - particle.getZ();
        double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
        // Lose exactly 1 level per block so an authored level of N lights about N blocks, like vanilla
        // block light. We intentionally ignore the falloffRatio LambDynamicLights passes (15 / MAX_RADIUS,
        // about 1.94): applying it would compress the falloff into the mod's 7.75 block cap and make every
        // light reach only about half its level in blocks, looking dimmer than the number implies. The mod
        // still limits reach to MAX_RADIUS (about 7.75 blocks), so levels above 8 are clipped there.
        return Math.max(0.0, lum - dist);
    }

    @Override
    public DynamicLightBehavior.BoundingBox getBoundingBox() {
        // Report only the emitting volume (the particle's own block); the API expands it by falloff.
        int x = (int) Math.floor(particle.getX());
        int y = (int) Math.floor(particle.getY());
        int z = (int) Math.floor(particle.getZ());
        return new DynamicLightBehavior.BoundingBox(x, y, z, x + 1, y + 1, z + 1);
    }

    @Override
    public boolean hasChanged() {
        int x = (int) Math.floor(particle.getX());
        int y = (int) Math.floor(particle.getY());
        int z = (int) Math.floor(particle.getZ());
        int lum = Math.min(MAX_LUMINANCE, particle.currentLightLevel());
        boolean changed = x != prevX || y != prevY || z != prevZ || lum != prevLuminance;
        if (changed) {
            prevX = x;
            prevY = y;
            prevZ = z;
            prevLuminance = lum;
            cachedLuminance = lum;
        }
        return changed;
    }

    @Override
    public boolean isRemoved() {
        return !particle.isAlive();
    }
}
