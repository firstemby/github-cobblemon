/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.render.generic

import com.cobblemon.mod.common.client.entity.GenericBedrockClientDelegate
import com.cobblemon.mod.common.client.render.models.blockbench.generic.PosableGenericEntityModel
import com.cobblemon.mod.common.client.render.models.blockbench.repository.VaryingModelRepository
import com.cobblemon.mod.common.entity.generic.GenericBedrockEntity
import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.RenderType
import net.minecraft.client.renderer.entity.EntityRenderer
import net.minecraft.client.renderer.entity.EntityRendererProvider
import net.minecraft.client.renderer.texture.OverlayTexture

open class GenericBedrockRenderer(context: EntityRendererProvider.Context) : EntityRenderer<GenericBedrockEntity>(context) {
    val model = PosableGenericEntityModel()
    override fun getTextureLocation(entity: GenericBedrockEntity) = VaryingModelRepository.getTexture(entity.category, (entity.delegate as GenericBedrockClientDelegate))
    override fun render(entity: GenericBedrockEntity, yaw: Float, partialTicks: Float, poseStack: PoseStack, buffer: MultiBufferSource, packedLight: Int) {
        if (entity.isInvisible) {
            return
        }

        val state = entity.delegate as GenericBedrockClientDelegate
        state.currentAspects = entity.aspects
        val model = VaryingModelRepository.getPoser(entity.category, state)
        this.model.posableModel = model
        model.context = this.model.context
        this.model.setupEntityTypeContext(entity)
        poseStack.pushPose()
        applyTransform(entity, yaw, partialTicks, poseStack)
        state.updatePartialTicks(partialTicks)
        val texture = VaryingModelRepository.getTextureSupplier(entity.category, state)
        val vertices = buffer.getBuffer(RenderType.entityCutout(texture(state)))
        val vertexConsumer = texture.vertexConsumer(state, vertices)
        model.setLayerContext(buffer, state, VaryingModelRepository.getLayers(entity.category, state))
        this.model.setupAnim(entity, 0f, 0f, entity.tickCount + partialTicks, 0F, 0F)
        this.model.renderToBuffer(poseStack, vertexConsumer, packedLight, OverlayTexture.NO_OVERLAY, -0x1)

        model.green = 1F
        model.blue = 1F
        model.red = 1F

        model.resetLayerContext()

        poseStack.popPose()
        super.render(entity, yaw, partialTicks, poseStack, buffer, packedLight)
    }

    protected open fun applyTransform(entity: GenericBedrockEntity, yaw: Float, partialTicks: Float, poseStack: PoseStack) {
        BedrockEntityTransform.apply(poseStack, yaw, entity.scale)
    }
}
