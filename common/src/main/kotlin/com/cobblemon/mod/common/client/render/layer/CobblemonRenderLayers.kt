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
import net.minecraft.client.renderer.RenderType
import net.minecraft.resources.ResourceLocation
import java.util.function.BiFunction
import java.util.function.Function
import net.minecraft.Util
import net.minecraft.client.renderer.RenderStateShard
import net.minecraft.client.renderer.RenderStateShard.*

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

    val ENTITY_TRANSLUCENT: BiFunction<ResourceLocation, Boolean, RenderType> = Util.memoize { texture: ResourceLocation, affectsOutline: kotlin.Boolean ->
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

    val ENTITY_CUTOUT: Function<ResourceLocation, RenderType> = Util.memoize { texture: ResourceLocation ->
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

    /**
     * Flat translucent (position + color) layer for the CobblePedia structure preview's approximate
     * water/lava volumes. Back-face culling is ON so only outward-facing faces draw (no seeing through
     * to the far/inner faces of the water body), and upload-sorting is OFF (the translucent sort
     * reorders faces by view angle under the preview's flipped/scaled GUI pose).
     */
    val STRUCTURE_PREVIEW_FLUID: RenderType = RenderType.create(
        "cobblemon_structure_preview_fluid",
        DefaultVertexFormat.POSITION_COLOR,
        VertexFormat.Mode.QUADS,
        1536,
        false,
        false,
        RenderType.CompositeState.builder()
            .setShaderState(POSITION_COLOR_SHADER)
            .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
            .createCompositeState(false)
    )

    /**
     * Translucent, fullbright, no-cull layer that ignores depth testing so the quad always renders
     * on top of the world geometry. Used by the Pokédex scan indicator so it's visible from any angle.
     */
    val SCAN_INDICATOR: Function<ResourceLocation, RenderType> = Util.memoize { texture: ResourceLocation ->
        val multiPhaseParameters =
            RenderType.CompositeState.builder()
                // Emissive shader renders fullbright and ignores lightmap + directional diffuse shading,
                // so the icon stays uniformly bright regardless of world light or viewing angle.
                .setShaderState(RENDERTYPE_ENTITY_TRANSLUCENT_EMISSIVE_SHADER)
                .setTextureState(TextureStateShard(texture, false, false))
                .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                .setCullState(NO_CULL)
                .setLightmapState(LightmapStateShard(false))
                .setOverlayState(OVERLAY)
                .setDepthTestState(NO_DEPTH_TEST)
                .setWriteMaskState(COLOR_WRITE)
                .createCompositeState(false)

        RenderType.create(
            "cobblemon_scan_indicator",
            DefaultVertexFormat.NEW_ENTITY,
            VertexFormat.Mode.QUADS,
            256,
            false,
            true,
            multiPhaseParameters
        )
    };

}