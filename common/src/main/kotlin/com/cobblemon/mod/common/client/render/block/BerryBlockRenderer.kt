/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.render.block

import com.cobblemon.mod.common.api.mulch.MulchVariant
import com.cobblemon.mod.common.block.BerryBlock
import com.cobblemon.mod.common.block.entity.BerryBlockEntity
import com.cobblemon.mod.common.client.CobblemonBakingOverrides
import com.cobblemon.mod.common.client.render.atlas.CobblemonAtlases.BERRY_SPRITE_ATLAS
import com.cobblemon.mod.common.client.render.models.blockbench.repository.BerryModelRepository
import com.cobblemon.mod.common.client.render.models.blockbench.setPosition
import com.cobblemon.mod.common.util.math.geometry.Axis
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.QuadInstance
import net.minecraft.client.renderer.Sheets
import net.minecraft.client.renderer.SubmitNodeCollector
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState
import net.minecraft.client.renderer.feature.ModelFeatureRenderer
import net.minecraft.client.renderer.rendertype.RenderTypes
import net.minecraft.client.renderer.state.level.CameraRenderState
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.client.resources.model.geometry.QuadCollection
import net.minecraft.resources.Identifier
import net.minecraft.world.phys.Vec3

class BerryBlockRenderer(private val context: BlockEntityRendererProvider.Context) : BlockEntityRenderer<BerryBlockEntity, BlockEntityRenderState> {
    private val mulchModels = mutableMapOf(
        MulchVariant.COARSE to CobblemonBakingOverrides.COARSE_MULCH,
        MulchVariant.GROWTH to CobblemonBakingOverrides.GROWTH_MULCH,
        MulchVariant.HUMID to CobblemonBakingOverrides.HUMID_MULCH,
        MulchVariant.LOAMY to CobblemonBakingOverrides.LOAMY_MULCH,
        MulchVariant.PEAT to CobblemonBakingOverrides.PEAT_MULCH,
        MulchVariant.RICH to CobblemonBakingOverrides.RICH_MULCH,
        MulchVariant.SANDY to CobblemonBakingOverrides.SANDY_MULCH,
        MulchVariant.SURPRISE to CobblemonBakingOverrides.SURPRISE_MULCH,
        MulchVariant.NONE to null
    )

    override fun createRenderState(): BlockEntityRenderState = BerryRenderState()

    override fun extractRenderState(
        blockEntity: BerryBlockEntity,
        renderState: BlockEntityRenderState,
        tickDelta: Float,
        cameraPos: Vec3,
        crumblingOverlay: ModelFeatureRenderer.CrumblingOverlay?
    ) {
        BlockEntityRenderState.extractBase(blockEntity, renderState, crumblingOverlay)
        if (renderState !is BerryRenderState) {
            return
        }

        renderState.berryPlacements.clear()
        renderState.mulchModel = mulchModels[blockEntity.mulchVariant]?.getModel()

        when (val age = blockEntity.blockState.getValue(BerryBlock.AGE)) {
            0 -> {
                val berry = blockEntity.berry() ?: return
                val pos = berry.stageOnePositioning.position
                val rot = berry.stageOnePositioning.rotation
                renderState.berryPlacements += BerryRenderPlacement(
                    berry.fruitModelIdentifier,
                    pos.x.toFloat(),
                    pos.y.toFloat(),
                    pos.z.toFloat(),
                    Math.toRadians(180.0 - rot.x).toFloat(),
                    Math.toRadians(180.0 + rot.y).toFloat(),
                    Math.toRadians(rot.z).toFloat()
                )
            }

            in (BerryBlock.MATURE_AGE + 1)..BerryBlock.FRUIT_AGE -> {
                val isFlower = age == BerryBlock.FLOWER_AGE
                for ((berry, growthPoint) in blockEntity.berryAndGrowthPoint()) {
                    renderState.berryPlacements += BerryRenderPlacement(
                        if (isFlower) berry.flowerModelIdentifier else berry.fruitModelIdentifier,
                        growthPoint.position.x.toFloat(),
                        growthPoint.position.y.toFloat(),
                        growthPoint.position.z.toFloat(),
                        Math.toRadians(180.0 - growthPoint.rotation.x).toFloat(),
                        Math.toRadians(180.0 + growthPoint.rotation.y).toFloat(),
                        Math.toRadians(growthPoint.rotation.z).toFloat()
                    )
                }
            }
        }
    }

    override fun submit(
        renderState: BlockEntityRenderState,
        poseStack: PoseStack,
        submitNodeCollector: SubmitNodeCollector,
        cameraRenderState: CameraRenderState
    ) {
        if (renderState !is BerryRenderState) {
            return
        }

        val mulchModel = renderState.mulchModel
        if (mulchModel != null && mulchModel != QuadCollection.EMPTY) {
            submitNodeCollector.submitCustomGeometry(poseStack, Sheets.cutoutBlockSheet()) { pose, consumer ->
                val quadInstance = QuadInstance().apply {
                    setColor(-1)
                    setLightCoords(renderState.lightCoords)
                    setOverlayCoords(OverlayTexture.NO_OVERLAY)
                }
                mulchModel.getAll().forEach { quad ->
                    consumer.putBakedQuad(pose, quad, quadInstance)
                }
            }
        }

        val berryAtlas = BERRY_SPRITE_ATLAS.textureAtlas?.location()
        if (renderState.berryPlacements.isNotEmpty() && berryAtlas != null) {
            submitNodeCollector.submitCustomGeometry(poseStack, RenderTypes.entityCutout(berryAtlas)) { pose, consumer ->
                val renderStack = PoseStack()
                renderStack.last().set(pose)
                renderState.berryPlacements.forEach { berry ->
                    val model = BerryModelRepository.modelOf(berry.modelId) ?: return@forEach
                    model.setPosition(Axis.X_AXIS.ordinal, berry.x)
                    model.setPosition(Axis.Y_AXIS.ordinal, berry.y)
                    model.setPosition(Axis.Z_AXIS.ordinal, berry.z)
                    model.setRotation(berry.rotX, berry.rotY, berry.rotZ)
                    model.render(renderStack, consumer, renderState.lightCoords, OverlayTexture.NO_OVERLAY)
                }
            }
        }
    }
}

private class BerryRenderState : BlockEntityRenderState() {
    var mulchModel: QuadCollection? = null
    val berryPlacements = mutableListOf<BerryRenderPlacement>()
}

private data class BerryRenderPlacement(
    val modelId: Identifier,
    val x: Float,
    val y: Float,
    val z: Float,
    val rotX: Float,
    val rotY: Float,
    val rotZ: Float
)
