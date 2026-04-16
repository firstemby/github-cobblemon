/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.snowstorm

import com.cobblemon.mod.common.client.render.shader.CobblemonShaders
import net.minecraft.client.particle.SingleQuadParticle
import net.minecraft.client.particle.ParticleRenderType
import net.minecraft.client.renderer.texture.TextureAtlas

object ParticleMaterials {
    val ALPHA: ParticleRenderType = ParticleRenderType("cobblemon:alpha")
    val ADD: ParticleRenderType = ParticleRenderType("cobblemon:add")
    val BLEND: ParticleRenderType = ParticleRenderType("cobblemon:blend")
    val OPAQUE: ParticleRenderType = ParticleRenderType("cobblemon:opaque")

    private val ALPHA_LAYER = SingleQuadParticle.Layer(false, TextureAtlas.LOCATION_PARTICLES, CobblemonShaders.PARTICLE_ALPHA)
    private val ADD_LAYER = SingleQuadParticle.Layer(false, TextureAtlas.LOCATION_PARTICLES, CobblemonShaders.PARTICLE_ADD)
    private val BLEND_LAYER = SingleQuadParticle.Layer(false, TextureAtlas.LOCATION_PARTICLES, CobblemonShaders.PARTICLE_BLEND)
    private val OPAQUE_LAYER = SingleQuadParticle.Layer(false, TextureAtlas.LOCATION_PARTICLES, CobblemonShaders.PARTICLE_OPAQUE)

    fun group(material: ParticleMaterial): ParticleRenderType {
        return when (material) {
            ParticleMaterial.ALPHA -> ALPHA
            ParticleMaterial.OPAQUE -> OPAQUE
            ParticleMaterial.BLEND -> BLEND
            ParticleMaterial.ADD -> ADD
        }
    }

    fun layer(material: ParticleMaterial): SingleQuadParticle.Layer {
        return when (material) {
            ParticleMaterial.ALPHA -> ALPHA_LAYER
            ParticleMaterial.ADD -> ADD_LAYER
            ParticleMaterial.BLEND -> BLEND_LAYER
            ParticleMaterial.OPAQUE -> OPAQUE_LAYER
        }
    }
}
