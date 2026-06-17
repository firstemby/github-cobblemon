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
 * lights the world around it like its own light source (maximum fidelity). The light level is whatever
 * the particle currently emits ({@link SnowstormParticle#currentLightLevel()} — a flat author-set level
 * or a per-sprite-frame value), so a particle that emits 0 contributes nothing even though it exists.
 *
 * <p>The number of these per emitter is capped (see {@code BedrockParticle.maxLitParticles}); the cap
 * is enforced at particle spawn, not here. This follows the particle as it moves and removes itself
 * when the particle dies.</p>
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
    /** Level cached each tick in hasChanged() so the per-block lightAtPos stays cheap and consistent. */
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
        // Vanilla-style falloff: exactly 1 light level lost per block, so an authored level of N lights
        // a ~N-block radius just like a Minecraft block-light source. We deliberately IGNORE the
        // falloffRatio LambDynamicLights passes (15 / MAX_RADIUS ~= 1.94), which would otherwise
        // compress the falloff into the mod's 7.75-block cap and make every light reach only ~half its
        // level in blocks (i.e. look dimmer than the number implies). Note: the mod still hard-caps
        // reach at MAX_RADIUS (~7.75 blocks), so authored levels above ~8 are clipped at that radius.
        return Math.max(0.0, lum - dist);
    }

    @Override
    public DynamicLightBehavior.BoundingBox getBoundingBox() {
        // The actively-emitting volume is just the particle's block (the API expands by falloff itself).
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
