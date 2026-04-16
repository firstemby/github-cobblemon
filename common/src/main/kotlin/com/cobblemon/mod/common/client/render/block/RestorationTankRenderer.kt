/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.render.block

import com.cobblemon.mod.common.CobblemonBlocks
import com.cobblemon.mod.common.api.fossil.Fossil
import com.cobblemon.mod.common.block.entity.RestorationTankBlockEntity
import com.cobblemon.mod.common.block.multiblock.FossilMultiblockStructure
import com.cobblemon.mod.common.client.CobblemonBakingOverrides
import com.cobblemon.mod.common.client.render.models.blockbench.fossil.FossilModel
import com.cobblemon.mod.common.client.render.models.blockbench.fossil.FossilState
import com.cobblemon.mod.common.client.render.models.blockbench.repository.RenderContext
import com.cobblemon.mod.common.client.render.models.blockbench.repository.VaryingModelRepository
import com.cobblemon.mod.common.client.render.models.blockbench.wavefunction.WaveFunction
import com.cobblemon.mod.common.client.render.models.blockbench.wavefunction.parabolaFunction
import com.cobblemon.mod.common.client.render.models.blockbench.wavefunction.rerange
import com.cobblemon.mod.common.client.render.models.blockbench.wavefunction.timeDilate
import com.cobblemon.mod.common.util.cobblemonResource
import com.mojang.blaze3d.vertex.QuadInstance
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.math.Axis
import kotlin.math.pow
import net.minecraft.client.Minecraft
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
import net.minecraft.core.Direction
import net.minecraft.resources.Identifier
import net.minecraft.world.level.block.HorizontalDirectionalBlock
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.Vec3
import net.minecraft.util.ARGB

class RestorationTankRenderer(ctx: BlockEntityRendererProvider.Context) : BlockEntityRenderer<RestorationTankBlockEntity, BlockEntityRenderState> {
    private val renderContext = RenderContext().also {
        it.put(RenderContext.DO_QUIRKS, true)
        it.put(RenderContext.RENDER_STATE, RenderContext.RenderState.RESURRECTION_MACHINE)
    }

    override fun createRenderState(): BlockEntityRenderState = RestorationTankRenderState()

    override fun extractRenderState(
        blockEntity: RestorationTankBlockEntity,
        renderState: BlockEntityRenderState,
        tickDelta: Float,
        cameraPos: Vec3,
        crumblingOverlay: ModelFeatureRenderer.CrumblingOverlay?
    ) {
        BlockEntityRenderState.extractBase(blockEntity, renderState, crumblingOverlay)
        if (renderState !is RestorationTankRenderState) {
            return
        }

        renderState.connectionDir = null
        renderState.fillLevel = 0
        renderState.isRunning = false
        renderState.hasCreatedPokemon = false
        renderState.timeRemaining = -1
        renderState.resultingFossil = null
        renderState.fossilState = null
        renderState.partialTicks = tickDelta

        val struct = blockEntity.multiblockStructure as? FossilMultiblockStructure ?: return
        renderState.connectionDir = struct.tankConnectorDirection
        renderState.fillLevel = struct.fillLevel
        renderState.isRunning = struct.isRunning()
        renderState.hasCreatedPokemon = struct.hasCreatedPokemon
        renderState.timeRemaining = struct.timeRemaining
        renderState.resultingFossil = struct.resultingFossil
        renderState.fossilState = struct.fossilState
        renderState.blockState = blockEntity.blockState
    }

