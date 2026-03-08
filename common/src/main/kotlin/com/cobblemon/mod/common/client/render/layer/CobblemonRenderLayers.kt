/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.render.layer

import com.cobblemon.mod.common.util.cobblemonResource
import com.mojang.blaze3d.vertex.DefaultVertexFormat
import com.mojang.blaze3d.vertex.VertexFormat
import net.minecraft.resources.Identifier
import java.util.function.BiFunction
import java.util.function.Function
import net.minecraft.util.Util

//TODO RENDER PIPELINES NOW????
object CobblemonRenderLayers {
    val BERRY_LAYER = run {
        val multiPhaseParameters = RenderType.CompositeState.builder()
            .setLightmapState(LIGHTMAP)
            .setShaderState(RENDERTYPE_CUTOUT_SHADER)
            .setTextureState(TextureStateShard(
                cobblemonResource("textures/atlas/berries.png"),
                false,
                true
            ))
            .setCullState(NO_CULL)
            .createCompositeState(true)

        RenderType.create(
            "berries",
            DefaultVertexFormat.BLOCK,
            VertexFormat.Mode.QUADS,
            512,
            true,
            false,
            multiPhaseParameters
        )
    }

    val ENTITY_TRANSLUCENT: BiFunction<Identifier, Boolean, RenderType> = Util.memoize { texture: Identifier, affectsOutline: kotlin.Boolean ->
        var multiPhaseParameters: RenderType.CompositeState =
            RenderType.CompositeState.builder()
                .setShaderState(RENDERTYPE_ENTITY_TRANSLUCENT_SHADER)
                .setTextureState(TextureStateShard(texture, false, false))
                .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                .setCullState(RenderStateShard.NO_CULL)
                .setLightmapState(LIGHTMAP)
                .setOverlayState(OVERLAY)
                .createCompositeState(affectsOutline)

        RenderType.create(
            "entity_translucent",
            DefaultVertexFormat.NEW_ENTITY,
            VertexFormat.Mode.QUADS,
            256,
            true,
            true,
            multiPhaseParameters
        )
    };

    val ENTITY_CUTOUT: Function<Identifier, RenderType> = Util.memoize { texture: Identifier ->
        val multiPhaseParameters =
            RenderType.CompositeState.builder()
                .setShaderState(RENDERTYPE_ENTITY_CUTOUT_SHADER)
                .setTextureState(TextureStateShard(texture, false, false))
                .setTransparencyState(NO_TRANSPARENCY)
                .setLightmapState(LIGHTMAP)
                .setOverlayState(OVERLAY)
                .createCompositeState(true)

        RenderType.create(
            "entity_cutout",
            DefaultVertexFormat.NEW_ENTITY,
            VertexFormat.Mode.QUADS,
            256,
            true,
            false,
            multiPhaseParameters
        )
    };

}
