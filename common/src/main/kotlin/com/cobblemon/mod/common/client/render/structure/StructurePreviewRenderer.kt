/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.render.structure

import com.cobblemon.mod.common.client.render.layer.CobblemonRenderLayers
import com.mojang.blaze3d.platform.Lighting
import com.mojang.math.Axis
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.renderer.BiomeColors
import net.minecraft.client.renderer.ItemBlockRenderTypes
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.registries.Registries
import net.minecraft.util.RandomSource
import net.minecraft.world.level.BlockAndTintGetter
import net.minecraft.world.level.biome.Biomes
import net.minecraft.world.level.block.RenderShape
import net.minecraft.world.level.material.FluidState
import net.minecraft.world.level.material.Fluids
import org.joml.Matrix4f
import org.joml.Quaternionf
import org.joml.Vector3f
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

        val rootMatrix = Matrix4f(pose.last().pose())

        // Pass 1: solid/cutout block models. These write depth and are flushed before the water, so the
        // fluid drawn afterward blends over them where it's in front and is depth-rejected where behind.
        for (entry in preview.blocks) {
            val pos = entry.pos
            val state = entry.state
            // Skip blocks fully enclosed by solid neighbors: invisible, so don't process them at all.
            if (state.renderShape != RenderShape.MODEL || isFullyOccluded(fakeLevel, pos)) continue
            pose.pushPose()
            pose.translate(pos.x.toDouble(), pos.y.toDouble(), pos.z.toDouble())
            random.setSeed(state.getSeed(pos))
            val renderType = ItemBlockRenderTypes.getChunkRenderType(state)
            blockRenderer.renderBatched(state, pos, fakeLevel, pose, bufferSource.getBuffer(renderType), true, random)
            pose.popPose()
        }
        context.flush()

        // Pass 2: fluid. The camera rotation is known (yaw/pitch), so transform each face center by the
        // same rotation the pose applies and draw the translucent quads back-to-front for the CURRENT
        // view angle, re-sorted every frame. Blending therefore stays correct however the model is
        // rotated, instead of depending on the order blocks happen to appear in the NBT.
        val rotation = Axis.XP.rotationDegrees(pitch).mul(Axis.YP.rotationDegrees(yaw))
        val faces = ArrayList<FluidFace>()
        for (entry in preview.blocks) {
            val fluid = entry.state.fluidState
            if (!fluid.isEmpty) collectFluidFaces(fakeLevel, entry.pos, fluid, preview, rotation, faces)
        }
        faces.sortBy { it.viewDepth } // most negative = farthest from the camera, drawn first
        val fluidBuffer = bufferSource.getBuffer(CobblemonRenderLayers.STRUCTURE_PREVIEW_FLUID)
        for (face in faces) {
            var i = 0
            while (i < 12) {
                fluidBuffer.addVertex(rootMatrix, face.verts[i], face.verts[i + 1], face.verts[i + 2])
                    .setColor(face.r, face.g, face.b, face.a)
                i += 3
            }
        }
        context.flush()

        Lighting.setupFor3DItems()
        pose.popPose()
        context.disableScissor()
    }

    /** One translucent fluid quad: 4 corners in structure space + color + camera-space depth key. */
    private class FluidFace(
        val verts: FloatArray, // 12 floats: 4 corners × xyz
        val r: Float, val g: Float, val b: Float, val a: Float,
        val viewDepth: Float
    )

    // Unit-cube corner positions per face, wound to match the previous full-cube quads.
    private val FACE_CORNERS: Map<Direction, FloatArray> = mapOf(
        Direction.UP to floatArrayOf(0f, 1f, 0f, 0f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 0f),
        Direction.DOWN to floatArrayOf(0f, 0f, 1f, 0f, 0f, 0f, 1f, 0f, 0f, 1f, 0f, 1f),
        Direction.NORTH to floatArrayOf(1f, 0f, 0f, 1f, 1f, 0f, 0f, 1f, 0f, 0f, 0f, 0f),
        Direction.SOUTH to floatArrayOf(0f, 0f, 1f, 0f, 1f, 1f, 1f, 1f, 1f, 1f, 0f, 1f),
        Direction.WEST to floatArrayOf(0f, 0f, 0f, 0f, 1f, 0f, 0f, 1f, 1f, 0f, 0f, 1f),
        Direction.EAST to floatArrayOf(1f, 0f, 1f, 1f, 1f, 1f, 1f, 1f, 0f, 1f, 0f, 0f)
    )

    /**
     * Collects the exposed faces of one fluid block as full-cube quads (the preview has no flowing-water
     * support, so plain and waterlogged blocks are identical full cubes; faces against same-fluid or
     * solid neighbors are never created). Each face is tagged with its camera-space depth — the face
     * center run through the same yaw/pitch [rotation] the pose applies — so the caller can sort all
     * fluid quads back-to-front for the current view before drawing them.
     */
    private fun collectFluidFaces(
        level: BlockAndTintGetter,
        pos: BlockPos,
        fluid: FluidState,
        preview: StructurePreview,
        rotation: Quaternionf,
        out: MutableList<FluidFace>
    ) {
        val lava = isLava(fluid)
        val color = if (lava) 0xFF6A00 else BiomeColors.getAverageWaterColor(level, pos)
        val r = ((color shr 16) and 0xFF) / 255f
        val g = ((color shr 8) and 0xFF) / 255f
        val b = (color and 0xFF) / 255f
        val a = if (lava) 0.95f else 0.7f
        val bx = pos.x.toFloat()
        val by = pos.y.toFloat()
        val bz = pos.z.toFloat()

        for (dir in Direction.entries) {
            if (fluidFaceHidden(level, pos, dir, fluid)) continue
            val corners = FACE_CORNERS.getValue(dir)
            val verts = FloatArray(12)
            for (i in 0 until 4) {
                verts[i * 3] = bx + corners[i * 3]
                verts[i * 3 + 1] = by + corners[i * 3 + 1]
                verts[i * 3 + 2] = bz + corners[i * 3 + 2]
            }
            // Camera-space depth of the face center (+z is toward the viewer in the preview pose).
            val center = Vector3f(
                bx + 0.5f + dir.stepX * 0.5f - preview.centerX,
                by + 0.5f + dir.stepY * 0.5f - preview.centerY,
                bz + 0.5f + dir.stepZ * 0.5f - preview.centerZ
            )
            rotation.transform(center)
            out.add(FluidFace(verts, r, g, b, a, center.z))
        }
    }

    /**
     * A fluid face is hidden if the neighbor is the same fluid kind (so two water/two lava blocks never
     * draw the shared interior face between them) or a solid opaque block.
     */
    private fun fluidFaceHidden(level: BlockAndTintGetter, pos: BlockPos, dir: Direction, fluid: FluidState): Boolean {
        val neighborPos = pos.relative(dir)
        val neighborState = level.getBlockState(neighborPos)
        if (sameFluidKind(neighborState.fluidState, fluid)) return true
        return neighborState.isSolidRender(level, neighborPos)
    }

    // Compare fluid TYPES rather than FluidTags: the tag lookup can come back false in some contexts,
    // which would stop interior faces from being culled and draw every shared face.
    private fun isWater(fs: FluidState) = fs.type === Fluids.WATER || fs.type === Fluids.FLOWING_WATER
    private fun isLava(fs: FluidState) = fs.type === Fluids.LAVA || fs.type === Fluids.FLOWING_LAVA
    private fun sameFluidKind(a: FluidState, b: FluidState): Boolean {
        if (a.isEmpty || b.isEmpty) return false
        return (isWater(a) && isWater(b)) || (isLava(a) && isLava(b))
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
