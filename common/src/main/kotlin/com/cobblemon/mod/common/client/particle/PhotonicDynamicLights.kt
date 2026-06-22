/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.particle

import java.util.concurrent.ConcurrentHashMap

/**
 * Cobblemon-side registry of moving dynamic lights (glowing move particles, and later other emitters)
 * exposed to the optional Photonic raytracing mod.
 *
 * Unlike LambDynamicLights (which only edits the vanilla lightmap), Photonic raytraces real light
 * sources and has NO public API to push per-frame point lights. So instead of pushing into Photonic,
 * Cobblemon keeps the set of currently-active dynamic lights here, and a Photonic-targeting mixin
 * (loaded only when "photonics" is present) READS this set each light-registry build and injects the
 * entries into Photonic's light grid. When Photonic is absent nothing reads this set; [enabled] stays
 * false and [register] is a cheap no-op, so there is no hard dependency and no overhead.
 *
 * The set is concurrent because Photonic compiles its light registry on a background thread while the
 * client thread spawns/expires particles.
 *
 * @see com.cobblemon.mod.common.client.render.SnowstormParticle for the registration site
 * @see PhotonicDynamicLight for the per-light contract the mixin reads
 */
object PhotonicDynamicLights {
    /** Set true by the Photonic mixin during its init, once it confirms it can read this registry. */
    @JvmStatic
    @Volatile
    var enabled: Boolean = false

    private val lights = ConcurrentHashMap.newKeySet<PhotonicDynamicLight>()

    /** Registers a moving light. No-op (and unreferenced) unless Photonic is present and active. */
    @JvmStatic
    fun register(light: PhotonicDynamicLight) {
        if (enabled) lights.add(light)
    }

    /**
     * Snapshot of the currently-alive lights, pruning any that have expired. Called by the Photonic
     * mixin when it rebuilds its light grid. Pruning here (rather than on a tick) keeps the dead-light
     * cleanup tied to actual consumption.
     */
    @JvmStatic
    fun activeSnapshot(): List<PhotonicDynamicLight> {
        if (lights.isEmpty()) return emptyList()
        lights.removeIf { !it.isAlive }
        return lights.toList()
    }

    /** Drops every tracked light (e.g. on world unload). */
    @JvmStatic
    fun clear() {
        lights.clear()
    }
}

/**
 * One moving light the Photonic mixin can read each frame: a world position, an RGB color (0..1,
 * sampled from the source's sprite where applicable), and a 0..15 luminance. [isAlive] returning false
 * marks the light for removal on the next [PhotonicDynamicLights.activeSnapshot].
 */
interface PhotonicDynamicLight {
    val x: Double
    val y: Double
    val z: Double
    /** Linear RGB, 0..1. */
    val red: Float
    val green: Float
    val blue: Float
    /** Vanilla-style light level, 0..15. */
    val luminance: Int
    val isAlive: Boolean
}
