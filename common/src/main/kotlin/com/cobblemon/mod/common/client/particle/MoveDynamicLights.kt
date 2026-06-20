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
 * Bridge that lets the optional LambDynamicLights integration attach a dynamic light to individual
 * glowing particles without the common particle code referencing the LambDynamicLights API directly.
 *
 * Each particle whose resolved light level is above 0 (and that claims one of its emitter's limited
 * light slots) registers its own light through [sink]. When LambDynamicLights is installed its
 * entrypoint ([com.cobblemon.mod.common.compat.lambdynamiclights.LambDynamicLightsInitializer]) sets
 * [sink]; when it is absent [sink] stays null and the registration call does nothing, so there is no
 * hard dependency.
 */
object MoveDynamicLights {
    @JvmStatic
    var sink: GlowingParticleLightSink? = null
}

/** Receives an individual glowing [SnowstormParticle] so a real dynamic light can be attached to it. */
fun interface GlowingParticleLightSink {
    fun register(particle: SnowstormParticle)
}
