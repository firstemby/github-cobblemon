/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.gui.pokedex.page

import com.cobblemon.mod.common.client.render.models.blockbench.PosableState
import com.cobblemon.mod.common.client.render.models.blockbench.repository.RenderContext
import com.cobblemon.mod.common.client.render.models.blockbench.repository.VaryingModelRepository
import com.cobblemon.mod.common.entity.PoseType
import com.mojang.math.Axis
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.model.geom.ModelPart
import net.minecraft.client.renderer.LevelRenderer
import net.minecraft.client.renderer.LightTexture
import net.minecraft.client.renderer.RenderType
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.level.block.Blocks

/**
 * Renders a substitute doll as a real 3D posable model inside an arbitrary scene pose (for the MoveDex
 * isometric battle viewport). Mirrors the portrait render's model/context/animation setup but with a
 * uniform scale (the portrait flattens Z into a billboard, which is why it looked 2D) and a caller-
 * supplied facing, so two dolls can stand at opposite ends of a rotated 3D scene like a real battle.
 */
object MoveDexSceneRenderer {

    /** Scene-space vertical rise (up = +) of the most recently rendered model's animated root. Read by the
     *  follow-camera right after each renderModel call to keep big-motion moves (e.g. Seismic Toss) in frame. */
    var lastRootRiseY = 0f

    /**
     * Renders the model [identifier] (a species resource id, or the substitute fallback) at the current
     * pose origin, rotated to [facingYaw] degrees and scaled by [modelScale]. The caller is expected to
     * have already applied the scene transform (iso rotation, viewport placement) and translated to this
     * doll's position. Returns true if it rendered, so the caller can fall back to the substitute.
     */
    /**
     * Draws the stadium ground plane: a horizontal textured quad at the dolls' feet (pose y = 0), spanning
     * [halfX] left/right and [halfZ] forward/back (blocks, before the scene's zoom). The texture's two stand
     * pads are authored at V=0.75 (attacker A, +Z) and V=0.25 (target B, -Z); with halfZ = 2*separation the
     * pads land exactly under each doll. Gives the scene a visible floor and a reference for where the move's
     * ground-colliding particles (Bulldoze etc.) come to rest. Caller must have applied the scene transform.
     */
    fun renderFloor(context: GuiGraphics, texture: ResourceLocation, halfX: Float, halfZ: Float) {
        val pose = context.pose()
        val entry = pose.last()
        val matrix = entry.pose()
        val renderType = RenderType.entityCutoutNoCull(texture)
        val immediate = Minecraft.getInstance().renderBuffers().bufferSource()
        val buffer = immediate.getBuffer(renderType)
        val light = LightTexture.pack(15, 15)
        // Pose +Y points down in this scene (particles negate Y to rise, dolls flip to stand upright), so the
        // floor's up-normal is -Y — matching the dolls so it shades the same under the inventory lighting.
        fun vert(x: Float, z: Float, u: Float, v: Float) {
            buffer.addVertex(matrix, x, 0f, z)
                .setColor(255, 255, 255, 255)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(entry, 0f, -1f, 0f)
        }
        vert(-halfX, -halfZ, 0f, 0f)
        vert(-halfX, halfZ, 0f, 1f)
        vert(halfX, halfZ, 1f, 1f)
        vert(halfX, -halfZ, 1f, 0f)
        immediate.endBatch(renderType)
    }

    /**
     * Renders a vanilla target block as the move's target dummy, [blockSize] blocks per edge, sitting on the
     * floor (base at pose y=0) and centred on X/Z at the current pose origin. The negative Y scale both stands
     * the block upright in this Y-down scene and keeps its winding correct for back-face culling (one net flip,
     * same trick the structure preview uses). The caller has already translated to the target's spot.
     */
    fun renderTargetBlock(
        context: GuiGraphics,
        blockSize: Float,
        offsetX: Float = 0f, offsetY: Float = 0f, offsetZ: Float = 0f,
        scaleX: Float = 1f, scaleY: Float = 1f, scaleZ: Float = 1f
    ) {
        val pose = context.pose()
        pose.pushPose()
        // Hit-reaction transform: the offset translates the block (knockback / lift) and the non-uniform scale
        // squashes/stretches it. Y scales about the base (y=0 stays on the floor); X/Z scale about the centre.
        pose.translate(offsetX.toDouble(), offsetY.toDouble(), offsetZ.toDouble())
        pose.scale(blockSize * scaleX, -blockSize * scaleY, blockSize * scaleZ)
        pose.translate(-0.5, 0.0, -0.5) // centre the unit cube on X/Z; base stays at y=0 (the floor)
        val immediate = Minecraft.getInstance().renderBuffers().bufferSource()
        Minecraft.getInstance().blockRenderer.renderSingleBlock(
            Blocks.TARGET.defaultBlockState(),
            pose,
            immediate,
            LightTexture.pack(15, 15),
            OverlayTexture.NO_OVERLAY
        )
        immediate.endBatch()
        pose.popPose()
    }

