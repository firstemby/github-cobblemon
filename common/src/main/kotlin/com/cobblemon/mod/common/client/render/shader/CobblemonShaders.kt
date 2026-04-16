/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.render.shader

import com.cobblemon.mod.common.util.ShaderRegistryData
import com.cobblemon.mod.common.util.cobblemonResource
import com.mojang.blaze3d.pipeline.BlendFunction
import com.mojang.blaze3d.pipeline.ColorTargetState
import com.mojang.blaze3d.pipeline.DepthStencilState
import com.mojang.blaze3d.pipeline.RenderPipeline
import com.mojang.blaze3d.platform.DestFactor
import com.mojang.blaze3d.platform.SourceFactor
import com.mojang.blaze3d.shaders.UniformType
import com.mojang.blaze3d.vertex.DefaultVertexFormat
import com.mojang.blaze3d.vertex.VertexFormat
import net.minecraft.server.packs.resources.ResourceProvider

/**
 * 1.21.11 uses the RenderPipeline system instead of the old ShaderInstance reload hook.
 * Keep the particle material split as prebuilt pipelines and preserve the legacy
 * registration list for loader compatibility.
 */
object CobblemonShaders {
    val SHADERS_TO_REGISTER = mutableListOf<(ResourceProvider) -> ShaderRegistryData>()

    private var initialized = false

    private val PARTICLE_VERTEX = cobblemonResource("core/particle_base")
    private val PARTICLE_ALPHA_FRAGMENT = cobblemonResource("core/particle_cutout")
    private val PARTICLE_ADD_FRAGMENT = cobblemonResource("core/particle_add")

    val PARTICLE_ALPHA: RenderPipeline = particlePipeline(
        location = cobblemonResource("particle_alpha"),
        vertexShader = PARTICLE_VERTEX,
        fragmentShader = PARTICLE_ALPHA_FRAGMENT,
        blend = BlendFunction.TRANSLUCENT
    )

    val PARTICLE_ADD: RenderPipeline = particlePipeline(
        location = cobblemonResource("particle_add"),
        vertexShader = PARTICLE_VERTEX,
        fragmentShader = PARTICLE_ADD_FRAGMENT,
        blend = BlendFunction(SourceFactor.SRC_ALPHA, DestFactor.ONE)
    )

    val PARTICLE_BLEND: RenderPipeline = particlePipeline(
        location = cobblemonResource("particle_blend"),
        vertexShader = PARTICLE_VERTEX,
        fragmentShader = PARTICLE_ADD_FRAGMENT,
        blend = BlendFunction.TRANSLUCENT
    )

    val PARTICLE_OPAQUE: RenderPipeline = particlePipeline(
        location = cobblemonResource("particle_opaque"),
        vertexShader = PARTICLE_VERTEX,
        fragmentShader = PARTICLE_ADD_FRAGMENT,
        blend = BlendFunction(SourceFactor.SRC_COLOR, DestFactor.ZERO)
    )

    fun init() {
        if (this.initialized) {
            return
        }

        this.initialized = true
    }

    private fun particlePipeline(
        location: net.minecraft.resources.Identifier,
        vertexShader: net.minecraft.resources.Identifier,
        fragmentShader: net.minecraft.resources.Identifier,
        blend: BlendFunction
    ): RenderPipeline {
        return RenderPipeline.builder()
            .withLocation(location)
            .withVertexShader(vertexShader)
            .withFragmentShader(fragmentShader)
            .withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER)
            .withUniform("Projection", UniformType.UNIFORM_BUFFER)
            .withUniform("Fog", UniformType.UNIFORM_BUFFER)
            .withSampler("Sampler0")
            .withSampler("Sampler2")
            .withColorTargetState(ColorTargetState(blend))
            .withDepthStencilState(DepthStencilState.DEFAULT)
            .withVertexFormat(DefaultVertexFormat.PARTICLE, VertexFormat.Mode.QUADS)
            .build()
    }
}
