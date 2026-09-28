/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.render.layer

import com.cobblemon.mod.common.client.render.player.RecallArmAnimationTracker
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.math.Axis
import net.minecraft.client.Minecraft
import net.minecraft.client.model.PlayerModel
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.entity.RenderLayerParent
import net.minecraft.client.renderer.entity.layers.RenderLayer
import net.minecraft.world.entity.HumanoidArm
import net.minecraft.world.entity.player.Player
import net.minecraft.world.phys.Vec3
import org.joml.Vector3f

class PlayerRecallBallRenderer<T : Player>(
    renderLayerParent: RenderLayerParent<T, PlayerModel<T>>
) : RenderLayer<T, PlayerModel<T>>(renderLayerParent) {

    override fun render(
        matrixStack: PoseStack,
        buffer: MultiBufferSource,
        packedLight: Int,
        player: T,
        limbSwing: Float,
        limbSwingAmount: Float,
        partialTicks: Float,
        ageInTicks: Float,
        netHeadYaw: Float,
        headPitch: Float
    ) {
        val anim = RecallArmAnimationTracker.get(player.uuid) ?: return
        val mc = Minecraft.getInstance()
        if (player === mc.player && mc.options.cameraType.isFirstPerson) return

        matrixStack.pushPose()
        getParentModel().translateToHand(HumanoidArm.RIGHT, matrixStack)
        matrixStack.mulPose(Axis.XP.rotationDegrees(-90F))
        matrixStack.mulPose(Axis.YP.rotationDegrees(180F))
        matrixStack.translate(0.0625, 0.125, -0.625)
        matrixStack.scale(0.54F, -0.54F, -0.54F)
        matrixStack.translate(0.0, 0.5, 0.0)
        matrixStack.mulPose(Axis.YP.rotationDegrees(180F))

        RecallArmAnimationTracker.renderBall(anim, matrixStack, buffer, packedLight, partialTicks)

        anim.ballState.locatorStates["beam"]?.let { beam ->
            matrixStack.pushPose()
            matrixStack.mulPose(beam.matrix)
            val offset = matrixStack.last().pose().transformPosition(Vector3f(0F, 0.18F, -0.3F))
            matrixStack.popPose()
            anim.beamOrigin = mc.gameRenderer.mainCamera.position.add(Vec3(offset))
        }
        matrixStack.popPose()
    }
}
