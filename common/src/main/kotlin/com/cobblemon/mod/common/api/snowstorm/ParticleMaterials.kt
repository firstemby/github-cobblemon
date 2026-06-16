/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.snowstorm

import com.cobblemon.mod.common.client.render.ShaderCompat
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

object ParticleMaterials {
    val ALPHA = object : ParticleRenderType {
        override fun begin(tessellator: Tesselator, textureManager: TextureManager): BufferBuilder {
            RenderSystem.enableBlend()
            RenderSystem.enableCull()
            RenderSystem.depthMask(true)
            RenderSystem.setShader { GameRenderer.getParticleShader() }
            RenderSystem.setShaderTexture(0, TextureAtlas.LOCATION_PARTICLES)
            // Premultiplied-alpha blending under an active shaderpack; straight alpha otherwise.
            //
            // WHY: Iris shaderpacks (Complementary Unbound/Reimagined, Bliss, Photon, ...) draw these
            // custom particles through their *translucent* particle program, which composites with a
            // premultiplied-alpha convention. Feeding it our straight-alpha colors makes faded
            // (low-alpha) particles darken toward black instead of fading toward the background — the
            // "gets darker as it fades" symptom. Switching to premultiplied blend (ONE, 1-SRC_ALPHA)
            // AND premultiplying the vertex color by its alpha (see SnowstormParticle.render) matches
            // that convention, so the fade looks ~like vanilla. This is one pack-agnostic code path:
            // it targets the shared premultiplied convention, not any single pack.
            //
            // SAFE FOR THE MAJORITY: with premultiplied vertex color, `ONE * (rgb*a) + (1-a)*dst`
            // equals straight alpha's `a*rgb + (1-a)*dst`, so the no-shader look is byte-identical;
            // the branch only diverges while a shaderpack is active.
            //
            // RESIDUAL LIMITATION: a pack that explicitly declares `blend.gbuffers_particles_translucent`
            // re-imposes its own blend and we cannot override that; and only the vertex alpha is
            // premultiplied (not the texture's per-texel alpha), so very soft sprite edges may read
            // slightly brighter than vanilla. The dominant over-lifetime fade is what this corrects.
            applyTranslucentBlend()
            return tessellator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.PARTICLE)
        }

        override fun toString() = "ALPHA"
    }

    val ADD = object : ParticleRenderType {
        override fun begin(tessellator: Tesselator, textureManager: TextureManager): BufferBuilder {
            RenderSystem.enableBlend()
            RenderSystem.disableCull()
            // Translucent (additive) particles should not write depth. With a shaderpack active
            // (Iris/OptiFine) these custom render types are drawn through the pack's *translucent*
            // particle program; if they also write depth they get treated as solid geometry by the
            // pack's depth-dependent passes (fog, deferred lighting, translucent compositing), which
            // visibly dims the additive glow — the worst-affected symptom. Dropping depth writes only
            // when shaders are active keeps the vanilla (no-shader) look — used by the vast majority
            // of players — byte-for-byte identical.
            //
            // RESIDUAL LIMITATION: the additive blendFunc below (SRC_ALPHA, ONE) is honored by Iris
            // (it swaps the shader program but does not override blend for particles), but a
            // shaderpack still has final authority over how its translucent particle program lights
            // and composites the fragment. We route depth correctly and preserve additive blending,
            // but we cannot force true additive parity against a pack that composites it otherwise.
            RenderSystem.depthMask(!ShaderCompat.shadersActive())
            RenderSystem.setShader { GameRenderer.getParticleShader() }
            RenderSystem.setShaderTexture(0, TextureAtlas.LOCATION_PARTICLES)
            RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE)
            return tessellator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.PARTICLE)
        }

        override fun toString() = "ADD"
    }

    val BLEND = object : ParticleRenderType {
        override fun begin(tessellator: Tesselator, textureManager: TextureManager): BufferBuilder {
            RenderSystem.enableBlend()
            RenderSystem.disableCull()
            // See ADD above: translucent particles drop depth writes only under an active shaderpack
            // so the pack's translucent pass doesn't treat them as solid. No change to the vanilla look.
            RenderSystem.depthMask(!ShaderCompat.shadersActive())
            RenderSystem.setShader { GameRenderer.getParticleShader() }
            RenderSystem.setShaderTexture(0, TextureAtlas.LOCATION_PARTICLES)
            // Premultiplied-alpha under shaders, straight alpha otherwise — see ALPHA for the full
            // rationale. Paired with the vertex-color premultiply in SnowstormParticle.render so the
            // no-shader look is unchanged.
            applyTranslucentBlend()
            return tessellator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.PARTICLE)
        }

        override fun toString() = "BLEND"
    }

    val OPAQUE = object : ParticleRenderType {
        override fun begin(tessellator: Tesselator, textureManager: TextureManager): BufferBuilder {
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
     * True when [material]'s vertex color must be premultiplied by its alpha before being submitted
     * (see [ALPHA]). Only the alpha-composited materials need it, and only while a shaderpack is
     * active; ADD is additive and OPAQUE is not blended, so neither is premultiplied. Kept here so
     * the vertex path ([com.cobblemon.mod.common.client.render.SnowstormParticle.render]) and the
     * blend func set in [applyTranslucentBlend] always agree on which particles are premultiplied.
     */
    fun premultipliesUnderShaders(material: ParticleMaterial): Boolean =
        (material == ParticleMaterial.ALPHA || material == ParticleMaterial.BLEND) && ShaderCompat.shadersActive()

    /**
     * Premultiplied-alpha blend (source already carries rgb*a, so source factor is ONE) while a
     * shaderpack is active; vanilla straight-alpha blend otherwise. See [ALPHA] for why.
     */
    private fun applyTranslucentBlend() {
        if (ShaderCompat.shadersActive()) {
            RenderSystem.blendFunc(GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA)
        } else {
            RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA)
        }
    }
}