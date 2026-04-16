/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.render.block

import com.cobblemon.mod.common.block.chest.GildedState
import com.cobblemon.mod.common.block.entity.GildedChestBlockEntity
import com.cobblemon.mod.common.client.render.models.blockbench.blockentity.BlockEntityModel
import com.cobblemon.mod.common.client.render.models.blockbench.repository.RenderContext
import com.cobblemon.mod.common.client.render.models.blockbench.repository.VaryingModelRepository
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.math.Axis
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.SubmitNodeCollector
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState
import net.minecraft.client.renderer.feature.ModelFeatureRenderer
import net.minecraft.client.renderer.rendertype.RenderTypes
import net.minecraft.client.renderer.state.level.CameraRenderState
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.resources.Identifier
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import net.minecraft.world.phys.Vec3

class GildedChestBlockRenderer(context: BlockEntityRendererProvider.Context) : BlockEntityRenderer<GildedChestBlockEntity, BlockEntityRenderState> {
    private val renderContext = RenderContext().also {
        it.put(RenderContext.RENDER_STATE, RenderContext.RenderState.BLOCK)
        it.put(RenderContext.DO_QUIRKS, true)
    }

    override fun createRenderState(): BlockEntityRenderState = GildedChestRenderState()

    override fun extractRenderState(
        blockEntity: GildedChestBlockEntity,
        renderState: BlockEntityRenderState,
        tickDelta: Float,
        cameraPos: Vec3,
        crumblingOverlay: ModelFeatureRenderer.CrumblingOverlay?
    ) {
        BlockEntityRenderState.extractBase(blockEntity, renderState, crumblingOverlay)
        if (renderState !is GildedChestRenderState) {
            return
        }

        renderState.poseState = blockEntity.posableState
        renderState.poserId = blockEntity.type.poserId
        renderState.partialTicks = tickDelta
        renderState.yRot = blockEntity.blockState.getValue(BlockStateProperties.HORIZONTAL_FACING).toYRot()
    }

    override fun submit(
        renderState: BlockEntityRenderState,
        poseStack: PoseStack,
        submitNodeCollector: SubmitNodeCollector,
        cameraRenderState: CameraRenderState
    ) {
        if (renderState !is GildedChestRenderState) {
            return
        }

        val state = renderState.poseState ?: return
        val poserId = renderState.poserId ?: return
        val bufferSource = Minecraft.getInstance().renderBuffers().bufferSource()
        val aspects = emptySet<String>()

        state.currentAspects = aspects
        state.updatePartialTicks(renderState.partialTicks)

        val model = VaryingModelRepository.getPoser(poserId, state) as? BlockEntityModel ?: return
        val texture = VaryingModelRepository.getTexture(poserId, state)
        val layers = VaryingModelRepository.getLayers(poserId, state)
        val vertexConsumer = bufferSource.getBuffer(RenderTypes.entityCutout(texture))

        model.context = renderContext
        model.bufferProvider = bufferSource
        state.currentModel = model
        renderContext.put(RenderContext.ASPECTS, aspects)
        renderContext.put(RenderContext.TEXTURE, texture)
        renderContext.put(RenderContext.SPECIES, poserId)
        renderContext.put(RenderContext.POSABLE_STATE, state)

        poseStack.pushPose()
        poseStack.mulPose(Axis.ZP.rotationDegrees(180F))
        poseStack.translate(-0.5, 0.0, 0.5)
        poseStack.mulPose(Axis.YP.rotationDegrees(renderState.yRot))
        poseStack.mulPose(Axis.YP.rotationDegrees(180F))

        model.applyAnimations(
            entity = null,
            state = state,
            headYaw = 0F,
            headPitch = 0F,
            limbSwing = 0F,
            limbSwingAmount = 0F,
            ageInTicks = state.animationSeconds * 20
        )
        model.withLayerContext(bufferSource, state, layers) {
            model.render(renderContext, poseStack, vertexConsumer, renderState.lightCoords, OverlayTexture.NO_OVERLAY, -0x1)
        }
        model.setDefault()
        model.red = 1F
        model.green = 1F
        model.blue = 1F
        model.resetLayerContext()
        poseStack.popPose()

        bufferSource.endBatch()
    }
}

private class GildedChestRenderState : BlockEntityRenderState() {
    var poseState: GildedState? = null
    var poserId: Identifier? = null
    var partialTicks: Float = 0F
    var yRot: Float = 0F
}
