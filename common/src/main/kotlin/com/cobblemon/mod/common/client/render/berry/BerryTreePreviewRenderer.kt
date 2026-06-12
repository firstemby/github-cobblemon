/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.render.berry

import com.cobblemon.mod.common.api.berry.Berry
import com.cobblemon.mod.common.api.berry.GrowthPoint
import com.cobblemon.mod.common.block.BerryBlock
import com.cobblemon.mod.common.client.render.atlas.CobblemonAtlases.BERRY_SPRITE_ATLAS
import com.cobblemon.mod.common.client.render.models.blockbench.repository.BerryModelRepository
import com.cobblemon.mod.common.client.render.models.blockbench.setPosition
import com.cobblemon.mod.common.client.render.structure.StructurePreviewLevel
import com.cobblemon.mod.common.util.math.geometry.Axis
import com.mojang.blaze3d.platform.Lighting
import com.mojang.blaze3d.vertex.VertexConsumer
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.renderer.ItemBlockRenderTypes
import net.minecraft.client.renderer.LightTexture
import net.minecraft.client.renderer.RenderType
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceLocation
import net.minecraft.util.RandomSource
import net.minecraft.world.level.biome.Biomes
import net.minecraft.world.level.block.RenderShape
import net.minecraft.world.phys.AABB

/**
 * Renders a single berry tree (the [BerryBlock] state at a chosen age, plus its fruit/flower/sprout
 * blockbench models at the berry's growth points) as an interactive 3D model in a GUI pane for the
 * BerryDex. Mirrors what BerryBlockRenderer does in-world, but drives the models straight from the
 * [Berry] definition so no block entity or level is needed.
 */
object BerryTreePreviewRenderer {

    private val FULL_BRIGHT = LightTexture.pack(15, 15)

    fun render(
        context: GuiGraphics,
        berry: Berry,
        age: Int,
        paneX: Int,
        paneY: Int,
        paneWidth: Int,
        paneHeight: Int,
        yaw: Float,
        pitch: Float,
        zoom: Float
    ) {
        val block = berry.block() ?: return
        val state = block.defaultBlockState().setValue(BerryBlock.AGE, age.coerceIn(0, BerryBlock.FRUIT_AGE))

        val mc = Minecraft.getInstance()
        val biome = mc.level?.registryAccess()?.registryOrThrow(Registries.BIOME)?.get(Biomes.PLAINS)
        // Slightly boosted face shading so the preview reads brighter than default world lighting.
        val fakeLevel = StructurePreviewLevel(mapOf(BlockPos.ZERO to state), biome, shadeBoost = 1.25f)

        // Center and fit on this age's actual model bounds (the block state's outline shape), so every
        // growth stage pivots on its own visual centroid instead of a fixed block center.
        val bounds = runCatching { state.getShape(fakeLevel, BlockPos.ZERO) }.getOrNull()
            ?.takeIf { !it.isEmpty }?.bounds()
            ?: AABB(0.0, 0.0, 0.0, 1.0, 1.0, 1.0)
        val centerX = (bounds.minX + bounds.maxX) / 2.0
        val centerY = (bounds.minY + bounds.maxY) / 2.0
        val centerZ = (bounds.minZ + bounds.maxZ) / 2.0
        val extent = maxOf(bounds.xsize, bounds.ysize, bounds.zsize, 0.4).toFloat()

        context.enableScissor(paneX, paneY, paneX + paneWidth, paneY + paneHeight)
        val pose = context.pose()
        pose.pushPose()
        pose.translate(paneX + paneWidth / 2.0, paneY + paneHeight / 2.0, 100.0)
        // Single axis flip (Y) so world-up is screen-up while keeping triangle winding correct.
        val fitScale = (minOf(paneWidth, paneHeight) * 0.62f / extent) * zoom
        pose.scale(fitScale, -fitScale, fitScale)
        pose.mulPose(com.mojang.math.Axis.XP.rotationDegrees(pitch))
        pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(yaw))
        pose.translate(-centerX, -centerY, -centerZ)

        Lighting.setupFor3DItems()

        val bufferSource = context.bufferSource()

        // The tree itself is the block state's regular model.
        if (state.renderShape == RenderShape.MODEL) {
            val random = RandomSource.create()
            random.setSeed(state.getSeed(BlockPos.ZERO))
            mc.blockRenderer.renderBatched(
                state, BlockPos.ZERO, fakeLevel, pose,
                bufferSource.getBuffer(ItemBlockRenderTypes.getChunkRenderType(state)), true, random
            )
            context.flush()
        }

        // Fruit / flowers / sprout, exactly as BerryBlockRenderer stages them by age. Rendered with flat
        // item lighting (no directional diffuse) so they read bright from every rotation.
        Lighting.setupForFlatItems()
        val buffer = bufferSource.getBuffer(RenderType.entityCutoutNoCull(BERRY_SPRITE_ATLAS.textureAtlas.location()))
        when {
            age == 0 -> renderGrowthModel(berry.fruitModelIdentifier, berry.stageOnePositioning, context, buffer)
            age > BerryBlock.MATURE_AGE -> {
                val modelId = if (age == BerryBlock.FLOWER_AGE) berry.flowerModelIdentifier else berry.fruitModelIdentifier
                for (point in berry.growthPoints) {
                    renderGrowthModel(modelId, point, context, buffer)
                }
            }
        }
        context.flush()

        Lighting.setupFor3DItems()
        pose.popPose()
        context.disableScissor()
    }

    private fun renderGrowthModel(modelId: ResourceLocation, point: GrowthPoint, context: GuiGraphics, buffer: VertexConsumer) {
        val model = BerryModelRepository.modelOf(modelId) ?: return
        model.setRotation(
            Math.toRadians(180.0 - point.rotation.x).toFloat(),
            Math.toRadians(180.0 + point.rotation.y).toFloat(),
            Math.toRadians(point.rotation.z).toFloat()
        )
        model.setPosition(Axis.X_AXIS.ordinal, point.position.x.toFloat())
        model.setPosition(Axis.Y_AXIS.ordinal, point.position.y.toFloat())
        model.setPosition(Axis.Z_AXIS.ordinal, point.position.z.toFloat())
        model.render(context.pose(), buffer, FULL_BRIGHT, OverlayTexture.NO_OVERLAY)
    }
}
