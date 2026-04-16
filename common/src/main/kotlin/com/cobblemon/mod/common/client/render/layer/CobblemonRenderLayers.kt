/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.render.layer

import com.cobblemon.mod.common.util.cobblemonResource
import com.mojang.blaze3d.pipeline.BlendFunction
import com.mojang.blaze3d.pipeline.ColorTargetState
import com.mojang.blaze3d.pipeline.DepthStencilState
import com.mojang.blaze3d.pipeline.RenderPipeline
import com.mojang.blaze3d.shaders.UniformType
import com.mojang.blaze3d.vertex.DefaultVertexFormat
import com.mojang.blaze3d.vertex.VertexFormat
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.client.renderer.rendertype.RenderSetup
import net.minecraft.client.renderer.rendertype.RenderType
import net.minecraft.resources.Identifier
import java.util.function.BiFunction
import java.util.function.Function
import net.minecraft.util.Util

object CobblemonRenderLayers {
    // Custom pipeline: same as ENTITY_TRANSLUCENT_EMISSIVE but with depth write ON
    // Fixes water/translucent blocks showing through emissive layers (e.g. Charizard tail flame)
    private val ENTITY_TRANSLUCENT_EMISSIVE_PIPELINE: RenderPipeline = RenderPipeline.builder()
        .withLocation(cobblemonResource("pipeline/entity_translucent_emissive"))
        .withVertexShader(Identifier.withDefaultNamespace("core/entity"))
        .withFragmentShader(Identifier.withDefaultNamespace("core/entity"))
        .withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER)
        .withUniform("Projection", UniformType.UNIFORM_BUFFER)
        .withUniform("Fog", UniformType.UNIFORM_BUFFER)
        .withUniform("Lighting", UniformType.UNIFORM_BUFFER)
        .withShaderDefine("EMISSIVE")
        .withShaderDefine("ALPHA_CUTOUT", 0.1f)
        .withShaderDefine("PER_FACE_LIGHTING")
        .withSampler("Sampler0")
        .withSampler("Sampler1")
        .withVertexFormat(DefaultVertexFormat.ENTITY, VertexFormat.Mode.QUADS)
        .withColorTargetState(ColorTargetState(BlendFunction.TRANSLUCENT))
        .withCull(false)
        .withDepthStencilState(DepthStencilState.DEFAULT) // depth write ON
        .build()

    val BERRY_LAYER: RenderType = RenderType.create(
        "berries",
        RenderSetup.builder(RenderPipelines.ENTITY_CUTOUT)
            .withTexture("Sampler0", cobblemonResource("textures/atlas/berries.png"))
            .useLightmap()
            .useOverlay()
            .affectsCrumbling()
            .setOutline(RenderSetup.OutlineProperty.AFFECTS_OUTLINE)
            .createRenderSetup()
    )

    val ENTITY_TRANSLUCENT: BiFunction<Identifier, Boolean, RenderType> = Util.memoize { texture: Identifier, affectsOutline: Boolean ->
            RenderType.create(
                "entity_translucent",
                RenderSetup.builder(RenderPipelines.ENTITY_TRANSLUCENT)
                    .withTexture("Sampler0", texture)
                    .useLightmap()
                    .useOverlay()
                    .affectsCrumbling()
                    .sortOnUpload()
                    .setOutline(if (affectsOutline) RenderSetup.OutlineProperty.AFFECTS_OUTLINE else RenderSetup.OutlineProperty.NONE)
                    .createRenderSetup()
            )
    }

    val ENTITY_CUTOUT: Function<Identifier, RenderType> = Util.memoize { texture: Identifier ->
            RenderType.create(
                "entity_cutout",
                RenderSetup.builder(RenderPipelines.ENTITY_CUTOUT)
                    .withTexture("Sampler0", texture)
                    .useLightmap()
                    .useOverlay()
                    .affectsCrumbling()
                    .setOutline(RenderSetup.OutlineProperty.AFFECTS_OUTLINE)
                    .createRenderSetup()
            )
    }

    // Custom translucent emissive using cobblemon pipeline (depth write ON, no sortOnUpload)
    // Fixes: cross-pattern face flickering + water/translucent blocks showing through
    val ENTITY_TRANSLUCENT_EMISSIVE: BiFunction<Identifier, Boolean, RenderType> = Util.memoize { texture: Identifier, affectsOutline: Boolean ->
        RenderType.create(
            "cobblemon_entity_translucent_emissive",
            RenderSetup.builder(ENTITY_TRANSLUCENT_EMISSIVE_PIPELINE)
                .withTexture("Sampler0", texture)
                .useOverlay()
                .affectsCrumbling()
                .setOutline(if (affectsOutline) RenderSetup.OutlineProperty.AFFECTS_OUTLINE else RenderSetup.OutlineProperty.NONE)
                .createRenderSetup()
        )
    }
}
