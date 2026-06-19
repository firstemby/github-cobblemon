/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.render.player

import com.cobblemon.mod.common.client.render.models.blockbench.repository.RenderContext
import com.cobblemon.mod.common.client.render.models.blockbench.repository.VaryingModelRepository
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.math.Axis
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.RenderType
import net.minecraft.client.renderer.entity.ItemRenderer
import net.minecraft.client.renderer.texture.OverlayTexture

/**
 * Renders the recalling player's Poké Ball in first person, rising up from the bottom of the screen
 * in place of whatever they were holding. The world-space recall beam still originates near the
 * player's hand (handled by the eye/look fallback in PokemonRenderer) — this is purely cosmetic.
 *
 * Invoked from ItemInHandRendererMixin, which cancels the vanilla main-hand item render while a
 * recall is active.
 */
object FirstPersonRecallBallRenderer {

    // Visual tuning knobs — adjust in-game to taste.
    /** Resting position of the ball in view space (right, up, forward). */
    private const val REST_X = 0.45
    private const val REST_Y = -0.55
    private const val REST_Z = -.6
    /** How far below REST_Y the ball starts before rising into view. */
    private const val RISE_DISTANCE = 0.6
    private const val BALL_SCALE = 0.45F

    private val ballContext = RenderContext().also {
        it.put(RenderContext.RENDER_STATE, RenderContext.RenderState.WORLD)
    }

    /**
     * @return true if a recall ball was rendered (so the caller should cancel the vanilla item).
     */
    fun render(poseStack: PoseStack, buffer: MultiBufferSource, packedLight: Int, partialTicks: Float): Boolean {
        val player = Minecraft.getInstance().player ?: return false
        val anim = RecallArmAnimationTracker.get(player.uuid) ?: return false
        val rise = RecallArmAnimationTracker.raiseFactor(player.uuid)

        poseStack.pushPose()
        // Slide up from below as the recall begins, hold, then drop back down as it ends.
        poseStack.translate(REST_X, REST_Y - RISE_DISTANCE * (1.0 - rise), REST_Z)
        poseStack.scale(BALL_SCALE, -BALL_SCALE, -BALL_SCALE)
        // Tilt slightly so the ball reads nicely in the corner. Front faces the crosshair.
        poseStack.mulPose(Axis.YP.rotationDegrees(180F))
        poseStack.mulPose(Axis.XP.rotationDegrees(15F))

        val state = anim.ballState
        val ball = anim.ball
        val model = VaryingModelRepository.getPoser(ball.name, state)
        val texture = VaryingModelRepository.getTexture(ball.name, state)
        state.updatePartialTicks(partialTicks)
        model.context = ballContext
        ballContext.put(RenderContext.ASPECTS, state.aspects)
        ballContext.put(RenderContext.POSABLE_STATE, state)
        model.applyAnimations(null, state, 0F, 0F, 0F, 0F, 0F)
        val consumer = ItemRenderer.getFoilBufferDirect(buffer, RenderType.entityCutout(texture), false, false)
        model.render(ballContext, poseStack, consumer, packedLight, OverlayTexture.NO_OVERLAY, -0x1)
        model.red = 1F
        model.green = 1F
        model.blue = 1F
        model.resetLayerContext()

        poseStack.popPose()
        return true
    }
}
