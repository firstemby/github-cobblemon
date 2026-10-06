/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.render.shader

import com.cobblemon.mod.common.api.types.ElementalTypes
import com.mojang.blaze3d.platform.GlStateManager
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.DefaultVertexFormat
import com.mojang.blaze3d.vertex.VertexFormat
import net.minecraft.Util
import net.minecraft.client.renderer.RenderStateShard
import net.minecraft.client.renderer.RenderType
import net.minecraft.resources.ResourceLocation

data class TeraRender(val overlay: RenderType, val halo: RenderType, val tint: Int)

object TeraOverlay {
    private const val ASPECT = "terastal"

    // Adds the shader's rgb and scales what's already drawn by its alpha, so facets can shade the base model.
    // Destination alpha is left alone
    private val TERA_TRANSPARENCY = RenderStateShard.TransparencyStateShard(
        "cobblemon_tera_transparency",
        {
            RenderSystem.enableBlend()
            RenderSystem.blendFuncSeparate(
                GlStateManager.SourceFactor.ONE,
                GlStateManager.DestFactor.SRC_ALPHA,
                GlStateManager.SourceFactor.ZERO,
                GlStateManager.DestFactor.ONE
            )
        },
        {
            RenderSystem.disableBlend()
            RenderSystem.defaultBlendFunc()
        }
    )

    private val renderType = Util.memoize { texture: ResourceLocation ->
        RenderType.create(
            "cobblemon_tera",
            DefaultVertexFormat.NEW_ENTITY,
            VertexFormat.Mode.QUADS,
            256,
            true,
            true,
            RenderType.CompositeState.builder()
                .setShaderState(RenderStateShard.ShaderStateShard { CobblemonShaders.TERA })
                .setTextureState(RenderStateShard.TextureStateShard(texture, false, false))
                .setTransparencyState(TERA_TRANSPARENCY)
                .setLightmapState(RenderStateShard.LIGHTMAP)
                .setOverlayState(RenderStateShard.OVERLAY)
                .setCullState(RenderStateShard.CULL)
                .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                .createCompositeState(false)
        )
    }

    fun forAspects(aspects: Set<String>, texture: ResourceLocation): TeraRender? {
        val aspect = aspects.firstOrNull { it == ASPECT || it.startsWith("$ASPECT-") } ?: return null
        val hue = ElementalTypes.get(aspect.removePrefix(ASPECT).removePrefix("-"))?.hue
        return TeraRender(renderType.apply(texture), TeraHalo.renderType(texture), 0xFF000000.toInt() or (hue ?: 0xFFFFFF))
    }
}
