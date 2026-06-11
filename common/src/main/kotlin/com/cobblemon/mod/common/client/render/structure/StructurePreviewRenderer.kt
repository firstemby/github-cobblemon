/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.render.structure

import com.mojang.blaze3d.platform.Lighting
import com.mojang.math.Axis
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.renderer.BiomeColors
import net.minecraft.client.renderer.ItemBlockRenderTypes
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.RenderType
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.registries.Registries
import net.minecraft.tags.FluidTags
import net.minecraft.util.RandomSource
import net.minecraft.world.level.BlockAndTintGetter
import net.minecraft.world.level.biome.Biomes
import net.minecraft.world.level.block.RenderShape
import net.minecraft.world.level.material.FluidState
import org.joml.Matrix4f
import kotlin.math.max

/**
 * Renders a [StructurePreview] as an interactive 3D model in a GUI pane, using the game's own
 * chunk-block renderer against an in-memory [StructurePreviewLevel] so neighbor face-culling, biome
 * tints, and brightness behave like in-world. Fluids (water/lava) are drawn as tinted translucent
 * volumes since the chunk fluid renderer can't be driven cleanly in GUI space.
 */
object StructurePreviewRenderer {

    fun render(
        context: GuiGraphics,
        preview: StructurePreview,
        paneX: Int,
        paneY: Int,
        paneWidth: Int,
        paneHeight: Int,
        yaw: Float,
        pitch: Float,
        zoom: Float
    ) {
        if (preview.blocks.isEmpty()) return

        val fitScale = (minOf(paneWidth, paneHeight) * 0.6f / max(1, preview.fitExtent)) * zoom

        context.enableScissor(paneX, paneY, paneX + paneWidth, paneY + paneHeight)
        val pose = context.pose()
        pose.pushPose()
        pose.translate(paneX + paneWidth / 2.0, paneY + paneHeight / 2.0, 100.0)
        // Single axis flip (Y) so world-up is screen-up while keeping triangle winding correct.
        pose.scale(fitScale, -fitScale, fitScale)
        pose.mulPose(Axis.XP.rotationDegrees(pitch))
        pose.mulPose(Axis.YP.rotationDegrees(yaw))
        // Pivot on the actual block centroid so rotation stays centered on the visible model.
        pose.translate(-preview.centerX.toDouble(), -preview.centerY.toDouble(), -preview.centerZ.toDouble())

        Lighting.setupFor3DItems()

        val mc = Minecraft.getInstance()
        val blockRenderer = mc.blockRenderer
        val biome = mc.level?.registryAccess()?.registryOrThrow(Registries.BIOME)?.get(Biomes.PLAINS)
        val fakeLevel = StructurePreviewLevel(preview.stateMap, biome)
        val bufferSource = context.bufferSource()
        val random = RandomSource.create()

        for (entry in preview.blocks) {
            val pos = entry.pos
            val state = entry.state
            pose.pushPose()
            pose.translate(pos.x.toDouble(), pos.y.toDouble(), pos.z.toDouble())

            // Skip blocks fully enclosed by solid neighbors: invisible, so don't process them at all.
            if (state.renderShape == RenderShape.MODEL && !isFullyOccluded(fakeLevel, pos)) {
                // renderBatched applies the block's getOffset() to the pose and does NOT restore it, so
                // isolate it in its own push/pop — otherwise a plant's random XZ offset leaks into the
                // fluid matrix below and shifts the water into the neighboring block.
                pose.pushPose()
                random.setSeed(state.getSeed(pos))
                val renderType = ItemBlockRenderTypes.getChunkRenderType(state)
                blockRenderer.renderBatched(state, pos, fakeLevel, pose, bufferSource.getBuffer(renderType), true, random)
                pose.popPose()
            }

            val fluid = state.fluidState
            if (!fluid.isEmpty) {
                renderFluid(pose.last().pose(), bufferSource, fakeLevel, pos, fluid)
            }

            pose.popPose()
        }

        context.flush()
        Lighting.setupFor3DItems()
        pose.popPose()
        context.disableScissor()
    }

    /**
     * Tinted translucent fluid volume. The preview has no flowing-water support, so every water spot —
     * plain water OR a waterlogged block — is drawn as a consistent full cube; this keeps all water at
     * the same height so nothing misaligns. Only faces exposed to air or a different fluid are emitted;
     * faces shared with a same-fluid or solid neighbor are culled so the body reads as one connected volume.
     */
    private fun renderFluid(matrix: Matrix4f, bufferSource: MultiBufferSource, level: BlockAndTintGetter, pos: BlockPos, fluid: FluidState) {
        val lava = fluid.`is`(FluidTags.LAVA)
        val color = if (lava) 0xFF6A00 else BiomeColors.getAverageWaterColor(level, pos)
        val r = ((color shr 16) and 0xFF) / 255f
        val g = ((color shr 8) and 0xFF) / 255f
        val b = (color and 0xFF) / 255f
        val a = if (lava) 0.95f else 0.7f

        val buffer = bufferSource.getBuffer(RenderType.debugQuads())
        fun v(x: Float, y: Float, z: Float) { buffer.addVertex(matrix, x, y, z).setColor(r, g, b, a) }

        if (!fluidFaceHidden(level, pos, Direction.UP, fluid))    { v(0f, 1f, 0f); v(0f, 1f, 1f); v(1f, 1f, 1f); v(1f, 1f, 0f) }
        if (!fluidFaceHidden(level, pos, Direction.DOWN, fluid))  { v(0f, 0f, 1f); v(0f, 0f, 0f); v(1f, 0f, 0f); v(1f, 0f, 1f) }
        if (!fluidFaceHidden(level, pos, Direction.NORTH, fluid)) { v(1f, 0f, 0f); v(1f, 1f, 0f); v(0f, 1f, 0f); v(0f, 0f, 0f) }
        if (!fluidFaceHidden(level, pos, Direction.SOUTH, fluid)) { v(0f, 0f, 1f); v(0f, 1f, 1f); v(1f, 1f, 1f); v(1f, 0f, 1f) }
        if (!fluidFaceHidden(level, pos, Direction.WEST, fluid))  { v(0f, 0f, 0f); v(0f, 1f, 0f); v(0f, 1f, 1f); v(0f, 0f, 1f) }
        if (!fluidFaceHidden(level, pos, Direction.EAST, fluid))  { v(1f, 0f, 1f); v(1f, 1f, 1f); v(1f, 1f, 0f); v(1f, 0f, 0f) }
    }

    /** A fluid face is hidden if the neighbor is the same fluid kind or a solid opaque block. */
    private fun fluidFaceHidden(level: BlockAndTintGetter, pos: BlockPos, dir: Direction, fluid: FluidState): Boolean {
        val neighborPos = pos.relative(dir)
        val neighborState = level.getBlockState(neighborPos)
        if (sameFluidKind(neighborState.fluidState, fluid)) return true
        return neighborState.isSolidRender(level, neighborPos)
    }

    private fun sameFluidKind(a: FluidState, b: FluidState): Boolean {
        if (a.isEmpty) return false
        return (a.`is`(FluidTags.WATER) && b.`is`(FluidTags.WATER)) || (a.`is`(FluidTags.LAVA) && b.`is`(FluidTags.LAVA))
    }

    /** True when every neighbor is a solid opaque cube, so this block cannot be seen and can be skipped. */
    private fun isFullyOccluded(level: BlockAndTintGetter, pos: BlockPos): Boolean {
        for (dir in Direction.values()) {
            val neighborPos = pos.relative(dir)
            if (!level.getBlockState(neighborPos).isSolidRender(level, neighborPos)) return false
        }
        return true
    }
}
