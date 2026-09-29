/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.render.stadium

import com.cobblemon.mod.common.client.CobblemonClient
import com.cobblemon.mod.common.client.cinematic.ClientSceneSessionManager
import com.cobblemon.mod.common.client.net.stadium.StadiumSpectateStateHandler
import com.cobblemon.mod.common.client.render.generic.BedrockEntityTransform
import com.cobblemon.mod.common.client.render.generic.GenericBedrockRenderer
import com.cobblemon.mod.common.entity.generic.GenericBedrockEntity
import com.cobblemon.mod.common.entity.stadium.StadiumCameraDroneEntity
import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.culling.Frustum
import net.minecraft.client.renderer.entity.EntityRendererProvider
import net.minecraft.util.Mth

class StadiumCameraDroneRenderer(context: EntityRendererProvider.Context) : GenericBedrockRenderer(context) {
    override fun render(entity: GenericBedrockEntity, yaw: Float, partialTicks: Float, poseStack: PoseStack, buffer: MultiBufferSource, packedLight: Int) {
        if (entity.isInvisible || StadiumSpectateStateHandler.watching || CobblemonClient.battle?.spectating == true ||
            ClientSceneSessionManager.getAllSessions().isNotEmpty()) return
        super.render(entity, yaw, partialTicks, poseStack, buffer, packedLight)
        (entity as? StadiumCameraDroneEntity)?.let { StadiumDroneLightningRenderer.render(it, partialTicks, poseStack, buffer) }
    }

    override fun shouldRender(entity: GenericBedrockEntity, frustum: Frustum, x: Double, y: Double, z: Double): Boolean {
        return super.shouldRender(entity, frustum, x, y, z) ||
            (entity as? StadiumCameraDroneEntity)?.lightningTrail?.bounds()?.let(frustum::isVisible) == true
    }

    override fun applyTransform(entity: GenericBedrockEntity, yaw: Float, partialTicks: Float, poseStack: PoseStack) {
        val pitch = Mth.lerp(partialTicks, entity.xRotO, entity.xRot)
        BedrockEntityTransform.apply(poseStack, yaw, entity.scale, pitch)
        poseStack.translate(0.0, 4.65 / 16.0, 2.75 / 16.0)
    }
}
