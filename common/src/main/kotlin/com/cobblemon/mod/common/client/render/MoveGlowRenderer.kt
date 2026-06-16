/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.render

import com.cobblemon.mod.common.client.particle.MoveDynamicLights
import com.cobblemon.mod.common.client.particle.ParticleStorm
import com.cobblemon.mod.common.client.render.shader.CobblemonShaders
import com.cobblemon.mod.common.platform.events.PlatformEvents
import com.cobblemon.mod.common.platform.events.RenderEvent
import com.mojang.blaze3d.platform.GlStateManager
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.BufferUploader
import com.mojang.blaze3d.vertex.DefaultVertexFormat
import com.mojang.blaze3d.vertex.Tesselator
import com.mojang.blaze3d.vertex.VertexFormat
import net.minecraft.client.Minecraft
import org.joml.Matrix4f
import org.joml.Vector3f

/**
 * Tier 2 of the move lighting: a **colored** screen-space "dynamic light" for glowing moves, active
 * only when **both LambDynamicLights and a shaderpack (Iris/OptiFine)** are present (per design — Lamb
 * alone gives the monochrome light level via [com.cobblemon.mod.common.compat.lambdynamiclights.SnowstormDynamicLight];
 * this adds the move's color on top).
 *
 * True per-light colored world illumination has no mod API on any pack, so this is a deferred
 * screen-space pass instead: after the world (and shaderpack) has composited, for each glowing
 * [ParticleStorm] it runs a fullscreen additive pass ([CobblemonShaders.MOVE_GLOW_LIGHT]) that reads
 * the scene depth, reconstructs each surface's position, and adds the move's color with distance
 * falloff. The *surfaces* near the move (blocks/entities) are tinted its color — no visible shape —
 * and it works identically on every pack because it runs post-composite on vanilla GL.
 *
 * Runs at [RenderEvent.Stage.POST_LEVEL]. Matrices are captured at the in-pipeline TRANSLUCENT stage
 * (where they're known-good). One pass per glowing storm (moves are few); colors from
 * [ParticleStorm.glowColor], strength from [ParticleStorm.glowIntensity]. Tune [LIGHT_RADIUS]/
 * [MAX_INTENSITY].
 */
object MoveGlowRenderer {

    /** Falloff radius of the colored light, in blocks. */
    private const val LIGHT_RADIUS = 8.0f
    /** Peak additive strength at full [ParticleStorm.glowIntensity]. */
    private const val MAX_INTENSITY = 0.8f

    // World matrices for the frame, captured at TRANSLUCENT (known-good) for the post-composite pass.
    private val modelView = Matrix4f()
    private val inverseProjection = Matrix4f()
    private var captured = false

    fun register() {
        PlatformEvents.RENDER.subscribe { event ->
            when (event.stage) {
                RenderEvent.Stage.TRANSLUCENT -> {
                    modelView.set(event.modelViewMatrix)
                    inverseProjection.set(event.projectionMatrix).invert()
                    captured = true
                }
                RenderEvent.Stage.POST_LEVEL -> render(event)
            }
        }
    }

    private fun render(event: RenderEvent) {
        if (!captured) return
        // Colored layer is gated on BOTH a shaderpack AND LambDynamicLights being present.
        if (!ShaderCompat.shadersActive()) return
        if (MoveDynamicLights.sink == null) return
        if (!CobblemonShaders.isMoveGlowLightReady()) return

        val storms = ParticleStorm.activeStorms.filter { it.isGlowing }
        if (storms.isEmpty()) return

        val minecraft = Minecraft.getInstance()
        val depthTextureId = minecraft.mainRenderTarget.depthTextureId
        val cam = event.camera.position
        val shader = CobblemonShaders.MOVE_GLOW_LIGHT

        RenderSystem.disableDepthTest()
        RenderSystem.depthMask(false)
        RenderSystem.disableCull()
        RenderSystem.enableBlend()
        RenderSystem.blendFunc(GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE) // additive
        RenderSystem.setShader { shader }

        shader.safeGetUniform("InverseProjMat").set(inverseProjection)
        shader.setSampler("DepthSampler", depthTextureId)

        for (storm in storms) {
            try {
                val intensity = storm.glowIntensity() * MAX_INTENSITY
                if (intensity <= 0f) continue
                val colour = storm.glowColor()
                // Emitter position in view space (matches the depth reconstruction's space).
                val viewPos = Vector3f(
                    (storm.getX() - cam.x).toFloat(),
                    (storm.getY() - cam.y).toFloat(),
                    (storm.getZ() - cam.z).toFloat()
                )
                modelView.transformPosition(viewPos)

                shader.safeGetUniform("LightViewPos")?.set(viewPos.x, viewPos.y, viewPos.z)
                shader.safeGetUniform("LightColor")?.set(colour.x, colour.y, colour.z)
                shader.safeGetUniform("LightRadius")?.set(LIGHT_RADIUS)
                shader.safeGetUniform("Intensity")?.set(intensity)

                drawFullscreenQuad()
            } catch (ignored: Exception) {
                // A single misbehaving effect must never break the world-render callback.
            }
        }

        RenderSystem.depthMask(true)
        RenderSystem.enableDepthTest()
        RenderSystem.enableCull()
        RenderSystem.disableBlend()
        RenderSystem.defaultBlendFunc()
    }

    /** A quad covering clip space (-1..1); the vertex shader passes it straight through. */
    private fun drawFullscreenQuad() {
        val buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION)
        buffer.addVertex(-1f, -1f, 0f)
        buffer.addVertex(1f, -1f, 0f)
        buffer.addVertex(1f, 1f, 0f)
        buffer.addVertex(-1f, 1f, 0f)
        val mesh = buffer.build() ?: return
        BufferUploader.drawWithShader(mesh)
    }
}
