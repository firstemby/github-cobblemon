/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.render.player

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.math.Axis
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.MultiBufferSource

object FirstPersonRecallBallRenderer {
    fun render(poseStack: PoseStack, buffer: MultiBufferSource, packedLight: Int, partialTicks: Float): Boolean {
        val player = Minecraft.getInstance().player ?: return false
        val anim = RecallArmAnimationTracker.get(player.uuid) ?: return false
        val rise = RecallArmAnimationTracker.raiseFactor(player.uuid)

        poseStack.pushPose()
        poseStack.translate(0.45, -0.55 - 0.6 * (1.0 - rise), -0.6)
        poseStack.scale(0.45F, -0.45F, -0.45F)
        poseStack.mulPose(Axis.YP.rotationDegrees(180F))
        poseStack.mulPose(Axis.XP.rotationDegrees(15F))
        RecallArmAnimationTracker.renderBall(anim, poseStack, buffer, packedLight, partialTicks)
        poseStack.popPose()
        return true
    }
}
