/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.snowstorm

import com.cobblemon.mod.common.client.render.ShaderCompat
import com.cobblemon.mod.common.client.render.ShaderCompat.ShaderParticleProfile
import com.mojang.blaze3d.platform.GlStateManager
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.BufferBuilder
import com.mojang.blaze3d.vertex.DefaultVertexFormat
import com.mojang.blaze3d.vertex.Tesselator
import com.mojang.blaze3d.vertex.VertexFormat
import net.minecraft.client.particle.ParticleRenderType
import net.minecraft.client.renderer.GameRenderer
import net.minecraft.client.renderer.texture.TextureAtlas
import net.minecraft.client.renderer.texture.TextureManager

/**
 * The four particle render types for snowstorm particles.
 *
 * Under an active shaderpack the blend/depth state of the translucent materials is driven by a
 * per-pack [ShaderParticleProfile] (see [ShaderCompat]) so each pack can be tuned independently —
 * Iris routes our particles through each pack's translucent particle program, and no single blend
 * works everywhere. With no shaderpack the profile is [ShaderCompat.VANILLA_PROFILE], reproducing
 * stock blending exactly, so the no-shader majority path is unchanged.
 *
 * [begin] refreshes the cached profile once per particle batch; the per-particle vertex path reads it
 * via [premultipliesUnderShaders] (see [com.cobblemon.mod.common.client.render.SnowstormParticle.render]).
 */
object ParticleMaterials {
    val ALPHA = object : ParticleRenderType {
        override fun begin(tessellator: Tesselator, textureManager: TextureManager): BufferBuilder {
            val profile = ShaderCompat.refreshParticleProfile()
            RenderSystem.enableBlend()
            RenderSystem.enableCull()
            RenderSystem.depthMask(true)
            RenderSystem.setShader { GameRenderer.getParticleShader() }
            RenderSystem.setShaderTexture(0, TextureAtlas.LOCATION_PARTICLES)
            applyTranslucentBlend(profile)
            return tessellator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.PARTICLE)
        }

        override fun toString() = "ALPHA"
    }

    val ADD = object : ParticleRenderType {
        override fun begin(tessellator: Tesselator, textureManager: TextureManager): BufferBuilder {
            val profile = ShaderCompat.refreshParticleProfile()
            RenderSystem.enableBlend()
            RenderSystem.disableCull()
            // Additive glow generally shouldn't write depth under shaders or the pack treats it as
            // solid and dims it — but some packs need the depth write or the particles vanish, so the
            // choice is per-pack (profile.addWritesDepth). Vanilla profile = true (unchanged look).
            RenderSystem.depthMask(profile.addWritesDepth)
            RenderSystem.setShader { GameRenderer.getParticleShader() }
            RenderSystem.setShaderTexture(0, TextureAtlas.LOCATION_PARTICLES)
            RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE)
            return tessellator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.PARTICLE)
        }

        override fun toString() = "ADD"
    }

    val BLEND = object : ParticleRenderType {
        override fun begin(tessellator: Tesselator, textureManager: TextureManager): BufferBuilder {
            val profile = ShaderCompat.refreshParticleProfile()
            RenderSystem.enableBlend()
            RenderSystem.disableCull()
            RenderSystem.depthMask(profile.blendWritesDepth)
            RenderSystem.setShader { GameRenderer.getParticleShader() }
            RenderSystem.setShaderTexture(0, TextureAtlas.LOCATION_PARTICLES)
            applyTranslucentBlend(profile)
            return tessellator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.PARTICLE)
        }

        override fun toString() = "BLEND"
    }

    val OPAQUE = object : ParticleRenderType {
        override fun begin(tessellator: Tesselator, textureManager: TextureManager): BufferBuilder {
            ShaderCompat.refreshParticleProfile()
            RenderSystem.enableBlend()
            RenderSystem.enableCull()
            RenderSystem.depthMask(true)
            RenderSystem.setShader { GameRenderer.getParticleShader() }
            RenderSystem.setShaderTexture(0, TextureAtlas.LOCATION_PARTICLES)
            RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_COLOR, GlStateManager.DestFactor.ZERO)
            return tessellator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.PARTICLE)
        }

        override fun toString() =  "OPAQUE"
    }

    /**
     * True when [material]'s vertex color must be premultiplied by its alpha before being submitted.
     * Driven by the cached per-pack profile, so the vertex path
     * ([com.cobblemon.mod.common.client.render.SnowstormParticle.render]) and the blend func set in
     * [applyTranslucentBlend] always agree. Only the alpha-composited materials (ALPHA/BLEND) are
     * premultiplied; ADD is additive and OPAQUE is not blended. With no shaderpack the profile is
     * VANILLA (premultiplyAlpha = false), so this returns false and the look is stock.
     */
    fun premultipliesUnderShaders(material: ParticleMaterial): Boolean =
        ShaderCompat.particleProfile().premultiplyAlpha &&
            (material == ParticleMaterial.ALPHA || material == ParticleMaterial.BLEND)

    /**
     * Premultiplied-alpha blend (source already carries rgb*a, so source factor is ONE) when the
     * active pack's profile asks for it; vanilla straight-alpha blend otherwise. Because
     * `ONE*(rgb*a) + (1-a)*dst == a*rgb + (1-a)*dst`, the vanilla profile is byte-identical to stock.
     */
    private fun applyTranslucentBlend(profile: ShaderParticleProfile) {
        if (profile.premultiplyAlpha) {
            RenderSystem.blendFunc(GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA)
        } else {
            RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA)
        }
    }
}
