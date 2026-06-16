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
import com.mojang.blaze3d.pipeline.TextureTarget
import com.mojang.blaze3d.platform.GlStateManager
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.BufferUploader
import com.mojang.blaze3d.vertex.DefaultVertexFormat
import com.mojang.blaze3d.vertex.Tesselator
import com.mojang.blaze3d.vertex.VertexFormat
import net.minecraft.client.Minecraft
import net.minecraft.world.phys.Vec3
import org.joml.Matrix4f
import org.joml.Vector3f
import org.lwjgl.opengl.GL14

/**
 * Tier 2 of the move lighting: a **colored** screen-space "dynamic light" for glowing moves, active
 * only when **both LambDynamicLights and a shaderpack (Iris/OptiFine)** are present (Lamb alone gives
 * the monochrome light level via
 * [com.cobblemon.mod.common.compat.lambdynamiclights.SnowstormDynamicLight]; this adds the move color).
 *
 * Colored world illumination has no mod API on any pack, so this is a deferred screen-space effect:
 * after the world (and shaderpack) has composited, each glowing [ParticleStorm] contributes light(s)
 * that read the scene depth, reconstruct each surface's position, and tint it with the move's color
 * and distance falloff. Beams light along the whole emitter->target segment; bursts sample several
 * real particles. The *surfaces* near the move are tinted (no visible shape), identically on every
 * pack because it runs post-composite on vanilla GL.
 *
 * ## Non-additive accumulation
 * All the lights are first drawn into an off-screen [glowTarget] with a **MAX** blend, so overlapping
 * lights/effects keep the **brightest** contribution rather than summing — no additive blow-out to
 * white when many effects (or a dense burst's samples) overlap. That buffer is then composited onto
 * the scene **once** (additive), bounded by a single light's [PER_LIGHT_INTENSITY].
 */
object MoveGlowRenderer {

    /** How many particles to sample per burst emitter as light points (spread evenly). */
    private const val SAMPLES_PER_EMITTER = 10
    /** Falloff radius of each colored light, in blocks. */
    private const val LIGHT_RADIUS = 10.0f
    /** Strength of a single light. Because accumulation is MAX (not additive), this is the actual peak
     *  glow a pixel can receive no matter how many lights overlap. */
    private const val PER_LIGHT_INTENSITY = 0.2f
    /** Safety cap on total light passes per frame (each is a fullscreen pass into the glow buffer). */
    private const val GLOBAL_MAX_LIGHTS = 60

    // World matrices for the frame, captured at TRANSLUCENT (known-good) for the post-composite pass.
    private val modelView = Matrix4f()
    private val inverseProjection = Matrix4f()
    private var captured = false

    // Off-screen buffer the lights MAX-accumulate into before being composited onto the scene once.
    private var glowTarget: TextureTarget? = null

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
        val main = minecraft.mainRenderTarget
        val target = ensureGlowTarget(main.width, main.height)
        val cam = event.camera.position
        val lightShader = CobblemonShaders.MOVE_GLOW_LIGHT

        // --- Pass 1: accumulate all move lights into the off-screen buffer with MAX (no stacking). ---
        target.setClearColor(0f, 0f, 0f, 0f)
        target.clear(Minecraft.ON_OSX)
        target.bindWrite(true)

        RenderSystem.disableDepthTest()
        RenderSystem.depthMask(false)
        RenderSystem.disableCull()
        RenderSystem.enableBlend()
        GlStateManager._blendEquation(GL14.GL_MAX) // result = max(src, dst): brightest light wins, no sum
        RenderSystem.setShader { lightShader }
        lightShader.safeGetUniform("InverseProjMat").set(inverseProjection)
        lightShader.setSampler("DepthSampler", main.depthTextureId) // read the real scene depth
        lightShader.safeGetUniform("LightRadius")?.set(LIGHT_RADIUS)

        var lightsDrawn = 0
        emitters@ for (storm in storms) {
            try {
                val beam = storm.beamEndpoints()
                if (beam != null) {
                    val intensity = PER_LIGHT_INTENSITY * storm.glowIntensity()
                    if (intensity > 0f) {
                        val a = viewSpace(beam.first.x, beam.first.y, beam.first.z, cam)
                        val b = viewSpace(beam.second.x, beam.second.y, beam.second.z, cam)
                        val colour = storm.glowColor()
                        lightShader.safeGetUniform("LightViewPosA")?.set(a.x, a.y, a.z)
                        lightShader.safeGetUniform("LightViewPosB")?.set(b.x, b.y, b.z)
                        lightShader.safeGetUniform("LightColor")?.set(colour.x, colour.y, colour.z)
                        lightShader.safeGetUniform("Intensity")?.set(intensity)
                        drawFullscreenQuad()
                        if (++lightsDrawn >= GLOBAL_MAX_LIGHTS) break@emitters
                    }
                } else {
                    val particles = storm.particles
                    if (particles.isEmpty()) continue
                    val samples = minOf(SAMPLES_PER_EMITTER, particles.size)
                    val step = (particles.size / samples).coerceAtLeast(1)
                    for (s in 0 until samples) {
                        val particle = particles.getOrNull(s * step) ?: continue
                        if (particle.invisible) continue
                        val colour = particle.lastColor
                        val alpha = colour.w
                        if (alpha <= 0.01f) continue
                        val p = viewSpace(particle.getX(), particle.getY(), particle.getZ(), cam)
                        lightShader.safeGetUniform("LightViewPosA")?.set(p.x, p.y, p.z)
                        lightShader.safeGetUniform("LightViewPosB")?.set(p.x, p.y, p.z)
                        lightShader.safeGetUniform("LightColor")?.set(colour.x, colour.y, colour.z)
                        lightShader.safeGetUniform("Intensity")?.set(PER_LIGHT_INTENSITY * alpha)
                        drawFullscreenQuad()
                        if (++lightsDrawn >= GLOBAL_MAX_LIGHTS) break@emitters
                    }
                }
            } catch (ignored: Exception) {
                // A single misbehaving effect must never break the world-render callback.
            }
        }

        // --- Pass 2: composite the accumulated glow onto the scene once (additive). ---
        GlStateManager._blendEquation(GL14.GL_FUNC_ADD)
        main.bindWrite(true)
        RenderSystem.blendFunc(GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE)
        val compositeShader = CobblemonShaders.MOVE_GLOW_COMPOSITE
        RenderSystem.setShader { compositeShader }
        compositeShader.setSampler("GlowSampler", target.colorTextureId)
        drawFullscreenQuad()

        // Restore neutral GL state.
        RenderSystem.depthMask(true)
        RenderSystem.enableDepthTest()
        RenderSystem.enableCull()
        RenderSystem.disableBlend()
        RenderSystem.defaultBlendFunc()
    }

    private fun ensureGlowTarget(width: Int, height: Int): TextureTarget {
        var target = glowTarget
        if (target == null) {
            target = TextureTarget(width, height, false, Minecraft.ON_OSX)
            glowTarget = target
        } else if (target.width != width || target.height != height) {
            target.resize(width, height, Minecraft.ON_OSX)
        }
        return target
    }

    /** World position -> view space (camera-relative, then the world model-view), for the shader. */
    private fun viewSpace(wx: Double, wy: Double, wz: Double, cam: Vec3): Vector3f {
        val v = Vector3f((wx - cam.x).toFloat(), (wy - cam.y).toFloat(), (wz - cam.z).toFloat())
        modelView.transformPosition(v)
        return v
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
