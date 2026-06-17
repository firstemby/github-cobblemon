/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.particle

/**
 * API-free bridge that lets the optional LambDynamicLights integration attach a dynamic light to
 * glowing particle effects without the common particle code referencing the LambDynamicLights API.
 *
 * When LambDynamicLights is installed, its entrypoint
 * ([com.cobblemon.mod.common.compat.lambdynamiclights.LambDynamicLightsInitializer]) sets [sink] to a
 * handler that registers a `DynamicLightBehavior` for the storm. When it isn't installed, [sink] stays
 * null and [ParticleStorm.spawn]'s call is a no-op — so there is no hard dependency.
 */
object MoveDynamicLights {
    @JvmStatic
    var sink: GlowingStormLightSink? = null
}

/** Receives glow-capable [ParticleStorm]s so a (monochrome) dynamic light can be attached to them. */
fun interface GlowingStormLightSink {
    fun register(storm: ParticleStorm)
}
