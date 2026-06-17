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
 * A monochrome dynamic light for beam-like (targeted) Cobblemon particle effects. Where
 * {@link SnowstormDynamicLight} is a point light at the emitter, this lights the whole segment from
 * the emitter (caster) to the target: {@link #lightAtPos} uses the distance to the nearest point on
 * that segment, so a long beam brightens evenly along its path instead of only at one end.
 *
 * <p>Peak level is author-controlled ({@link ParticleStorm#lightEmission()}) and ramped by live
 * presence ({@link ParticleStorm#glowIntensity()}). If the target endpoint disappears it falls back
 * to lighting just the emitter point. Removes itself when the effect despawns.</p>
 *
 * <p>Only instantiated by {@link LambDynamicLightsInitializer}, so it is never loaded unless
 * LambDynamicLights is installed. Note: a long beam's bounding box can be large, so keep authored
 * light levels modest for beams — {@link #hasChanged} only re-lights on block-level movement.</p>
 */
public final class SnowstormBeamLight implements DynamicLightBehavior {
    private static final int MAX_LUMINANCE = 15;

    private final ParticleStorm storm;
    private int prevAx = Integer.MIN_VALUE, prevAy = Integer.MIN_VALUE, prevAz = Integer.MIN_VALUE;
    private int prevBx = Integer.MIN_VALUE, prevBy = Integer.MIN_VALUE, prevBz = Integer.MIN_VALUE;
    private double prevLuminance = -1.0;

    public SnowstormBeamLight(ParticleStorm storm) {
        this.storm = storm;
    }

    /** Peak luminance (0..15) for this effect right now: authored level scaled by live presence. */
    private double luminance() {
        int level = Math.min(MAX_LUMINANCE, storm.lightEmission());
        return level * storm.glowIntensity();
    }

    /** Far endpoint, or the emitter itself if there is no live target (degenerates to a point). */
    private Vec3 endPoint() {
        Vec3 target = storm.beamTarget();
        return target != null ? target : new Vec3(storm.getX(), storm.getY(), storm.getZ());
    }

    /** Squared distance from a block-center to the emitter->target segment. */
    private double distSqToSegment(double px, double py, double pz) {
        double ax = storm.getX(), ay = storm.getY(), az = storm.getZ();
        Vec3 b = endPoint();
        double abx = b.x - ax, aby = b.y - ay, abz = b.z - az;
        double lenSq = abx * abx + aby * aby + abz * abz;
        double t = lenSq <= 1.0e-6 ? 0.0
                : ((px - ax) * abx + (py - ay) * aby + (pz - az) * abz) / lenSq;
        if (t < 0.0) t = 0.0; else if (t > 1.0) t = 1.0;
        double cx = ax + t * abx, cy = ay + t * aby, cz = az + t * abz;
        double dx = px - cx, dy = py - cy, dz = pz - cz;
        return dx * dx + dy * dy + dz * dz;
    }

    @Override
    public double lightAtPos(BlockPos pos, double falloffRatio) {
        double lum = luminance();
        if (lum <= 0.0) return 0.0;
        double dist = Math.sqrt(distSqToSegment(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5));
        return Math.max(0.0, lum - dist);
    }

    @Override
    public DynamicLightBehavior.BoundingBox getBoundingBox() {
        int r = Math.max(1, Math.min(MAX_LUMINANCE, (int) Math.ceil(luminance())));
        Vec3 b = endPoint();
        int minX = (int) Math.floor(Math.min(storm.getX(), b.x)) - r;
        int minY = (int) Math.floor(Math.min(storm.getY(), b.y)) - r;
        int minZ = (int) Math.floor(Math.min(storm.getZ(), b.z)) - r;
        int maxX = (int) Math.ceil(Math.max(storm.getX(), b.x)) + r;
        int maxY = (int) Math.ceil(Math.max(storm.getY(), b.y)) + r;
        int maxZ = (int) Math.ceil(Math.max(storm.getZ(), b.z)) + r;
        return new DynamicLightBehavior.BoundingBox(minX, minY, minZ, maxX, maxY, maxZ);
    }

    @Override
    public boolean hasChanged() {
        int ax = (int) Math.floor(storm.getX());
        int ay = (int) Math.floor(storm.getY());
        int az = (int) Math.floor(storm.getZ());
        Vec3 b = endPoint();
        int bx = (int) Math.floor(b.x);
        int by = (int) Math.floor(b.y);
        int bz = (int) Math.floor(b.z);
        double lum = luminance();
        boolean changed = ax != prevAx || ay != prevAy || az != prevAz
                || bx != prevBx || by != prevBy || bz != prevBz
                || Math.abs(lum - prevLuminance) >= 1.0;
        if (changed) {
            prevAx = ax; prevAy = ay; prevAz = az;
            prevBx = bx; prevBy = by; prevBz = bz;
            prevLuminance = lum;
        }
        return changed;
    }

    @Override
    public boolean isRemoved() {
        return storm.getDespawned();
    }
}
