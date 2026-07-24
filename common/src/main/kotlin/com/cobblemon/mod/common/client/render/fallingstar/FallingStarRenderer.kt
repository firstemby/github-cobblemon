/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.render.fallingstar

import com.cobblemon.mod.common.client.entity.FallingStarClientDelegate
import com.cobblemon.mod.common.client.render.models.blockbench.projectile.PosableFallingStarEntityModel
import com.cobblemon.mod.common.client.render.models.blockbench.repository.VaryingModelRepository
import com.cobblemon.mod.common.entity.projectile.FallingStarEntity
import com.cobblemon.mod.common.util.cobblemonResource
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.math.Axis
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.RenderType
import net.minecraft.client.renderer.entity.EntityRenderer
import net.minecraft.client.renderer.entity.EntityRendererProvider
import net.minecraft.client.renderer.texture.OverlayTexture

class FallingStarRenderer(context: EntityRendererProvider.Context) : EntityRenderer<FallingStarEntity>(context) {
    val model = PosableFallingStarEntityModel()
    val identifier = cobblemonResource("falling_star")
    override fun getTextureLocation(entity: FallingStarEntity) = VaryingModelRepository.getTexture(identifier, (entity.delegate as FallingStarClientDelegate))
    override fun render(entity: FallingStarEntity, yaw: Float, partialTicks: Float, poseStack: PoseStack, buffer: MultiBufferSource, packedLight: Int) {
        if (entity.isInvisible) {
            return
        }

        val state = entity.delegate as FallingStarClientDelegate
        state.currentAspects = emptySet()
        val model = VaryingModelRepository.getPoser(identifier, state)
        this.model.posableModel = model
        model.context = this.model.context
        this.model.setupEntityTypeContext(entity)
        poseStack.pushPose()
        poseStack.scale(1.0F, -1.0F, 1.0F)
        poseStack.mulPose(Axis.YP.rotationDegrees(yaw))
        val vertexConsumer = buffer.getBuffer(RenderType.entityCutout(getTextureLocation(entity)))

        state.updatePartialTicks(partialTicks)
        model.setLayerContext(buffer, state, VaryingModelRepository.getLayers(identifier, state))
        this.model.setupAnim(entity, 0f, 0f, entity.tickCount + partialTicks, 0F, 0F)
        this.model.renderToBuffer(poseStack, vertexConsumer, packedLight, OverlayTexture.NO_OVERLAY, -0x1)

        model.green = 1F
        model.blue = 1F
        model.red = 1F

        model.resetLayerContext()

        poseStack.popPose()
        super.render(entity, yaw, partialTicks, poseStack, buffer, packedLight)
    }
}