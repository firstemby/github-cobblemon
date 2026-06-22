/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.particle;

import com.cobblemon.mod.common.client.render.SnowstormParticle;
import org.joml.Vector3f;

/**
 * Adapts a single glowing {@link SnowstormParticle} to the {@link PhotonicDynamicLight} contract so the
 * Photonic raytracing mod can inject it as a moving light. Position and luminance track the live
 * particle; the color is the particle's own tint, captured at spawn.
 *
 * <p>Color note: most Cobblemon move particles use white sprite textures tinted at runtime (e.g. the
 * ember effect tints a white "cloudyfire" sprite orange), so the particle's resolved tint is the true
 * emitted color — sampling the sprite would just return white. We therefore use
 * {@link SnowstormParticle#currentLightColor()}.</p>
 *
 * <p>Written in Java (like the LambDynamicLights adapter) so {@code particle.getX()} resolves to the
 * particle's accessor rather than the protected {@code Particle.x} field that Kotlin's property
 * synthesis would otherwise bind to. Only instantiated when Photonic is present (see
 * {@link PhotonicDynamicLights#getEnabled()}), so it never couples Cobblemon to the mod.</p>
 */
public final class SnowstormPhotonicLight implements PhotonicDynamicLight {
    private static final int MAX_LUMINANCE = 15;

    private final SnowstormParticle particle;
    // Reused holder for the live tint. NOT captured at construction: the particle's tint isn't
    // resolvable until it has aged a tick (a gradient at age 0 has no node), so reading it in the
    // constructor would throw inside the particle engine. Refreshed lazily in getRed().
    private final Vector3f color = new Vector3f(1.0f, 1.0f, 1.0f);

    public SnowstormPhotonicLight(SnowstormParticle particle) {
        this.particle = particle;
    }

    @Override
    public double getX() {
        return particle.getX();
    }

    @Override
    public double getY() {
        return particle.getY();
    }

    @Override
    public double getZ() {
        return particle.getZ();
    }

    // The consumer (PhotonicLightRegistryMixin) reads red, then green, then blue in that order, so we
    // resolve the live tint once on getRed() and let green/blue read the cached value — one gradient
    // evaluation per light per frame instead of three.
    @Override
    public float getRed() {
        color.set(particle.currentLightColor());
        return color.x;
    }

    @Override
    public float getGreen() {
        return color.y;
    }

    @Override
    public float getBlue() {
        return color.z;
    }

    @Override
    public int getLuminance() {
        return Math.min(MAX_LUMINANCE, particle.currentLightLevel());
    }

    @Override
    public boolean isAlive() {
        return particle.isAlive();
    }
}
