/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.render.block

import com.cobblemon.mod.common.api.berry.Berries
import com.cobblemon.mod.common.block.PokeSnackBlock
import com.cobblemon.mod.common.block.PokeSnackBlock.Companion.CANDLE
import com.cobblemon.mod.common.block.PokeSnackBlock.Companion.getCandleById
import com.cobblemon.mod.common.block.entity.PokeSnackBlockEntity
import com.cobblemon.mod.common.client.render.atlas.CobblemonAtlases.BERRY_SPRITE_ATLAS
import com.cobblemon.mod.common.client.render.models.blockbench.repository.BerryModelRepository
import com.cobblemon.mod.common.client.render.models.blockbench.setPosition
import com.cobblemon.mod.common.util.math.geometry.Axis
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.math.Axis as MojangAxis
import net.minecraft.client.renderer.SubmitNodeCollector
import net.minecraft.client.renderer.block.BlockModelResolver
import net.minecraft.client.renderer.block.BlockModelRenderState
import net.minecraft.client.renderer.block.model.BlockDisplayContext
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState
import net.minecraft.client.renderer.feature.ModelFeatureRenderer
import net.minecraft.client.renderer.rendertype.RenderTypes
import net.minecraft.client.renderer.state.level.CameraRenderState
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.core.Direction
import net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.properties.BlockStateProperties.LIT
import net.minecraft.world.phys.Vec3

open class PokeSnackBlockEntityRenderer(ctx: BlockEntityRendererProvider.Context) : BlockEntityRenderer<PokeSnackBlockEntity, BlockEntityRenderState> {
    private val blockModelResolver = ctx.blockModelResolver()
    private val blockDisplayContext = BlockDisplayContext.create()

    override fun createRenderState(): BlockEntityRenderState = PokeSnackRenderState()

    override fun extractRenderState(
        blockEntity: PokeSnackBlockEntity,
        renderState: BlockEntityRenderState,
        tickDelta: Float,
        cameraPos: Vec3,
        crumblingOverlay: ModelFeatureRenderer.CrumblingOverlay?
    ) {
        BlockEntityRenderState.extractBase(blockEntity, renderState, crumblingOverlay)
        if (renderState !is PokeSnackRenderState) {
            return
        }

        renderState.berries.clear()
        renderState.candleState = null
        renderState.facing = blockEntity.blockState.getValue(FACING)

        val candleId = blockEntity.blockState.getValue(CANDLE)
        if (candleId > 0) {
            renderState.candleState = getCandleById(candleId).defaultBlockState()
                .setValue(LIT, blockEntity.blockState.getValue(LIT))
        }

        val berryIngredients = (blockEntity.ingredientComponent?.ingredientIds?.take(3) ?: emptyList())
            .mapNotNull { Berries.getByIdentifier(it) }

        berryIngredients.forEachIndexed { index, berry ->
            val placement = when (berryIngredients.size) {
                1 -> {
                    val base = berry.pokeSnackPositionings[0]
                    if (renderState.candleState != null) {
                        BerryPlacement(
                            berry.fruitModelIdentifier,
                            base.position.x.toFloat(),
                            base.position.y.toFloat(),
                            base.position.z.toFloat(),
                            Math.toRadians(base.rotation.x).toFloat(),
                            Math.toRadians(base.rotation.y).toFloat(),
                            Math.toRadians(base.rotation.z).toFloat()
                        )
                    } else {
                        BerryPlacement(
                            berry.fruitModelIdentifier,
                            8F,
                            base.position.y.toFloat(),
                            8F,
                            Math.toRadians(180.0).toFloat(),
                            0F,
                            0F
                        )
                    }
                }
                2 -> {
                    val base = berry.pokeSnackPositionings[0]
                    val isFirst = index == 0
                    BerryPlacement(
                        berry.fruitModelIdentifier,
                        base.position.x.toFloat(),
                        base.position.y.toFloat(),
                        if (isFirst) base.position.z.toFloat() else 16F - base.position.z.toFloat(),
                        Math.toRadians(if (isFirst) base.rotation.x else 180.0 + (180.0 - base.rotation.x)).toFloat(),
                        Math.toRadians(base.rotation.y).toFloat(),
                        Math.toRadians(base.rotation.z).toFloat()
                    )
                }
                else -> {
                    val base = berry.pokeSnackPositionings[index]
                    BerryPlacement(
                        berry.fruitModelIdentifier,
                        base.position.x.toFloat(),
                        base.position.y.toFloat(),
                        base.position.z.toFloat(),
                        Math.toRadians(base.rotation.x).toFloat(),
                        Math.toRadians(base.rotation.y).toFloat(),
                        Math.toRadians(base.rotation.z).toFloat()
                    )
                }
            }
            renderState.berries += placement
        }
    }

    override fun submit(
        renderState: BlockEntityRenderState,
        poseStack: PoseStack,
        submitNodeCollector: SubmitNodeCollector,
        cameraRenderState: CameraRenderState
    ) {
        if (renderState !is PokeSnackRenderState) {
            return
        }

        poseStack.pushPose()
        poseStack.translate(0.5, 0.5, 0.5)
        val rotationAngle = when (renderState.facing) {
            Direction.NORTH -> 180F
            Direction.SOUTH -> 0F
            Direction.WEST -> 90F
            Direction.EAST -> -90F
            else -> 0F
        }
        poseStack.mulPose(MojangAxis.YP.rotationDegrees(rotationAngle))
        poseStack.translate(-0.5, -0.5, -0.5)

        renderState.candleState?.let { candleState ->
            poseStack.pushPose()
            poseStack.translate(0.0, PokeSnackBlock.CAKE_HEIGHT, 0.0)
            val candleModelState = BlockModelRenderState()
            blockModelResolver.update(candleModelState, candleState, blockDisplayContext)
            candleModelState.submit(poseStack, submitNodeCollector, renderState.lightCoords, OverlayTexture.NO_OVERLAY, 0)
            poseStack.popPose()
        }

        val berryAtlas = BERRY_SPRITE_ATLAS.textureAtlas?.location()
        if (renderState.berries.isNotEmpty() && berryAtlas != null) {
            submitNodeCollector.submitCustomGeometry(
                poseStack,
                RenderTypes.entityCutout(berryAtlas)
            ) { pose, consumer ->
                val renderStack = PoseStack()
                renderStack.last().set(pose)
                renderState.berries.forEach { berry ->
                    val model = BerryModelRepository.modelOf(berry.modelId) ?: return@forEach
                    model.setPosition(Axis.X_AXIS.ordinal, berry.x)
                    model.setPosition(Axis.Y_AXIS.ordinal, berry.y)
                    model.setPosition(Axis.Z_AXIS.ordinal, berry.z)
                    model.setRotation(berry.rotX, berry.rotY, berry.rotZ)
                    model.render(renderStack, consumer, renderState.lightCoords, OverlayTexture.NO_OVERLAY)
                }
            }
        }

        poseStack.popPose()
    }
}

private class PokeSnackRenderState : BlockEntityRenderState() {
    var facing: Direction = Direction.NORTH
    var candleState: BlockState? = null
    val berries = mutableListOf<BerryPlacement>()
}

private data class BerryPlacement(
    val modelId: net.minecraft.resources.Identifier,
    val x: Float,
    val y: Float,
    val z: Float,
    val rotX: Float,
    val rotY: Float,
    val rotZ: Float
)
