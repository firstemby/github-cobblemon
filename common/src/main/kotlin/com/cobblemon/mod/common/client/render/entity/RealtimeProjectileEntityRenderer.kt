/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.RenderType
import net.minecraft.client.renderer.entity.EntityRenderer
import net.minecraft.client.renderer.entity.EntityRendererProvider
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.core.BlockPos
import net.minecraft.resources.ResourceLocation
import com.cobblemon.mod.common.entity.RealtimeMoveProjectile

class RealtimeProjectileEntityRenderer(context: EntityRendererProvider.Context) : EntityRenderer<RealtimeMoveProjectile>(context) {
    override fun getBlockLightLevel(entity: RealtimeMoveProjectile, pos: BlockPos): Int {
        return 15
    }

    override fun render(
        entity: RealtimeMoveProjectile,
        entityYaw: Float,
        partialTicks: Float,
        poseStack: PoseStack,
        buffer: MultiBufferSource,
        packedLight: Int
    ) {
        poseStack.pushPose()
        poseStack.scale(2.0f, 2.0f, 2.0f)
        poseStack.mulPose(this.entityRenderDispatcher.cameraOrientation())
        val pose = poseStack.last()
        val vertexConsumer = buffer.getBuffer(RENDER_TYPE)
        vertex(vertexConsumer, pose, packedLight, 0.0f, 0, 0, 1)
        vertex(vertexConsumer, pose, packedLight, 1.0f, 0, 1, 1)
        vertex(vertexConsumer, pose, packedLight, 1.0f, 1, 1, 0)
        vertex(vertexConsumer, pose, packedLight, 0.0f, 1, 0, 0)
        poseStack.popPose()
        super.render(entity, entityYaw, partialTicks, poseStack, buffer, packedLight)
    }

    override fun getTextureLocation(entity: RealtimeMoveProjectile): ResourceLocation {
        return TEXTURE_LOCATION
    }

    companion object {
        private val TEXTURE_LOCATION: ResourceLocation =
            ResourceLocation.withDefaultNamespace("textures/entity/enderdragon/dragon_fireball.png")
        private val RENDER_TYPE: RenderType

        private fun vertex(
            consumer: VertexConsumer,
            pose: PoseStack.Pose,
            packedLight: Int,
            x: Float,
            y: Int,
            u: Int,
            v: Int
        ) {
            consumer.addVertex(pose, x - 0.5f, y.toFloat() - 0.25f, 0.0f).setColor(-1).setUv(u.toFloat(), v.toFloat())
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(packedLight).setNormal(pose, 0.0f, 1.0f, 0.0f)
        }

        init {
            RENDER_TYPE = RenderType.entityCutoutNoCull(TEXTURE_LOCATION)
        }
    }
}