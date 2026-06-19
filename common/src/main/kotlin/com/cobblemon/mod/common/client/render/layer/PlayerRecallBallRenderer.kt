/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.render.layer

import com.cobblemon.mod.common.client.render.models.blockbench.repository.RenderContext
import com.cobblemon.mod.common.client.render.models.blockbench.repository.VaryingModelRepository
import com.cobblemon.mod.common.client.render.player.RecallArmAnimationTracker
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.math.Axis
import net.minecraft.client.Minecraft
import net.minecraft.client.model.PlayerModel
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.RenderType
import net.minecraft.client.renderer.entity.ItemRenderer
import net.minecraft.client.renderer.entity.RenderLayerParent
import net.minecraft.client.renderer.entity.layers.RenderLayer
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.world.entity.HumanoidArm
import net.minecraft.world.entity.player.Player
import net.minecraft.world.phys.Vec3
import org.joml.Vector3f

/**
 * Renders the matching Poké Ball in a recalling player's right hand and reports the world-space point
 * the recall beam should emit from. Driven entirely by [RecallArmAnimationTracker]; the right arm is
 * posed forward separately in HumanoidModelMixin so [PlayerModel.translateToHand] lands at the
 * extended hand.
 *
 * The local player in first person is skipped here and handled by the first-person ball renderer
 * (see ItemInHandRendererMixin) instead.
 */
class PlayerRecallBallRenderer<T : Player>(
    renderLayerParent: RenderLayerParent<T, PlayerModel<T>>
) : RenderLayer<T, PlayerModel<T>>(renderLayerParent) {

    companion object {
        private const val BALL_SCALE = 0.5F

        private const val HAND_X = 0.0625
        private const val HAND_Y = 0.125
        private const val HAND_Z = -0.625

        private const val BALL_YAW_SPIN = 180F
        private const val BALL_INFLATE = 1.08F

        private const val BEAM_X_OFFSET = 0F
        private const val BEAM_Y_OFFSET = 0.18F
        private const val BEAM_Z_OFFSET = -0.30F
    }

    private val ballContext = RenderContext().also {
        it.put(RenderContext.RENDER_STATE, RenderContext.RenderState.WORLD)
    }

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
        matrixStack.translate(HAND_X, HAND_Y, HAND_Z)

        renderBall(anim, matrixStack, buffer, packedLight, partialTicks)

        matrixStack.popPose()
    }

    private fun renderBall(
        anim: RecallArmAnimationTracker.RecallAnim,
        matrixStack: PoseStack,
        buffer: MultiBufferSource,
        packedLight: Int,
        partialTicks: Float
    ) {
        val state = anim.ballState
        val ball = anim.ball
        matrixStack.pushPose()
        matrixStack.scale(BALL_INFLATE, BALL_INFLATE, BALL_INFLATE)
        matrixStack.translate(0.0, (-8.0 / 16.0) * BALL_SCALE, 0.0)
        matrixStack.scale(BALL_SCALE, -BALL_SCALE, -BALL_SCALE)
        matrixStack.mulPose(Axis.YP.rotationDegrees(BALL_YAW_SPIN))

        val model = VaryingModelRepository.getPoser(ball.name, state)
        val texture = VaryingModelRepository.getTexture(ball.name, state)
        state.updatePartialTicks(partialTicks)
        model.context = ballContext
        ballContext.put(RenderContext.ASPECTS, state.aspects)
        ballContext.put(RenderContext.POSABLE_STATE, state)

        model.applyAnimations(null, state, 0F, 0F, 0F, 0F, 0F)

        state.locatorStates["beam"]?.let { beamLocator ->
            matrixStack.pushPose()
            matrixStack.mulPose(beamLocator.matrix)
            val camRelative = matrixStack.last().pose().transformPosition(Vector3f(BEAM_X_OFFSET, BEAM_Y_OFFSET, BEAM_Z_OFFSET))
            matrixStack.popPose()
            val camera = Minecraft.getInstance().gameRenderer.mainCamera.position
            anim.beamOrigin = Vec3(camera.x + camRelative.x, camera.y + camRelative.y, camera.z + camRelative.z)
        }
        val consumer = ItemRenderer.getFoilBufferDirect(buffer, RenderType.entityCutout(texture), false, false)
        model.render(ballContext, matrixStack, consumer, packedLight, OverlayTexture.NO_OVERLAY, -0x1)
        model.red = 1F
        model.green = 1F
        model.blue = 1F
        model.resetLayerContext()
        matrixStack.popPose()
    }
}
