/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.particle

import com.cobblemon.mod.common.client.render.SnowstormParticle

/**
 * API-free bridge that lets the optional LambDynamicLights integration attach a real dynamic light to
 * individual glowing particles without the common particle code referencing the LambDynamicLights API.
 *
 * Each particle whose resolved light level is > 0 (and that wins one of its emitter's limited light
 * slots) registers its own light through [sink]. When LambDynamicLights is installed, its entrypoint
 * ([com.cobblemon.mod.common.compat.lambdynamiclights.LambDynamicLightsInitializer]) sets [sink] to a
 * handler that adds a per-particle `DynamicLightBehavior`. When it isn't installed [sink] stays null
 * and the registration call is a no-op — so there is no hard dependency.
 */
object MoveDynamicLights {
    @JvmStatic
    var sink: GlowingParticleLightSink? = null
}

/** Receives an individual glowing [SnowstormParticle] so a real dynamic light can be attached to it. */
fun interface GlowingParticleLightSink {
    fun register(particle: SnowstormParticle)
}