    /** Draws a small wireframe box [half] blocks around (x,y,z) in the current pose — a debug locator marker. */
    fun renderDebugMarker(context: GuiGraphics, x: Float, y: Float, z: Float, half: Float, r: Float, g: Float, b: Float) {
        val immediate = Minecraft.getInstance().renderBuffers().bufferSource()
        val buffer = immediate.getBuffer(RenderType.lines())
        LevelRenderer.renderLineBox(
            context.pose(), buffer,
            (x - half).toDouble(), (y - half).toDouble(), (z - half).toDouble(),
            (x + half).toDouble(), (y + half).toDouble(), (z + half).toDouble(),
            r, g, b, 1f
        )
        immediate.endBatch(RenderType.lines())
    }

    fun renderModel(context: GuiGraphics, identifier: ResourceLocation, state: PosableState, worldScale: Float, facingYaw: Float, partialTicks: Float): Boolean {
        return runCatching {
            val model = VaryingModelRepository.getPoser(identifier, state)
            state.currentModel = model
            val texture = VaryingModelRepository.getTexture(identifier, state)

            val ctx = RenderContext()
            model.context = ctx
            VaryingModelRepository.getTextureNoSubstitute(identifier, state).let { ctx.put(RenderContext.TEXTURE, it) }
            ctx.put(RenderContext.SCALE, 1f)
            ctx.put(RenderContext.SPECIES, identifier)
            ctx.put(RenderContext.ASPECTS, state.currentAspects)
            ctx.put(RenderContext.POSABLE_STATE, state)
            ctx.put(RenderContext.DO_QUIRKS, true)

            // No-cull cutout: the GUI projection flips the winding order, so plain entityCutout would cull the
            // FRONT faces and leave the back faces showing through. Rendering both faces (depth test keeps the
            // nearer one) avoids that without disturbing the model's orientation.
            val renderType = RenderType.entityCutoutNoCull(texture)
            state.setPoseToFirstSuitable(PoseType.PROFILE)
            state.updatePartialTicks(partialTicks)
            model.applyAnimations(null, state, 0f, 0f, 0f, 0f, 0f)
            // Capture how far the move animation has translated the model's ROOT vertically (Seismic Toss throws
            // it way up). rootPart.y is raw bedrock units (÷16 -> blocks) and worldScale is the doll scale, so this
            // is the scene-space rise the follow-camera uses to keep the action framed. Positive = up.
            lastRootRiseY = ((model.rootPart as? ModelPart)?.y ?: 0f) / 16f * worldScale
            // Populate the model's real geo-defined locators (target/special/mouth/etc.) into state.locatorStates
            // so the move can emit from the exact spots battle uses. (Entity-null: geo locators still populate.)
            runCatching { model.updateLocators(null, state) }

            val pose = context.pose()
            pose.pushPose()
            pose.mulPose(Axis.YP.rotationDegrees(facingYaw))
            // Render at the model's TRUE world scale (baseScale, in blocks) — the same as battle — so the model
            // and the move's particles share one world scale. The scene's auto-fit handles fitting the window;
            // we must NOT use profileScale here (it normalises model size and would decouple it from particles).
            pose.scale(worldScale, worldScale, worldScale)

            val immediate = Minecraft.getInstance().renderBuffers().bufferSource()
            val buffer = immediate.getBuffer(renderType)
            model.withLayerContext(immediate, state, VaryingModelRepository.getLayers(identifier, state)) {
                model.render(ctx, pose, buffer, LightTexture.pack(15, 15), OverlayTexture.NO_OVERLAY, -1)
                immediate.endBatch()
            }
            model.setDefault()
            pose.popPose()
            true
        }.getOrDefault(false)
    }
}