    override fun submit(
        renderState: BlockEntityRenderState,
        poseStack: PoseStack,
        submitNodeCollector: SubmitNodeCollector,
        cameraRenderState: CameraRenderState
    ) {
        if (renderState !is RestorationTankRenderState) {
            return
        }

        val connectionDir = renderState.connectionDir
        if (connectionDir == null && renderState.fillLevel == 0 && !renderState.hasCreatedPokemon) {
            return
        }

        poseStack.pushPose()
        when (connectionDir) {
            Direction.NORTH -> poseStack.rotateAround(Axis.YP.rotationDegrees(0F), 0.5F, 0F, 0.5F)
            Direction.EAST -> poseStack.rotateAround(Axis.YP.rotationDegrees(270F), 0.5F, 0F, 0.5F)
            Direction.SOUTH -> poseStack.rotateAround(Axis.YP.rotationDegrees(180F), 0.5F, 0F, 0.5F)
            Direction.WEST -> poseStack.rotateAround(Axis.YP.rotationDegrees(90F), 0.5F, 0F, 0.5F)
            null -> {}
            else -> {}
        }

        val connectorModel = currentConnectorModel()
        if (connectionDir != null && connectorModel != QuadCollection.EMPTY) {
            submitNodeCollector.submitCustomGeometry(poseStack, Sheets.cutoutBlockSheet()) { pose, consumer ->
                val quadInstance = QuadInstance().apply {
                    setColor(ARGB.colorFromFloat(1F, 0.75F, 0.75F, 0.75F))
                    setLightCoords(renderState.lightCoords)
                    setOverlayCoords(OverlayTexture.NO_OVERLAY)
                }
                connectorModel.getAll().forEach { quad ->
                    consumer.putBakedQuad(pose, quad, quadInstance)
                }
            }
        }

        if (renderState.fillLevel == 0 && !renderState.hasCreatedPokemon) {
            poseStack.popPose()
            return
        }

        if (renderState.isRunning || renderState.hasCreatedPokemon) {
            renderFetus(renderState, poseStack)
        }

        val fluidModel = when {
            renderState.isRunning -> currentFluidModel(9)
            renderState.hasCreatedPokemon -> currentFluidModel(8)
            else -> currentFluidModel(renderState.fillLevel)
        }

        if (fluidModel != QuadCollection.EMPTY) {
            submitNodeCollector.submitCustomGeometry(poseStack, RenderTypes.translucentMovingBlock()) { pose, consumer ->
                val quadInstance = QuadInstance().apply {
                    setColor(ARGB.colorFromFloat(1F, 0.75F, 0.75F, 0.75F))
                    setLightCoords(renderState.lightCoords)
                    setOverlayCoords(OverlayTexture.NO_OVERLAY)
                }
                fluidModel.getAll().forEach { quad ->
                    consumer.putBakedQuad(pose, quad, quadInstance)
                }
            }
        }

        poseStack.popPose()
    }

    private fun renderFetus(renderState: RestorationTankRenderState, poseStack: PoseStack) {
        val fossil = renderState.resultingFossil ?: return
        val fossilState = renderState.fossilState ?: return
        val timeRemaining = renderState.timeRemaining
        val blockState = renderState.blockState ?: return
        if (blockState.block != CobblemonBlocks.RESTORATION_TANK) {
            return
        }

        val tankDirection = blockState.getValue(HorizontalDirectionalBlock.FACING)
        val connectionDir = renderState.connectionDir
        val aspects = fossil.result.aspects
        fossilState.updatePartialTicks(renderState.partialTicks)
        fossilState.currentAspects = aspects

        var fossilPoserId = fossil.identifier.withPath("${fossil.identifier.path}_fetus")
        val fossilFetusModel = VaryingModelRepository.getPoser(fossilPoserId, fossilState) as? FossilModel ?: let {
            fossilPoserId = cobblemonResource("substitute_fetus")
            VaryingModelRepository.getPoser(fossilPoserId, fossilState) as? FossilModel
        } ?: return

        val completionPercentage = (1 - timeRemaining / FossilMultiblockStructure.TIME_TO_TAKE.toFloat()).coerceIn(0F, 1F)
        val embryo1Scale = EMBRYO_CURVE_1(completionPercentage)
        val embryo2Scale = EMBRYO_CURVE_2(completionPercentage)
        val embryo3Scale = EMBRYO_CURVE_3(completionPercentage)
        val fossilScale = FOSSIL_CURVE(completionPercentage)
        val bufferSource = Minecraft.getInstance().renderBuffers().bufferSource()

        val identifiersAndScales = listOf(
            EMBRYO_IDENTIFIERS[0] to embryo1Scale,
            EMBRYO_IDENTIFIERS[1] to embryo2Scale,
            EMBRYO_IDENTIFIERS[2] to embryo3Scale,
            fossilPoserId to fossilScale
        )

        identifiersAndScales.forEach { (identifier, scaleValue) ->
            if (scaleValue <= 0F) {
                return@forEach
            }

            val model = VaryingModelRepository.getPoser(identifier, fossilState) as? FossilModel ?: return@forEach
            val texture = VaryingModelRepository.getTexture(identifier, fossilState)
            val layers = VaryingModelRepository.getLayers(identifier, fossilState)
            val vertexConsumer = bufferSource.getBuffer(RenderTypes.entityCutout(texture))
            val scale = if (timeRemaining == 0) model.maxScale else scaleValue * model.maxScale

            fossilState.currentModel = model
            fossilState.setPoseToFirstSuitable()
            model.context = renderContext
            renderContext.put(RenderContext.TEXTURE, texture)
            renderContext.put(RenderContext.SPECIES, fossil.identifier)
            renderContext.put(RenderContext.RENDER_STATE, RenderContext.RenderState.RESURRECTION_MACHINE)
            renderContext.put(RenderContext.POSABLE_STATE, fossilState)
            renderContext.put(RenderContext.ASPECTS, aspects)

            poseStack.pushPose()
            poseStack.translate(0.5, 1.0 + fossilFetusModel.yTranslation, 0.5)
            poseStack.mulPose(Axis.ZP.rotationDegrees(180F))
            if (tankDirection.counterClockWise == connectionDir) {
                poseStack.mulPose(Axis.YP.rotationDegrees(-90F))
            } else if (tankDirection == connectionDir) {
                poseStack.mulPose(Axis.YP.rotationDegrees(180F))
            } else if (tankDirection.opposite != connectionDir) {
                poseStack.mulPose(Axis.YP.rotationDegrees(90F))
            }

            poseStack.pushPose()
            poseStack.scale(scale, scale, scale)
            poseStack.translate(0.0, model.yGrowthPoint.toDouble(), 0.0)
            model.applyAnimations(
                entity = null,
                state = fossilState,
                headYaw = 0F,
                headPitch = 0F,
                limbSwing = 0F,
                limbSwingAmount = 0F,
                ageInTicks = fossilState.animationSeconds * 20
            )
            model.withLayerContext(bufferSource, fossilState, layers) {
                model.render(renderContext, poseStack, vertexConsumer, renderState.lightCoords, OverlayTexture.NO_OVERLAY, -0x1)
            }
            model.setDefault()
            model.red = 1F
            model.green = 1F
            model.blue = 1F
            model.resetLayerContext()
            poseStack.popPose()
            poseStack.popPose()
        }

        bufferSource.endBatch()
    }

