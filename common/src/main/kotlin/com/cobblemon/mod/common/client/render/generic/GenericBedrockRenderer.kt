/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.render.generic

import com.cobblemon.mod.common.client.entity.GenericBedrockClientDelegate
import com.cobblemon.mod.common.client.render.models.blockbench.repository.RenderContext
import com.cobblemon.mod.common.client.render.models.blockbench.repository.VaryingModelRepository
import com.cobblemon.mod.common.entity.generic.GenericBedrockEntity
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.math.Axis
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.SubmitNodeCollector
import net.minecraft.client.renderer.entity.EntityRenderer
import net.minecraft.client.renderer.entity.EntityRendererProvider
import net.minecraft.client.renderer.entity.state.EntityRenderState
import net.minecraft.client.renderer.rendertype.RenderTypes
import net.minecraft.client.renderer.state.level.CameraRenderState
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.resources.Identifier
import net.minecraft.util.Mth

class GenericBedrockRenderState : EntityRenderState() {
    var entity: GenericBedrockEntity? = null
    var poseState: GenericBedrockClientDelegate? = null
    var category: Identifier? = null
    var aspects: Set<String> = emptySet()
    var texture: Identifier = Identifier.parse("minecraft:textures/misc/white.png")
    var yaw: Float = 0F
    var scale: Float = 1F
    var partialTicks: Float = 0F
}

@Suppress("UNUSED_PARAMETER")
class GenericBedrockRenderer(context: EntityRendererProvider.Context) : EntityRenderer<GenericBedrockEntity, GenericBedrockRenderState>(context) {
    override fun createRenderState() = GenericBedrockRenderState()

    override fun extractRenderState(entity: GenericBedrockEntity, renderState: GenericBedrockRenderState, partialTick: Float) {
        super.extractRenderState(entity, renderState, partialTick)

        val state = entity.delegate as? GenericBedrockClientDelegate ?: return
        renderState.entity = entity
        renderState.poseState = state
        renderState.category = entity.category
        renderState.aspects = entity.aspects
        renderState.texture = VaryingModelRepository.getTexture(entity.category, state)
        renderState.yaw = Mth.rotLerp(partialTick, entity.yRotO, entity.yRot)
        renderState.scale = entity.scale
        renderState.partialTicks = partialTick
        renderState.shadowRadius = 0F
    }

    override fun submit(
        renderState: GenericBedrockRenderState,
        poseStack: PoseStack,
        submitNodeCollector: SubmitNodeCollector,
        cameraRenderState: CameraRenderState
    ) {
        val entity = renderState.entity ?: return
        if (entity.isInvisible) {
            return
        }

        val state = renderState.poseState ?: return
        val category = renderState.category ?: return
        val model = VaryingModelRepository.getPoser(category, state)
        val renderContext = RenderContext().also {
            it.put(RenderContext.RENDER_STATE, RenderContext.RenderState.WORLD)
            it.put(RenderContext.ENTITY, entity)
            it.put(RenderContext.POSABLE_STATE, state)
            it.put(RenderContext.ASPECTS, renderState.aspects)
        }
        val bufferSource = Minecraft.getInstance().renderBuffers().bufferSource()

        state.currentAspects = renderState.aspects
        state.updatePartialTicks(renderState.partialTicks)
        model.context = renderContext
        model.applyAnimations(entity, state, 0F, 0F, entity.tickCount + renderState.partialTicks, 0F, 0F)

        poseStack.pushPose()
        poseStack.scale(1F, -1F, 1F)
        poseStack.scale(renderState.scale, renderState.scale, renderState.scale)
        poseStack.mulPose(Axis.YP.rotationDegrees(renderState.yaw))
        model.withLayerContext(bufferSource, state, VaryingModelRepository.getLayers(category, state)) {
            val buffer = bufferSource.getBuffer(RenderTypes.entityCutout(renderState.texture))
            model.render(renderContext, poseStack, buffer, renderState.lightCoords, OverlayTexture.NO_OVERLAY, -0x1)
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
