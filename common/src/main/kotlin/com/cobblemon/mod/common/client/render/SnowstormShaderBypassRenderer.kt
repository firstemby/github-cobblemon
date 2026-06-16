/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.render

import com.cobblemon.mod.common.platform.events.PlatformEvents
import com.cobblemon.mod.common.platform.events.RenderEvent
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.BufferUploader
import com.mojang.blaze3d.vertex.Tesselator
import net.minecraft.client.Minecraft
import org.joml.Matrix4f

/**
 * Renders Cobblemon's snowstorm particles with a vanilla-identical, pack-agnostic look while a
 * shaderpack (Iris/OptiFine) is active.
 *
 * ## Why this exists
 * Iris/OptiFine replace the vanilla particle shader with the shaderpack's translucent-particle
 * program for the duration of the world pass, and every pack composites that program differently —
 * which made our particles render too solid (Complementary), invisible (Photon), or still-darkening
 * (Bliss) no matter how we tuned blend/alpha inside the vanilla [net.minecraft.client.particle.ParticleEngine].
 * The pack has final authority there; we cannot win.
 *
 * The one place a shaderpack does NOT intercept a draw is **after** `LevelRenderer.renderLevel`
 * returns: Iris only overrides shaders while `isRenderingWorld` is true, and it flips that off during
 * its final composite (injected at the world-render RETURN). So we:
 *  1. suppress the vanilla draw of these particles while shaders are active
 *     ([SnowstormParticle.getRenderType] returns NO_RENDER), and
 *  2. redraw them here at [RenderEvent.Stage.POST_LEVEL] (post-composite) with the exact vanilla GL
 *     state from [com.cobblemon.mod.common.api.snowstorm.ParticleMaterials] and the unchanged
 *     [SnowstormParticle.render] quad math.
 *
 * The main framebuffer's depth attachment still holds the full (opaque + translucent) world depth at
 * this point — Iris reuses Minecraft's main depth texture and the composite only copies colour — so
 * particles still occlude correctly behind terrain AND water/glass. They simply don't receive the
 * pack's lighting/fog/bloom, which is exactly the intended "looks like vanilla" result.
 *
 * When no shaderpack is active this renderer does nothing and particles draw normally through the
 * ParticleEngine, so the majority (no-shader) path is completely unchanged.
 *
 * @author Cobblemon
 */
object SnowstormShaderBypassRenderer {

    // The world's model-view and projection for the current frame, captured during the in-pipeline
    // TRANSLUCENT stage (where they are known-good) and reused for the post-composite draw, since
    // the POST_LEVEL event does not carry a reliable camera transform.
    private val modelViewMatrix = Matrix4f()
    private var captured = false

    fun register() {
        PlatformEvents.RENDER.subscribe { event ->
            when (event.stage) {
                RenderEvent.Stage.TRANSLUCENT -> {
                    modelViewMatrix.set(event.modelViewMatrix)
                    captured = true
                }
                RenderEvent.Stage.POST_LEVEL -> render(event)
            }
        }
    }

    private fun render(event: RenderEvent) {
        if (!ShaderCompat.shadersActive() || !captured) return

        val visible = SnowstormParticle.activeParticles.filter { !it.invisible }
        if (visible.isEmpty()) return

        val minecraft = Minecraft.getInstance()
        val camera = event.camera
        val partialTicks = event.tickCounter.getGameTimeDeltaPartialTick(false)
        val lightTexture = minecraft.gameRenderer.lightTexture()
        val tesselator = Tesselator.getInstance()

        // Re-establish the world's model-view so the camera-relative quads built by render() land in
        // the right place; projection is still the world's 3D projection at this point in the frame.
        val modelViewStack = RenderSystem.getModelViewStack()
        modelViewStack.pushMatrix()
        modelViewStack.set(modelViewMatrix)

        lightTexture.turnOnLightLayer()

        // Group by the particle's own render type (ParticleMaterials.ALPHA/ADD/BLEND/OPAQUE). Calling
        // its begin() sets the IDENTICAL GL state vanilla would use, so the look matches no-shaders.
        for ((renderType, particles) in visible.groupBy { it.particleTextureSheet }) {
            val buffer = renderType.begin(tesselator, minecraft.textureManager) ?: continue
            for (particle in particles) {
                particle.render(buffer, camera, partialTicks)
            }
            val mesh = buffer.build() ?: continue
            BufferUploader.drawWithShader(mesh)
        }

        lightTexture.turnOffLightLayer()
        modelViewStack.popMatrix()

        // Restore neutral GL state so nothing downstream (hand already drawn, GUI next) is affected.
        RenderSystem.depthMask(true)
        RenderSystem.disableBlend()
        RenderSystem.defaultBlendFunc()
        RenderSystem.enableCull()
    }
}