    private fun currentConnectorModel(): QuadCollection {
        return CobblemonBakingOverrides.RESTORATION_TANK_CONNECTOR.getModel()
    }

    private fun currentFluidModel(fillLevel: Int): QuadCollection {
        return when (fillLevel) {
            1 -> CobblemonBakingOverrides.RESTORATION_TANK_FLUID_CHUNKED_1.getModel()
            2 -> CobblemonBakingOverrides.RESTORATION_TANK_FLUID_CHUNKED_2.getModel()
            3 -> CobblemonBakingOverrides.RESTORATION_TANK_FLUID_CHUNKED_3.getModel()
            4 -> CobblemonBakingOverrides.RESTORATION_TANK_FLUID_CHUNKED_4.getModel()
            5 -> CobblemonBakingOverrides.RESTORATION_TANK_FLUID_CHUNKED_5.getModel()
            6 -> CobblemonBakingOverrides.RESTORATION_TANK_FLUID_CHUNKED_6.getModel()
            7 -> CobblemonBakingOverrides.RESTORATION_TANK_FLUID_CHUNKED_7.getModel()
            8 -> CobblemonBakingOverrides.RESTORATION_TANK_FLUID_CHUNKED_8.getModel()
            9 -> CobblemonBakingOverrides.RESTORATION_TANK_FLUID_BUBBLING.getModel()
            else -> QuadCollection.EMPTY
        }
    }

    companion object {
        val EMBRYO_IDENTIFIERS = listOf(
            cobblemonResource("embryo_stage1"),
            cobblemonResource("embryo_stage2"),
            cobblemonResource("embryo_stage3")
        )

        val EMBRYO_CURVE_1 = parabolaFunction(peak = 0.5F, period = 1F).rerange(0.0F, 0.8F).timeDilate(2.5F)
        val EMBRYO_CURVE_2 = parabolaFunction(peak = 0.9F, period = 1F).rerange(0.2F, 1.2F).timeDilate(2.5F)
        val EMBRYO_CURVE_3 = parabolaFunction(peak = 1F, period = 1F).rerange(0.6F, 1.4F).timeDilate(2.5F)
        val FOSSIL_CURVE: WaveFunction = { t: Float -> -0.4F * (t - 2.5F).pow(2) + 1F }.timeDilate(2.5F)
    }
}

private class RestorationTankRenderState : BlockEntityRenderState() {
    var connectionDir: Direction? = null
    var fillLevel: Int = 0
    var isRunning: Boolean = false
    var hasCreatedPokemon: Boolean = false
    var timeRemaining: Int = -1
    var resultingFossil: Fossil? = null
    var fossilState: FossilState? = null
    var partialTicks: Float = 0F
    var blockState: BlockState? = null
}
